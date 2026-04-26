# GLS Scaling Architecture Plan

## Alignment Analysis: Current State vs Recommendations

### Already in place (strong foundations)

| Recommendation | Current State |
|---|---|
| Visual graph + topological sort | Kahn's algorithm in `PipelineExecutionEngine` |
| Node type registry drives dispatch | `NodeTypeDefinition` with `executionCategory`, `configSchema`, `requiresDocReload` |
| Accelerators to skip LLM | 4 types: BERT, fingerprint, rules engine, similarity cache |
| Error states + retry | `PROCESSING_FAILED`, `CLASSIFICATION_FAILED`, `ENFORCEMENT_FAILED` with `lastError`, `retryCount` |
| Dead-letter queue | `gls.documents.dlq` with DLX binding |
| AI usage audit | `AiUsageLog` captures tool calls, tokens, duration, cost per classification |
| Block versioning | Immutable `PipelineBlock` versions with draft/publish/rollback |
| Legacy async fallback | `ASYNC_BOUNDARY` execution category still in code, rollback is data-driven |

### Not in place (gaps that matter at scale)

| Gap | Impact at 100M+ records |
|---|---|
| **No PipelineRun/NodeRun** | Can't debug, replay, or audit individual pipeline executions. At scale, "what happened to this document?" becomes unanswerable without querying logs |
| **No MCP response caching** | Taxonomy, sensitivity defs, traits are re-queried from MongoDB on every single LLM call. At 1M classifications that's 5-8M unnecessary DB queries |
| **Single consumer per queue** | One document at a time through the pipeline. A backlog of 10K documents processes serially |
| **No container resource limits** | Any service can OOM-kill neighbours. Elasticsearch at 512MB heap will fall over with millions of indexed documents |
| **Execution state = document state** | `PROCESSING` means both "pipeline is running" and "text extraction done". Can't distinguish "stuck" from "in progress" without timestamps |
| **No horizontal scaling** | No replicas, no Kubernetes, single MongoDB instance, single-node Elasticsearch |
| **No metrics/tracing** | No Prometheus, no distributed tracing. Blind to bottlenecks at scale |
| **No backpressure** | Nothing prevents 1000 documents flooding the LLM worker simultaneously |
| **PII dismissals unbounded** | `getPiiDismissals()` loads ALL historical records into memory — will OOM at scale |

### Verdict: Is the architecture change worth it?

**Yes, unambiguously.** The current architecture works well for demo/pilot scale (thousands of documents). At 100M+ records, three things will break hard:

1. **The synchronous LLM call blocks the entire pipeline consumer thread.** With 1 consumer and 30-150s per classification, throughput is ~600-2,800 documents/day through LLM. Even with accelerators skipping 70% of LLM calls, you're looking at weeks to process a million-document backlog.

2. **MongoDB as single instance with no execution state** means you can't horizontally scale consumers without write conflicts on `DocumentModel`, and you can't recover from crashes mid-pipeline.

3. **No caching of static governance data** means every LLM call pays a ~20-50KB tax loading taxonomy, sensitivity definitions, and traits that change maybe once a week.

---

## Phased Implementation Plan

### Phase 1: Near Term (4-6 weeks) — Make it production-safe

**Goal:** Async LLM execution, MCP caching, basic observability, concurrent consumers.

#### 1a. PipelineRun / NodeRun models (1 week)

New MongoDB collections:

```
pipeline_runs {
  _id, documentId, pipelineId, pipelineVersion,
  status: PENDING | RUNNING | WAITING | COMPLETED | FAILED,
  startedAt, completedAt, currentNodeKey,
  totalDurationMs, nodeCount, llmNodeCount,
  error, retryCount, correlationId
}

node_runs {
  _id, pipelineRunId, documentId, nodeKey, nodeType,
  executionCategory,
  status: PENDING | RUNNING | WAITING | SUCCEEDED | FAILED | SKIPPED,
  startedAt, completedAt, durationMs,
  input: {}, output: {}, error,
  retryCount, idempotencyKey
}
```

- Engine creates `PipelineRun` at start, `NodeRun` per node
- Document status still updates (for user-facing state) but execution tracking is separate
- Enables: debugging, replay, SLA monitoring, stale detection by node

#### 1b. Async callback pattern for LLM nodes (2 weeks)

Replace `SYNC_LLM` with `ASYNC_JOB` execution:

```
Engine hits LLM node
  → Creates NodeRun(status=WAITING, jobId=uuid, idempotencyKey)
  → Publishes LlmJobRequested to RabbitMQ
  → Persists PipelineRun(status=WAITING, currentNodeKey)
  → Returns (releases thread)

LLM worker picks up job
  → Publishes LlmJobStarted
  → Classifies (MCP tools, 30-150s)
  → Publishes LlmJobCompleted(jobId, result)

PipelineResumer receives completion
  → Loads PipelineRun, validates idempotencyKey
  → Updates NodeRun(status=SUCCEEDED)
  → Resumes engine from next node
  → Continues inline for fast nodes
```

This generalises the existing `ASYNC_BOUNDARY` pattern but with proper state tracking and the ability for *any* node to be async, not just the classification split.

#### 1c. MCP response caching (3-4 days)

Add Spring `@Cacheable` with Caffeine (in-memory, TTL-based) to MCP tools:

| Tool | Cache TTL | Rationale |
|---|---|---|
| `get_classification_taxonomy` | 5 min | Changes rarely, largest payload |
| `get_sensitivity_definitions` | 5 min | Almost never changes |
| `get_document_traits` | 5 min | Rarely changes |
| `get_governance_policies` | 2 min | May change more often |
| `get_retention_schedules` | 5 min | Rarely changes |
| `get_storage_capabilities` | 5 min | Rarely changes |
| `get_correction_history` | No cache | Must be fresh per document |
| `get_org_pii_patterns` | 1 min | Changes with corrections |
| `get_metadata_schemas` | 2 min | Per-category, cacheable |
| `save_classification_result` | No cache | Write operation |

Cache eviction on admin config changes (publish event from admin controllers).

**Expected impact:** 50-70% reduction in MCP tool latency, 4-6 fewer MongoDB queries per classification.

#### 1d. Consumer concurrency + virtual threads (3-4 days)

```yaml
spring:
  rabbitmq:
    listener:
      simple:
        concurrency: 5
        max-concurrency: 20
        prefetch: 5
  threads:
    virtual:
      enabled: true
```

With async LLM nodes, the consumer thread is freed after submitting the job. 20 concurrent consumers can have 20 documents in-flight simultaneously through fast nodes, with LLM jobs queued independently.

#### 1e. Basic observability (1 week)

- Add `micrometer-registry-prometheus` to all services
- Key metrics: `pipeline.run.duration`, `pipeline.node.duration`, `llm.classification.duration`, `llm.tool.calls.count`, `mcp.cache.hit.ratio`, `rabbitmq.queue.depth`
- Expose `/actuator/prometheus` on all services
- Add Grafana + Prometheus containers to docker-compose

---

### Phase 2: Medium Term (2-3 months) — Make it scale horizontally

**Goal:** Multi-instance everything, proper data infrastructure, backpressure.

#### 2a. MongoDB replica set + indexing

- Move from single instance to 3-node replica set (1 primary, 2 secondaries)
- Read preference: `secondaryPreferred` for MCP tool queries (taxonomy, corrections)
- Add compound indexes for hot query paths:
  - `{status: 1, organisationId: 1, createdAt: -1}` on `documents`
  - `{pipelineRunId: 1, nodeKey: 1}` on `node_runs`
  - `{documentId: 1, status: 1}` on `pipeline_runs`
  - `{correctionType: 1, correctedAt: -1}` on `classification_corrections` (with limit)
- Fix unbounded queries: add `.limit()` to `getPiiDismissals()`
- Optimistic locking on `DocumentModel` (add `@Version` field)

#### 2b. Node execution policy registry

Extend `NodeTypeDefinition` with runtime policy:

```java
enum ExecutionPolicy {
  INLINE_FAST,      // Sub-second, run in-thread
  ASYNC_JOB,        // Submit to worker queue, callback on completion
  CALLBACK_JOB,     // External system calls back (webhooks)
  HUMAN_GATE,       // Pauses until human action
  FANOUT,           // Parallel execution of branches
}
```

- Pipeline editor shows estimated duration per node and total pipeline time
- Save-time validation: warn if pipeline has >3 async nodes
- Admin can mark custom HTTP nodes as `ASYNC_JOB` if they're slow

#### 2c. Multi-instance API + LLM workers

- Docker Compose: `replicas: 3` for api, `replicas: 5` for llm-worker
- nginx upstream with round-robin load balancing
- RabbitMQ: separate queues per job type (`llm.jobs.classification`, `llm.jobs.custom`)
- LLM worker concurrency: 3 per instance (rate-limited by Anthropic API)

#### 2d. Elasticsearch cluster

- Move from single-node to 3-node cluster
- Index per month: `documents-2026-04`, `documents-2026-05` (for lifecycle management)
- Bulk indexing consumer on RabbitMQ (batch 500 documents per index request)
- Separate search replicas from indexing nodes

#### 2e. Backpressure + admission control

- Per-org rate limit on document ingestion (configurable via `app_config`)
- LLM job queue depth check: if >100 pending, reject new SYNC uploads with 429
- Priority queue for re-classifications (human corrections should process faster than bulk uploads)

---

### Phase 3: Long Term (6-12 months) — Make it enterprise-grade

**Goal:** Multi-tenant at scale, cost optimisation, optional durable workflow engine.

#### 3a. Evaluate Temporal vs custom orchestrator

At this point you'll have a working async orchestrator with PipelineRun/NodeRun. Evaluate whether to adopt Temporal based on:

- Do you need multi-day workflows (approval chains, legal holds)?
- Do you need versioned workflow migration (running pipelines survive schema changes)?
- Do you need cross-service saga compensation?

If yes to 2+ of these, migrate to Temporal. If not, your custom orchestrator is fine.

#### 3b. MongoDB sharding

At 100M+ documents, shard by `organisationId`:

- `documents` collection: shard key `{organisationId: 1, _id: 1}`
- `pipeline_runs`: shard key `{organisationId: 1, _id: 1}`
- `classification_results`: shard key `{documentId: "hashed"}`
- Config servers + mongos routers

#### 3c. LLM cost optimisation

- Use accelerators aggressively: target 70-80% of documents classified without LLM
- BERT classifier as first-pass for common categories
- Model stepping: Haiku for documents with rich correction history, Sonnet for novel categories
- Batch classification: group similar documents, classify once, apply to batch
- Pre-computed "classification context" snapshots per org (replaces 6 MCP tool calls with 1 cached payload)

#### 3d. Event sourcing (optional)

Replace direct document updates with append-only event log:

- `DocumentIngested`, `TextExtracted`, `PiiScanned`, `Classified`, `GovernanceApplied`
- Materialised view rebuilds document state from events
- Enables: audit trail, replay, temporal queries ("what was the classification at time T?")

---

## Compute Resources

### Current (dev/pilot: ~1,000 documents)

What you have now — single Docker host, everything on one machine.

| Service | CPU | RAM | Storage |
|---|---|---|---|
| Total | 4 cores | 16 GB | 100 GB SSD |

### Phase 1 target (production: up to 1M documents)

Single server or small VM cluster. Handles ~5,000-10,000 classifications/day.

| Service | Instances | CPU | RAM | Storage |
|---|---|---|---|---|
| API (gls-app-assembly) | 2 | 2 cores each | 2 GB each | — |
| LLM worker | 3 | 1 core each | 1.5 GB each | — |
| MCP server | 2 | 1 core each | 1 GB each | — |
| BERT classifier | 2 | 2 cores each (or GPU) | 2 GB each | — |
| MongoDB | 1 (replica set later) | 4 cores | 8 GB | 200 GB SSD |
| Elasticsearch | 1 | 2 cores | 4 GB (2 GB heap) | 200 GB SSD |
| RabbitMQ | 1 | 2 cores | 2 GB | 20 GB |
| MinIO | 1 | 2 cores | 2 GB | 1 TB (documents) |
| nginx | 1 | 1 core | 512 MB | — |
| Prometheus + Grafana | 1 | 1 core | 1 GB | 50 GB |
| **Total** | | **~24 cores** | **~32 GB** | **~1.5 TB** |

**Cloud estimate:** ~$400-600/month on AWS (mix of t3.xlarge + t3.large instances)

**LLM API cost:** At 10K docs/day, ~30% hitting LLM (accelerators skip 70%), ~3K classifications/day × ~$0.01-0.05 per classification = **$30-150/day** ($900-4,500/month)

### Phase 2 target (scale: 10-50M documents)

Kubernetes cluster or managed container service.

| Service | Instances | CPU | RAM | Storage |
|---|---|---|---|---|
| API | 3-5 | 2 cores each | 4 GB each | — |
| LLM worker | 5-10 | 1 core each | 2 GB each | — |
| MCP server | 3 | 1 core each | 2 GB each | — |
| BERT classifier | 3-5 | 2 cores each (GPU preferred) | 4 GB each | — |
| MongoDB (replica set) | 3 | 4 cores each | 16 GB each | 500 GB SSD each |
| Elasticsearch (cluster) | 3 | 4 cores each | 8 GB each (4 GB heap) | 500 GB SSD each |
| RabbitMQ (cluster) | 3 | 2 cores each | 4 GB each | 50 GB each |
| MinIO (distributed) | 4 | 2 cores each | 4 GB each | 2 TB each |
| nginx/LB | 2 | 1 core each | 1 GB each | — |
| Monitoring stack | 2 | 2 cores each | 4 GB each | 200 GB |
| **Total** | | **~80-120 cores** | **~180-250 GB** | **~15 TB** |

**Cloud estimate:** ~$2,000-4,000/month on AWS

**LLM API cost:** With better accelerators hitting 80% skip rate, and Haiku for correction-rich categories: **$3,000-8,000/month**

### Phase 3 target (enterprise: 100M+ documents)

| Service | Instances | CPU | RAM | Storage |
|---|---|---|---|---|
| API | 5-10 | 4 cores each | 8 GB each | — |
| LLM worker | 10-20 | 2 cores each | 4 GB each | — |
| MCP server | 5 | 2 cores each | 4 GB each | — |
| BERT classifier | 5-10 (GPU) | 4 cores + GPU | 8 GB each | — |
| MongoDB (sharded) | 9+ (3 shards × 3 replicas) | 8 cores each | 32 GB each | 1 TB NVMe each |
| Elasticsearch | 6-9 | 8 cores each | 16 GB each | 1 TB SSD each |
| RabbitMQ | 3-5 | 4 cores each | 8 GB each | 100 GB each |
| MinIO / S3 | Managed S3 | — | — | 50+ TB |
| Temporal (if adopted) | 3 | 4 cores each | 8 GB each | 500 GB each |
| Monitoring + tracing | 3 | 4 cores each | 8 GB each | 1 TB |
| **Total** | | **~300-500 cores** | **~800 GB-1.2 TB** | **~70+ TB** |

**Cloud estimate:** ~$10,000-20,000/month on AWS (or significantly less with reserved instances)

**LLM API cost:** With 90%+ accelerator skip rate and model stepping: **$5,000-15,000/month**

---

## Key Question: Ingestion Rate

Before building any of this: **what's your expected ingestion rate and timeline?**

- 100M records over 5 years (55K/day) is very different from 100M records migrated in 1 month (3.3M/day)
- Bulk migration of existing archives vs steady-state new uploads changes the priority completely
- If most documents are similar (same org, same categories), accelerators will handle the majority and LLM costs drop dramatically

The architecture is absolutely worth the investment. Phase 1 alone (async jobs + caching + concurrency) would take throughput from ~600 docs/day through LLM to ~50,000+/day — that's the difference between "toy" and "production".
