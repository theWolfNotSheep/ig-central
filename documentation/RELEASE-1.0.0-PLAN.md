# GLS 1.0.0 Release Plan

## Current State Assessment

### What's done

| Area | Status | Detail |
|---|---|---|
| Frontend (Next.js) | Complete | All workflows implemented: upload, classify, review, file, search, governance config, pipeline editor, monitoring, admin. Storage providers S3/SharePoint/Box/SMB shown in UI as roadmap (Google Drive + Local fully working). Admin-provisioned accounts (no self-registration by design). Duplicate `/pipelines` and `/blocks` routes consolidated under `/ai/*`. |
| Backend API | 150+ endpoints | All implemented, no stubs. Auth, documents, governance, pipelines, blocks, drives, review, admin, monitoring, search, PII/SARs |
| Pipeline engine | Working | Topological graph execution, accelerators, condition branching, LLM classification, governance enforcement |
| Visual pipeline editor | Complete | React Flow drag-and-drop, node types from API, configurable via block system |
| Governance framework | Complete | Taxonomy, sensitivity labels, policies, retention, storage tiers, PII types, metadata schemas |
| Google Drive connector | Complete | OAuth, file browser, in-situ classification, filing back to Drive |
| Review queue | Complete | Approve, override, reject, flag PII, correction feedback loop |
| MCP tools | 10 tools | Taxonomy, sensitivity, corrections, PII patterns, traits, schemas, policies, retention, storage, save result |
| Block versioning | Complete | Draft/publish/rollback, feedback tracking, AI improvement suggestions |
| Governance Hub | Complete | Separate service for publishing/downloading governance packs |
| Build | Healthy | Maven compiles clean (10s), 12 modules, Java 21, Spring Boot 4.0.2 |

### What's missing for 1.0.0

| Gap | Risk | Priority |
|---|---|---|
| **Pipeline blocks the consumer thread for 30-150s per LLM call** | Cannot process more than ~600-2,800 docs/day | P0 |
| **No PipelineRun/NodeRun tracking** | Can't debug or recover failed pipelines | P0 |
| **No MCP response caching** | Unnecessary DB load, slower classifications | P1 |
| **Zero tests** | No confidence in changes, no regression safety | P1 |
| **Zero CI/CD** | Manual builds, no automated quality gates | P1 |
| **No container resource limits** | Services can OOM-kill each other | P1 |
| **No production config profile** | Dev secrets in docker-compose, no secret management | P1 |
| **No consumer concurrency** | Single-threaded message processing | P2 |
| **No Prometheus/Grafana** | Blind to production issues | P2 |
| **Unbounded PII dismissal queries** | Will OOM as corrections accumulate | P2 |
| **No rate limiting** | Bulk uploads can overwhelm LLM workers | P2 |

---

## 1.0.0 Scope

**Goal:** A deployable, architecturally sound product that can handle a pilot customer with 10K-100K documents, with the foundation to scale without re-architecture.

**Not in scope for 1.0.0:**
- MongoDB replica sets or sharding
- Elasticsearch clustering
- Kubernetes / container orchestration
- Temporal or external workflow engine
- Event sourcing
- S3 / SharePoint / Box / SMB storage connectors (UI present as roadmap signal, Google Drive + Local only)
- User self-registration (admin-provisioned accounts only)
- User profile self-service (password change, preferences)
- Multi-region deployment

---

## Work Packages

### WP1: Async Pipeline Architecture (2 weeks)

This is the single most important change. It makes the pipeline production-viable and sets the architectural foundation for all future scaling.

#### 1.1 PipelineRun + NodeRun models

New domain models in `gls-document`:

```java
// PipelineRun — one per document pipeline execution
@Document("pipeline_runs")
public class PipelineRun {
    @Id String id;
    String documentId;
    String organisationId;
    String pipelineId;
    int pipelineVersion;
    
    PipelineRunStatus status;  // PENDING, RUNNING, WAITING, COMPLETED, FAILED, CANCELLED
    
    String currentNodeKey;
    String correlationId;      // UUID for tracing
    
    List<String> executionPlan; // Compiled node order from topological sort
    int currentNodeIndex;
    
    Map<String, Object> sharedContext; // Accumulated node outputs for downstream use
    
    Instant startedAt;
    Instant completedAt;
    long totalDurationMs;
    
    String error;
    String errorNodeKey;
    int retryCount;
    
    Instant createdAt;
    Instant updatedAt;
}

// NodeRun — one per node execution within a pipeline run
@Document("node_runs")
public class NodeRun {
    @Id String id;
    String pipelineRunId;
    String documentId;
    
    String nodeKey;
    String nodeType;           // "textExtraction", "aiClassification", etc.
    String executionCategory;  // BUILT_IN, SYNC_LLM -> ASYNC_JOB, etc.
    
    NodeRunStatus status;      // PENDING, RUNNING, WAITING, SUCCEEDED, FAILED, SKIPPED
    
    String jobId;              // For async nodes: the job correlation ID
    String idempotencyKey;     // For safe retries
    
    Map<String, Object> input;  // What went in (document state snapshot)
    Map<String, Object> output; // What came out (classification result, extracted text, etc.)
    
    Instant startedAt;
    Instant completedAt;
    long durationMs;
    
    String error;
    int retryCount;
}
```

**Indexes:**
- `pipeline_runs`: `{documentId: 1, createdAt: -1}`, `{status: 1}`, `{correlationId: 1}` (unique)
- `node_runs`: `{pipelineRunId: 1, nodeKey: 1}`, `{jobId: 1}` (unique sparse), `{status: 1}`

#### 1.2 Async LLM job pattern

Replace `SYNC_LLM` execution with `ASYNC_JOB`:

**New RabbitMQ topology:**
```
Exchange: gls.pipeline (topic, durable)

Queues:
  gls.pipeline.llm.jobs          — LLM classification/custom prompt requests
  gls.pipeline.llm.completed     — LLM job completion callbacks
  gls.pipeline.resume            — Pipeline resume after async node
  gls.pipeline.dlq               — Dead-letter for failed jobs
```

**New events:**
```java
record LlmJobRequestedEvent(
    String jobId,
    String pipelineRunId,
    String nodeRunId,
    String documentId,
    String nodeKey,
    String mode,           // CLASSIFICATION or CUSTOM_PROMPT
    String blockId,
    Integer blockVersion,
    String pipelineId,
    String extractedText,
    String fileName,
    String mimeType,
    long fileSizeBytes,
    String uploadedBy,
    String idempotencyKey
) {}

record LlmJobCompletedEvent(
    String jobId,
    String pipelineRunId,
    String nodeRunId,
    boolean success,
    String classificationResultId,
    String categoryId,
    String categoryName,
    String sensitivityLabel,
    List<String> tags,
    double confidence,
    boolean requiresHumanReview,
    String retentionScheduleId,
    List<String> applicablePolicyIds,
    Map<String, Object> customResult,  // For CUSTOM_PROMPT mode
    String error,
    Instant completedAt
) {}
```

**Engine changes to `PipelineExecutionEngine`:**
```
executePipeline(event):
  1. Create PipelineRun(status=RUNNING)
  2. Compile execution plan (topological sort — already exists)
  3. Walk nodes sequentially:
     
     For INLINE nodes (NOOP, BUILT_IN, ACCELERATOR):
       - Create NodeRun(status=RUNNING)
       - Execute handler (existing code)
       - Update NodeRun(status=SUCCEEDED, output, durationMs)
       - Continue to next node
     
     For ASYNC_JOB nodes (was SYNC_LLM):
       - Create NodeRun(status=WAITING, jobId=uuid)
       - Publish LlmJobRequestedEvent to gls.pipeline.llm.jobs
       - Update PipelineRun(status=WAITING, currentNodeKey, currentNodeIndex)
       - RETURN (release thread)
     
     For CONDITION nodes:
       - Evaluate condition from PipelineRun.sharedContext (not transient memory)
       - Route to correct branch
     
  4. After last node: PipelineRun(status=COMPLETED)

resumePipeline(completedEvent):
  1. Load PipelineRun by pipelineRunId
  2. Validate idempotencyKey (reject duplicates/late arrivals)
  3. Update NodeRun(status=SUCCEEDED, output=result)
  4. Store result in PipelineRun.sharedContext
  5. Resume walking from currentNodeIndex + 1
  6. Continue inline for fast nodes, pause again if another async node
```

**LLM worker changes to `ClassificationPipeline`:**
```
onLlmJobRequested(event):
  1. Check idempotency (have we already processed this jobId?)
  2. Classify or run custom prompt (existing code, no changes to LLM logic)
  3. Publish LlmJobCompletedEvent to gls.pipeline.llm.completed
  4. On failure: publish LlmJobCompletedEvent(success=false, error=message)
```

**Key design decisions:**
- Document status still updates at each stage (user-facing state unchanged)
- PipelineRun.sharedContext carries data between nodes (replaces reloading document after every node)
- Condition nodes evaluate from sharedContext, not from transient in-memory variables
- Late/duplicate completions are detected via idempotencyKey and ignored safely
- Failed async nodes set NodeRun(status=FAILED) and PipelineRun(status=FAILED)
- Retry from monitoring page creates a new PipelineRun, not a mutation of the old one

#### 1.3 Pipeline monitoring integration

Update the existing monitoring page to show PipelineRun/NodeRun data:
- Pipeline execution timeline (node-by-node with duration bars)
- Currently waiting nodes (which documents are awaiting LLM)
- Failed node detail (error message, retry button)
- Average duration per node type (from NodeRun aggregation)

This replaces the current "pipeline logs" SSE approach with structured data.

---

### WP2: MCP Caching + Performance (3-4 days)

#### 2.1 Caffeine cache on MCP tools

Add `spring-boot-starter-cache` + Caffeine to `gls-mcp-server`:

```java
@Configuration
@EnableCaching
public class CacheConfig {
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.setCacheSpecification("maximumSize=100,expireAfterWrite=300s");
        return manager;
    }
}
```

Apply `@Cacheable` to tools:
- `get_classification_taxonomy` — cache key: `"taxonomy"`, TTL 5 min
- `get_sensitivity_definitions` — cache key: `"sensitivities"`, TTL 5 min
- `get_document_traits` — cache key: `"traits"`, TTL 5 min
- `get_governance_policies` — cache key: `"policies:" + categoryId`, TTL 2 min
- `get_retention_schedules` — cache key: `"retention:" + scheduleId`, TTL 5 min
- `get_storage_capabilities` — cache key: `"storage:" + sensitivity`, TTL 5 min
- `get_metadata_schemas` — cache key: `"schema:" + categoryId`, TTL 2 min

Add `@CacheEvict` on admin governance update endpoints (publish a cache-invalidation event via RabbitMQ, MCP server listens and evicts).

#### 2.2 Bound the unbounded queries

- `getPiiDismissals()` — add `.limit(50)` and sort by `correctedAt` descending
- `getPiiCorrections()` — add `.limit(50)` if not already bounded
- Add pagination to correction history if > 100 records per category

#### 2.3 Consumer concurrency

```yaml
# application-docker.yaml for gls-app-assembly
spring:
  threads:
    virtual:
      enabled: true
  rabbitmq:
    listener:
      simple:
        concurrency: 3
        max-concurrency: 10
        prefetch: 3
```

With async LLM nodes, consumers process the fast portion of the pipeline (~1-5 seconds) then release the thread. 10 concurrent consumers can have 10 documents in the fast-node portion simultaneously.

---

### WP3: Testing (1.5-2 weeks)

Zero to sufficient test coverage for a 1.0.0 release. Focus on critical paths, not 100% coverage.

#### 3.1 Backend test infrastructure (2 days)

Add to `pom.xml`:
- `spring-boot-starter-test` (JUnit 5, Mockito, AssertJ)
- `testcontainers` (MongoDB, RabbitMQ) for integration tests
- `de.flapdoodle.embed.mongo` for unit tests

#### 3.2 Critical path unit tests (3-4 days)

| Test class | What it covers |
|---|---|
| `PipelineExecutionEngineTest` | Graph compilation, topological sort, condition evaluation, accelerator short-circuit, async node handoff |
| `PipelineResumeTest` | Resume from waiting state, idempotency key validation, late arrival rejection |
| `SyncLlmNodeExecutorTest` | Request building, response parsing, error handling, timeout behaviour |
| `ClassificationPipelineTest` | Prompt building, result parsing, PII merge logic |
| `GovernanceServiceTest` | Taxonomy retrieval, correction aggregation, policy matching, retention lookup |
| `DocumentServiceTest` | Status transitions, error state setting, slug generation |
| `AuthServiceTest` | JWT generation, validation, refresh, role extraction |
| `EnforcementServiceTest` | Policy application, retention assignment, storage tier selection |

#### 3.3 Integration tests (3-4 days)

| Test class | What it covers |
|---|---|
| `DocumentUploadFlowIT` | Upload -> RabbitMQ event -> PipelineRun created -> text extraction -> PII scan -> async LLM job published |
| `ClassificationCallbackIT` | LLM job completion -> pipeline resume -> governance applied -> document status INBOX |
| `ReviewQueueIT` | Low-confidence classification -> REVIEW_REQUIRED -> approve/override -> correction saved |
| `GovernanceAdminIT` | Create taxonomy -> create policy -> create pipeline -> verify end-to-end |

#### 3.4 Frontend tests (2-3 days)

Add Vitest + React Testing Library to `web/`:
- Auth flow (login, token refresh, protected routes)
- Document upload and status polling
- Pipeline editor (add node, connect, save)
- Review queue (approve, override actions)

---

### WP4: CI/CD Pipeline (3-4 days)

#### 4.1 GitHub Actions workflow

```yaml
# .github/workflows/ci.yml
name: CI
on: [push, pull_request]

jobs:
  backend-test:
    runs-on: ubuntu-latest
    services:
      mongodb:
        image: mongo:7
        ports: [27017:27017]
      rabbitmq:
        image: rabbitmq:4-management-alpine
        ports: [5672:5672]
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { java-version: 21, distribution: temurin }
      - run: cd backend && ./mvnw verify -pl gls-app-assembly -am

  frontend-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with: { node-version: 22 }
      - run: cd web && npm ci && npm run lint && npm test

  docker-build:
    needs: [backend-test, frontend-test]
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - run: docker compose build
```

#### 4.2 Release workflow

```yaml
# .github/workflows/release.yml
name: Release
on:
  push:
    tags: ['v*']

jobs:
  build-and-push:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - run: docker compose build
      - run: |
          # Tag and push images to registry
          # (GitHub Container Registry or Docker Hub)
```

---

### WP5: Production Hardening (1 week)

#### 5.1 Docker Compose production profile

Create `docker-compose.prod.yml` with:

```yaml
services:
  api:
    deploy:
      resources:
        limits:
          cpus: '2.0'
          memory: 2G
        reservations:
          cpus: '1.0'
          memory: 1G
      replicas: 2
    environment:
      - SPRING_PROFILES_ACTIVE=docker,production
    restart: always
    
  llm-worker:
    deploy:
      resources:
        limits:
          cpus: '1.0'
          memory: 1.5G
      replicas: 2
    restart: always
    
  mcp-server:
    deploy:
      resources:
        limits:
          cpus: '1.0'
          memory: 1G
    restart: always
    
  mongo:
    deploy:
      resources:
        limits:
          cpus: '4.0'
          memory: 8G
    volumes:
      - mongo_data:/data/db
    restart: always
    
  elasticsearch:
    deploy:
      resources:
        limits:
          cpus: '2.0'
          memory: 4G
    environment:
      - "ES_JAVA_OPTS=-Xms2g -Xmx2g"
    restart: always
    
  rabbitmq:
    deploy:
      resources:
        limits:
          cpus: '2.0'
          memory: 2G
    restart: always
    
  minio:
    deploy:
      resources:
        limits:
          cpus: '2.0'
          memory: 2G
    restart: always
    
  web:
    deploy:
      replicas: 2
      resources:
        limits:
          cpus: '1.0'
          memory: 1G
    restart: always
```

#### 5.2 Secret management

- Move all secrets to `.env.production` (not committed)
- Document required environment variables in `DEPLOYMENT.md`
- Admin password must be changed on first login (add `forcePasswordChange` flag)
- API keys and OAuth secrets via environment only

Required secrets:
```
MONGO_ROOT_PASSWORD
JWT_SECRET
ANTHROPIC_API_KEY
GOOGLE_OAUTH_CLIENT_ID
GOOGLE_OAUTH_CLIENT_SECRET
ADMIN_PASSWORD
PUBLIC_URL
MINIO_ACCESS_KEY
MINIO_SECRET_KEY
```

#### 5.3 Security hardening

- HTTPS via Cloudflare tunnel (already configured) or Let's Encrypt
- CORS: restrict origins to `PUBLIC_URL` only
- Rate limit on `/api/auth/login` (5 attempts / minute per IP)
- Rate limit on document upload (configurable per org, default 100/hour)
- Disable Actuator endpoints in production except `/health` and `/prometheus`
- MongoDB authentication (already using root auth)
- Ensure `X-Content-Type-Options`, `X-Frame-Options`, `Strict-Transport-Security` headers via nginx

#### 5.4 Nginx production config

```nginx
# Rate limiting
limit_req_zone $binary_remote_addr zone=login:10m rate=5r/m;
limit_req_zone $binary_remote_addr zone=upload:10m rate=100r/h;

server {
    # HSTS
    add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;
    add_header X-Content-Type-Options "nosniff" always;
    add_header X-Frame-Options "DENY" always;
    
    location /api/auth/login {
        limit_req zone=login burst=3 nodelay;
        proxy_pass http://api;
    }
    
    location /api/documents {
        limit_req zone=upload burst=10 nodelay;
        proxy_pass http://api;
    }
    
    # Upstream with multiple API instances
    upstream api {
        server api-1:8080;
        server api-2:8080;
    }
}
```

#### 5.5 Backup strategy

- MongoDB: daily `mongodump` to S3/MinIO backup bucket
- MinIO: versioning enabled on document bucket
- Elasticsearch: can be rebuilt from MongoDB (index, not source of truth)
- Cron job or systemd timer for automated backups

---

### WP6: Observability (3-4 days)

#### 6.1 Prometheus + Grafana

Add to docker-compose:
```yaml
prometheus:
  image: prom/prometheus:latest
  volumes:
    - ./monitoring/prometheus.yml:/etc/prometheus/prometheus.yml
  ports:
    - "9090:9090"

grafana:
  image: grafana/grafana:latest
  ports:
    - "3002:3000"
  volumes:
    - grafana_data:/var/lib/grafana
    - ./monitoring/dashboards:/etc/grafana/provisioning/dashboards
```

#### 6.2 Key metrics (via Micrometer)

Add `micrometer-registry-prometheus` to all Spring Boot services:

```
# Pipeline metrics
pipeline_run_total{status}              — Counter: completed/failed/cancelled runs
pipeline_run_duration_seconds           — Histogram: end-to-end pipeline duration
pipeline_node_duration_seconds{node}    — Histogram: per-node execution time
pipeline_waiting_count                  — Gauge: documents waiting on async nodes

# LLM metrics
llm_classification_total{status}        — Counter: success/failure/timeout
llm_classification_duration_seconds     — Histogram: LLM call duration
llm_tool_calls_total{tool}              — Counter: MCP tool invocations
llm_tokens_total{direction}             — Counter: input/output tokens
llm_estimated_cost_total                — Counter: running cost estimate

# MCP cache metrics
mcp_cache_hit_total{tool}               — Counter: cache hits
mcp_cache_miss_total{tool}              — Counter: cache misses

# Queue metrics
rabbitmq_queue_depth{queue}             — Gauge: messages pending per queue
```

#### 6.3 Grafana dashboards (provisioned)

- **Pipeline Overview**: throughput, latency percentiles, failure rate, queue depth
- **LLM Performance**: classification duration, tool call counts, token usage, cost
- **Infrastructure**: MongoDB ops, Elasticsearch indexing rate, RabbitMQ message rates, JVM heap

---

### WP7: Data Seeding + Deployment Docs (2-3 days)

#### 7.1 Governance data seeder

The existing `GovernanceDataSeeder` handles initial data. Verify it seeds:
- Default taxonomy (top-level categories at minimum)
- Default sensitivity labels (PUBLIC, INTERNAL, CONFIDENTIAL, RESTRICTED)
- Default retention schedules
- Default storage tiers
- Default PII type definitions
- Default pipeline with standard nodes
- Default prompt block for classification
- Default regex block for PII scanning
- Admin user account
- Default roles and features

#### 7.2 Deployment documentation

Create `DEPLOYMENT.md`:
- Prerequisites (Docker, Docker Compose, domain, Cloudflare account)
- Environment variable reference (all secrets documented)
- Step-by-step deployment instructions
- Post-deployment checklist (change admin password, configure Google OAuth, import governance pack)
- Backup and restore procedures
- Troubleshooting guide (common issues)
- Upgrade procedure (for future releases)

---

## Timeline

```
Week 1-2:   WP1 — Async pipeline architecture (PipelineRun/NodeRun, async LLM jobs, resume)
Week 2:     WP2 — MCP caching, query bounds, consumer concurrency (parallel with WP1 tail)
Week 3-4:   WP3 — Testing (unit + integration + frontend basics)
Week 4:     WP4 — CI/CD pipeline (parallel with WP3 tail)
Week 5:     WP5 — Production hardening (Docker, secrets, security, nginx, backups)
Week 5:     WP6 — Observability (Prometheus, Grafana, metrics — parallel with WP5)
Week 6:     WP7 — Data seeding verification, deployment docs, release
Week 6:     Final QA, tag v1.0.0
```

**Total: ~6 weeks**

---

## Compute Resources for 1.0.0

Target: single server deployment handling a pilot customer with 10K-100K documents.

### Minimum viable (single host)

| Component | CPU | RAM | Storage |
|---|---|---|---|
| API x2 | 4 cores | 4 GB | — |
| LLM worker x2 | 2 cores | 3 GB | — |
| MCP server x1 | 1 core | 1 GB | — |
| BERT classifier x1 | 2 cores | 2 GB | — |
| MongoDB | 4 cores | 8 GB | 200 GB SSD |
| Elasticsearch | 2 cores | 4 GB | 200 GB SSD |
| RabbitMQ | 1 core | 1 GB | 10 GB |
| MinIO | 1 core | 1 GB | 500 GB |
| Web x1 | 1 core | 512 MB | — |
| nginx | 0.5 core | 256 MB | — |
| Prometheus + Grafana | 1 core | 1 GB | 50 GB |
| **Total** | **~20 cores** | **~26 GB** | **~960 GB** |

**Recommended host:** 24-core, 32 GB RAM, 1 TB NVMe SSD

**Cloud equivalent:** AWS `m6i.4xlarge` (16 vCPU, 64 GB) or `c6i.4xlarge` (16 vCPU, 32 GB) — **~$300-500/month**

**LLM API cost (pilot):**
- 100K documents, 30% needing LLM (accelerators skip 70%)
- 30K classifications x ~$0.02 avg = **~$600 one-time for initial backlog**
- Steady state: ~$50-150/month depending on new document volume

### Throughput at 1.0.0

| Stage | Throughput | Bottleneck |
|---|---|---|
| Upload + extraction | ~10,000 docs/hour | MinIO + Tika |
| PII scanning | ~20,000 docs/hour | Regex, CPU-bound |
| Accelerator classification | ~5,000 docs/hour | BERT / similarity |
| LLM classification | ~200 docs/hour (2 workers) | Anthropic API latency |
| Governance enforcement | ~10,000 docs/hour | MongoDB writes |
| **End-to-end (with LLM)** | **~200 docs/hour** | **LLM is the bottleneck** |
| **End-to-end (accelerator hit)** | **~5,000 docs/hour** | **BERT inference** |

With 70% accelerator hit rate on a 100K document backlog:
- 70K via accelerators: ~14 hours
- 30K via LLM: ~150 hours (~6 days)
- **Total backlog processing: ~1 week**

---

## Frontend Decisions

| Item | Decision | Rationale |
|---|---|---|
| User self-registration | Not for 1.0.0 | Admin-provisioned accounts. Pilot customers get accounts created by admin. Self-registration is a post-1.0 feature. |
| User profile self-service | Not for 1.0.0 | Admins manage user details via `/admin/users`. Password change and profile editing deferred. |
| S3 / SharePoint / Box / SMB | UI visible, not functional | Storage provider icons remain in the Drives page as a roadmap signal. Only Google Drive and Local storage are wired to backends. |
| Duplicate `/pipelines` and `/blocks` routes | Removed | Consolidated under `/ai/pipelines` and `/ai/blocks`. Internal links updated. |

---

## Release Checklist

```
[x] Frontend: Remove duplicate /pipelines and /blocks routes (consolidated under /ai/*)
[x] Frontend: Update internal links from /blocks to /ai/blocks
[x] WP1: PipelineRun/NodeRun models and repositories
[x] WP1: Async LLM job events and RabbitMQ topology
[x] WP1: PipelineExecutionEngine refactored for async nodes
[x] WP1: PipelineResumer for callback-driven resume
[x] WP1: Monitoring page updated with execution timeline
[x] WP1: Idempotency keys and late-arrival handling
[x] WP2: Caffeine cache on MCP tools
[x] WP2: Cache eviction on governance admin changes
[x] WP2: Bounded PII queries (.limit())
[x] WP2: Consumer concurrency + virtual threads enabled
[x] WP3: Test infrastructure (JUnit, Testcontainers)
[x] WP3: Unit tests for pipeline engine, auth, governance
[x] WP3: Integration tests for upload->classify->enforce flow
[ ] WP3: Frontend tests for critical flows
[x] WP4: GitHub Actions CI workflow
[x] WP4: Release workflow for tagged builds
[x] WP5: docker-compose.prod.yml with resource limits
[x] WP5: Secret management via .env.production
[x] WP5: Security headers in nginx
[x] WP5: Rate limiting on auth + upload
[x] WP5: Backup automation
[x] WP6: Prometheus + Grafana containers
[x] WP6: Micrometer metrics on all services
[x] WP6: Grafana dashboards provisioned
[x] WP7: Governance seeder verified
[x] WP7: DEPLOYMENT.md written
[ ] WP7: Final QA pass
[ ] Tag v1.0.0
```

---

## Risk Register

| Risk | Impact | Mitigation |
|---|---|---|
| Async pipeline introduces bugs in state management | Documents stuck in WAITING forever | Stale detection: NodeRun WAITING > 10 min triggers alert + auto-retry. Monitoring page shows waiting nodes. |
| MCP cache serves stale data after admin changes | Wrong classifications until cache expires | Cache eviction on admin writes + short TTLs (2-5 min max) |
| Anthropic API rate limits at scale | Classification backlog grows | Backpressure: monitor queue depth, alert at >500 pending, pause ingestion if needed |
| BERT classifier accuracy too low | Too many documents fall through to LLM | Tune confidence threshold, add rules engine for common document types, train BERT on customer's data |
| Google Drive token expiry during long operations | File access fails mid-classification | Token refresh before each Drive API call (already implemented), retry on 401 |
| MongoDB performance under 100K documents | Slow queries on monitoring/search pages | Compound indexes (WP2), query explain analysis before release |
