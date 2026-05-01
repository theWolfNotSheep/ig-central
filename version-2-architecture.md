---
title: Multi-Tier Classification Architecture
lifecycle: forward
---

# Multi-Tier Classification Architecture

**Status:** Draft for review
**Last updated:** 2026-04-26
**Scope:** How the document classification pipeline decomposes into independently-scalable containers when classification is performed across BERT, SLM, and LLM tiers, and how those containers communicate via REST (with RabbitMQ retained for pipeline lifecycle events and async LLM dispatch).

**Where we are:** topology, REST contracts, unhappy-path semantics, audit model and tier strategy are settled (§1–§8). Phase A/B carve-outs are queued (§9). Currently working through the **Classifier-Router OpenAPI spec** — see §11 for the open design decisions blocking the spec draft.

---

## 1. Context

The current pipeline runs a single classification path (LLM via `igc-llm-orchestration`). The roadmap calls for a tiered model strategy:

- **BERT** — fine-tuned encoder, fast and free, handles patterns the org has already taught it.
- **SLM** — Haiku / Llama-7B / qwen-7B, low-cost reasoning over MCP tool context.
- **LLM** — Sonnet/Opus, deep reasoning for novel or ambiguous documents.

The goal (per `CLAUDE.md` "Path to cheaper models") is for correction history to push more traffic down-tier over time. To make that practical operationally, each tier needs its own deployable so they can be sized, restarted, rate-limited, and replaced independently.

`PipelineBlock.BlockType` already includes `BERT_CLASSIFIER` alongside `PROMPT`, `ROUTER`, and `ENFORCER` — the runtime topology described here is the missing physical expression of that block model.

---

## 2. Tier strategy

| Tier | Latency | Cost | Scaling driver | Notes |
|---|---|---|---|---|
| **BERT** | 20–80 ms | ~free | Throughput per replica; benefits from batching | Loads ~500 MB ONNX model into RAM; restart has warm-up cost. |
| **SLM** | 1–3 s | low | LLM rate-limit pool or local GPU count | Anthropic Haiku and on-prem Ollama use disjoint quotas; can split per provider. |
| **LLM** | 5–30 s | high | Anthropic concurrency | Already async via RabbitMQ; keep as-is. |
| **Router** | < 5 ms | free | Stateless; horizontal | Where the cascade policy lives; tuned more often than the models. |

**Cascade policy** is held in a `ROUTER` block, hot-loadable from MongoDB:

```json
{
  "bertAcceptThreshold": 0.92,
  "slmAcceptThreshold": 0.75,
  "perCategoryOverrides": {
    "HR > Maternity": { "bertAccept": 0.85 },
    "Legal > Contract": { "bertAccept": 1.01 }
  },
  "minCorrectionsForBert": 50,
  "fallbackOnTierUnavailable": true
}
```

`bertAccept = 1.01` effectively disables BERT for that category until enough corrections accumulate.

---

## 3. Container topology

### System map

The complete service catalogue and the connections between them. The numbered stages ①–⑤ correspond directly to the five conceptual stages in §5 — same numbering at both abstraction levels.

```
                    ┌─────────────────────────────────────┐
                    │  External: Cloudflare tunnel        │   (ingress)
                    └─────────────────┬───────────────────┘
                                      ▼
                    ┌─────────────────────────────────────┐
                    │              nginx                  │
                    └─────────┬───────────────┬───────────┘
                              ▼               ▼
                    ┌──────────────┐  ┌────────────────┐
                    │   igc-web    │  │    igc-api     │   ① INGEST
                    │   (Next.js)  │  │  HTTP / admin  │   (Class A)
                    └──────────────┘  └────────┬───────┘
                                               │ enqueue +
                                               │ audit_outbox
                                               ▼
   ╔════════════════════════════════════════════════════════════════════════╗
   ║              RabbitMQ — 3-node cluster, quorum queues                  ║
   ║                                                                        ║
   ║   igc.documents.{ingested, classified, *.dlq}                          ║
   ║   igc.pipeline.{llm.jobs, llm.completed, resume, *.dlq}                ║
   ║   igc.audit.{tier1.domain, tier2.system, tier3.trace, *.dlq}           ║
   ╚════╤════════════════════════╤══════════════════════════╤═══════════════╝
        │ consume                │ ⑤ classified             │ audit
        ▼                        ▼                          ▼
 ┌──────────────┐         ┌───────────────┐    ┌────────────────────────┐
 │ orchestrator │         │  indexing-    │    │  audit-collector       │
 │   ×N  (B)    │         │  worker ×N    │    │   T1 ×2 leader (D)     │
 │              │         │     (B)       │    │   T2 ×N horiz   (B)    │
 └──────┬───────┘         └──────┬────────┘    └──────┬─────────────────┘
        │ REST per stage         ▼                    ▼
        │ (idempotent on    ┌──────────────┐    ┌──────────────────────┐
        │  nodeRunId;       │Elasticsearch │    │ T1 WORM (S3 Obj Lock │
        │  persists Pipe-   └──────────────┘    │   + Athena)          │
        │  lineRun + NodeRun)                   │ T2 OpenSearch hot    │
        ▼                                       │   + S3 cold          │
                                                └──────────────────────┘
─── ② PARSE ──────────────────────────────────  ┌──────────────────────┐
                                                │  otel-collector      │
   ┌────────────────────────┐                   │   (Tempo / Jaeger)   │
   │ extraction family      │                   └──────────────────────┘
   │ (mime-routed; A)       │── MinIO / S3 ◀── raw bytes
   │  • extraction-tika     │
   │  • extraction-ocr (GPU)│
   │  • extraction-audio    │
   │  • extraction-archive  │── recurses on child docs into ①
   └────────────┬───────────┘
                │ text  (or textRef in MinIO if > 256 KB)
                ▼
─── ③ CLASSIFY ───────────────────────────────────────────────────────────

   ┌──────────────────────────────────┐
   │  igc-classifier-router (A)       │ ◀── ROUTER block (cascade policy)
   │  task-agnostic cascade           │      Used for classify AND for the
   │                                  │      ④ scans + metadata extraction
   └─┬────────┬─────────┬─────────────┘
     ▼        ▼         ▼
   ┌─────┐┌──────┐┌────────────┐
   │bert ││ slm- ││  llm-      │ ── Anthropic Sonnet/Haiku, Ollama
   │ inf ││ work ││  worker    │   sync entry; async path ──┐
   │ (C) ││ (A)  ││ (A sync,   │                            │
   └──┬──┘└──┬───┘│  B async)  │                            │ to igc.pipeline.
      │      │    └─────┬──────┘                            │     llm.jobs
      │      └────┬─────┘                                   │
      │           ▼ MCP tool calls                          │
      │    ┌─────────────────────┐                          │
      │    │  igc-mcp-server     │── MongoDB (corrections,  │
      │    │   Class A, SSE      │   taxonomy, schemas)     │
      │    └─────────────────────┘                          │
      ▼                                                     │
   MinIO model artifacts ◀── ONNX ── igc-bert-trainer ──────┘
                                       (Python, E, GPU Job;
                                        kicked off by scheduler)

                │ category, sensitivity, confidence
                ▼
─── ④ EXTRACT & ENFORCE — taxonomy-driven, conditional fan-out ───────────

   ┌─────────────────────────────────────────────────────────────────┐
   │  Orchestrator looks up the chosen taxonomy node and dispatches  │
   │  ONLY the work that node demands. Many documents need none of   │
   │  the scans (marketing copy, public docs) — others need all.     │
   └──┬───────────┬───────────┬───────────────────┬──────────────────┘
      ▼           ▼           ▼                   ▼
   ┌──────┐  ┌──────┐  ┌──────┐    ┌──────────────────────────┐
   │ PII  │  │ PHI  │  │ PCI  │    │ structured metadata via  │
   │ scan │  │ scan │  │ scan │    │ MetadataSchema linked to │
   │      │  │      │  │      │    │ the taxonomy node        │
   └──┬───┘  └──┬───┘  └──┬───┘    └──────────────┬───────────┘
      └─────────┴─────────┴───────────────────────┘
                          │
                          ▼
   ╔═══════════════════════════════════════════════════════════════════╗
   ║  Each is a call BACK through igc-classifier-router with a         ║
   ║  different prompt block — same cascade (BERT→SLM→LLM), same MCP   ║
   ║  context, different task. No new containers per scan type;        ║
   ║  the suitable model tier is selected by ROUTER block per task.    ║
   ╚═══════════════════════════════════════════════════════════════════╝
                          │ piiHits[] (or none),
                          │ extractedMetadata{}
                          ▼
   ┌──────────────────────────────┐
   │ igc-enforcement-worker (A)   │── retention, sensitivity, access
   │                              │   controls, legal-hold — driven by
   └──────────────┬───────────────┘   the taxonomy node, not hardcoded
                  │ status = GOVERNANCE_APPLIED
                  ▼ AMQP igc.documents.classified
─── ⑤ TIDY UP ────  (consumed by indexing-worker, top of diagram)

═══════════════ OFFLINE & SUPPORT ══════════════════════════════════════════
   ┌──────────────────────┐         ┌──────────────────────┐
   │ igc-connectors       │── ① ──▶ │ igc-scheduler        │
   │  (Drive, Gmail; D /  │ ingest  │  (singleton; D)      │
   │   sharded by source) │         │  • stale recovery    │
   │  per-source watches  │         │  • retention sweeps  │
   └──────────────────────┘         │  • training advisor  │
                                    │  • outbox reconciler │
                                    └──────────────────────┘
```

**Legend** — `(A)` stateless RPS-scaled, `(B)` queue-driven on lag, `(C)` warm-up bound, `(D)` singleton with leader, `(E)` batch / on-demand. See *Scaling profile by class* below.

What the diagram encodes architecturally:

- **Classify before extract.** PII / PHI / PCI scanning and metadata extraction are *taxonomy-scoped* — the category chosen in ③ selects which (if any) of these to run in ④. Marketing and public documents skip the scans entirely; medical records run PHI; HR runs PII; finance runs PCI; many run none.
- **The cascade is task-agnostic.** `igc-classifier-router` is the model-tier router for *any* prompt-block-driven task — classification, sensitive-data scans, metadata extraction. Different blocks, same cascade. No need for parallel scanner-routers per data class.
- **Three independent message buses.** `igc.documents.*` (pipeline lifecycle), `igc.pipeline.llm.*` (slow LLM dispatch), `igc.audit.*` (audit fan-out). Failure in one does not block the others.
- **MCP is read-mostly and downstream.** Tool surface for SLM and LLM, not a participant in orchestration. Caching it well is what keeps the cascade fast — and stage ④ amortises across the cache that ③ already warmed for the same document.
- **bert-trainer is offline.** No place in the request path. Its only contract with serving is the ONNX artefact in MinIO and the new `BERT_CLASSIFIER` block version — that seam lets training and serving evolve independently.
- **Audit is its own pipeline.** Outbox-relay-collector pattern from §7.3, completely orthogonal to the document pipeline. Either side can be down without taking the other with it.
- **Connectors and scheduler emit events into the same bus the API uses,** so downstream is uniform regardless of source. Sharding (per-source watches) prevents them from being throughput bottlenecks.
- **Storage is shared but role-separated.** MongoDB is the system of record; MinIO holds bytes and models; Elasticsearch is the query index; the audit stores are isolated. No service writes the same fact to two of those.

Replica counts and HPA signals are not shown — see *Scaling profile* and *HPA signals* below. Failure-mode arrows are not shown — see §6.

### Container roster

| Container | Module(s) it runs | Listens on | Calls |
|---|---|---|---|
| `igc-api` | controllers from `igc-app-assembly` | HTTP 8080 | Mongo, Mongo-backed config |
| `igc-orchestrator` | `PipelineExecutionEngine`, both pipeline consumers | AMQP `igc.documents.ingested`, `igc.pipeline.llm.completed` | extraction-worker, classifier-router, enforcement-worker (REST) |
| `igc-extraction-*` (family) | NEW — mime-routed parse-only family: `tika`, `ocr`, `audio`, `archive`. PII / PHI / PCI moved out of this stage. | HTTP per family member | MinIO |
| `igc-classifier-router` | NEW — task-agnostic cascade router. Used for classification AND for taxonomy-driven post-classify scans (PII/PHI/PCI) and metadata extraction — different prompt blocks, same cascade. | HTTP 8091 | bert-inference, slm-worker, llm-worker (REST) |
| `igc-bert-inference` | NEW — JVM + ONNX Runtime / DJL | HTTP 8092 | MinIO (model artifacts) |
| `igc-bert-trainer` | NEW — Python, HuggingFace transformers | Triggered by `BertTrainingJobRepository` | Mongo (training samples), MinIO (publishes ONNX) |
| `igc-slm-worker` | Adapter layer over Anthropic Haiku / Ollama | HTTP 8093 | mcp-server, Anthropic, Ollama |
| `igc-llm-worker` | existing `igc-llm-orchestration` | AMQP `igc.pipeline.llm.jobs` (kept) + HTTP 8094 (new sync entry) | mcp-server, Anthropic |
| `igc-mcp-server` | existing `igc-mcp-server` | HTTP 8081 (SSE) | Mongo |
| `igc-enforcement-worker` | `igc-governance-enforcement` | HTTP 8095 + AMQP `igc.documents.classified` | Mongo |
| `igc-indexing-worker` | NEW | AMQP `igc.documents.classified` | Elasticsearch |
| `igc-connectors` | Drive monitor, Gmail polling | scheduled | Google APIs, Mongo, MinIO |
| `igc-scheduler` | Stale recovery, retention, BERT advisor | scheduled | Mongo, Rabbit |
| `igc-audit-collector` | NEW — single-writer Tier 1 (chain), horizontal Tier 2 (see §7) | AMQP `igc.audit.tier1.*`, `igc.audit.tier2.*` | Tier 1 store (WORM), Tier 2 store (OpenSearch + S3) |

### Scaling profile by class

The roster collapses into five distinct *scaling shapes*. Each class has a different autoscaling signal, a different minimum replica count, and a different failure-mode profile. Picking the right shape per container matters more than picking the right replica count — the count is the consequence, not the cause.

**Class A — Stateless HTTP, horizontal on request load**
- `igc-api`, `igc-classifier-router`, `igc-slm-worker`, `igc-pii-scanner`, the extraction family (`igc-extraction-tika`, `-ocr`, `-audio`, …), `igc-mcp-server`.
- HPA driven by concurrent in-flight requests and CPU. Minimum 2 for HA. Replicas are interchangeable — any replica serves any request.
- Failure mode: a sick replica is evicted by the readiness probe; load redistributes.

**Class B — Queue-driven, horizontal on queue lag**
- `igc-orchestrator`, `igc-llm-worker` (async path), `igc-indexing-worker`, `igc-enforcement-worker` (Rabbit mode).
- HPA driven by AMQP queue depth, not CPU. Each replica is a *competing consumer* on the same queue.
- Idempotency on `nodeRunId` is mandatory (§6.11) — two replicas grabbing the same retry message must converge to one outcome.
- Failure mode: a stuck consumer's prefetched messages return to the queue on connection close; another replica picks them up.

**Class C — Stateful, warm-up bound**
- `igc-bert-inference`.
- Replicas are *not* immediately interchangeable — each loads a ~500 MB ONNX model into RAM and warms the runtime before serving.
- HPA on RPS, but with deliberately long cool-down (5–10 min scale-down) to avoid thrashing the warm pool. Memory-bound, not CPU-bound.
- Rolling reload on new `BERT_CLASSIFIER` block versions (§6.3) — the replica reports `warming` and returns 503 until ready.

**Class D — Singleton with leader election**
- `igc-scheduler`. Some flows in `igc-connectors` (one watch per shared drive, not one per replica).
- Run 2+ replicas for HA, but only one acts at a time. ShedLock on Mongo for distributed locks; per-job leases.
- Cannot scale *throughput* by adding replicas — to scale, **shard the work** (one leader per source, not one leader for all sources), or decompose the singleton into independent jobs each with its own lock key.
- Failure mode: leader dies → lock expires → another replica takes over within the lease TTL.

**Class E — Batch / on-demand**
- `igc-bert-trainer`.
- Spawned as a one-shot Kubernetes Job when a `BertTrainingJob` row appears; pod terminates on completion. Concurrency, not replica count, is the scaling lever — bounded by GPU pool size.

### Scaled-out shape

```
   ╔══════════════ Class A — stateless HTTP, RPS-scaled ═══════════════╗
   ║                                                                    ║
   ║  api ×3    classifier-router ×4    slm-worker ×3    mcp ×2         ║
   ║  pii-scanner ×3    extraction-tika ×4    extraction-ocr ×2 (GPU)   ║
   ║                          ↑                                         ║
   ║                          │                                         ║
   ║              HPA: in-flight requests + CPU                         ║
   ║                  (min 2 each, max bounded by                       ║
   ║                   Mongo / MinIO / vendor-API ceilings)             ║
   ╚════════════════════════════════════════════════════════════════════╝

   ╔══════════════ Class B — queue-driven, lag-scaled ═════════════════╗
   ║                                                                    ║
   ║  orchestrator ×4         llm-worker ×6        indexing ×3          ║
   ║       ↑                       ↑                    ↑               ║
   ║       │                       │                    │               ║
   ║       └─── HPA: RabbitMQ queue depth (KEDA-style) ─┘               ║
   ║                                                                    ║
   ║   Rabbit cluster (3 nodes, quorum queues) absorbs the burst        ║
   ║   competing-consumer pattern; idempotency on nodeRunId             ║
   ╚════════════════════════════════════════════════════════════════════╝

   ╔══════════════ Class C — stateful, warm-up bound ══════════════════╗
   ║                                                                    ║
   ║   bert-inference ×2-6                                              ║
   ║      ↑    ↑                                                        ║
   ║      │    │ readiness=warming gates traffic until model loaded     ║
   ║      │    │                                                        ║
   ║      HPA: RPS with 5–10 min scale-down cool-down (avoid thrash)    ║
   ║      memory-bound; sized as model size × pod count                 ║
   ╚════════════════════════════════════════════════════════════════════╝

   ╔══════════════ Class D — singletons (leader-elected) ══════════════╗
   ║                                                                    ║
   ║   scheduler ×2          connectors ×N (sharded by source-hash)    ║
   ║      ↑                       ↑                                     ║
   ║      ShedLock                one leader per shared drive /         ║
   ║      (HA, 1 active)          mailbox, not one per fleet            ║
   ╚════════════════════════════════════════════════════════════════════╝

   ╔══════════════ Class E — batch jobs ═══════════════════════════════╗
   ║                                                                    ║
   ║   bert-trainer  (k8s Job, on-demand; concurrency bounded by GPU)   ║
   ╚════════════════════════════════════════════════════════════════════╝
```

### HPA signals and sizing

| Container | Class | HPA signal | Min | Bounded by |
|---|---|---|---|---|
| `igc-api` | A | In-flight requests + CPU | 2 | Mongo connection pool |
| `igc-orchestrator` | B | `igc.documents.ingested` depth | 2 | Mongo + Rabbit channels |
| `igc-classifier-router` | A | RPS + CPU | 2 | Downstream tier capacity |
| `igc-extraction-{tika,ocr,audio,…}` | A | RPS per mime family | 2 each | MinIO bandwidth, GPU (OCR/audio) |
| `igc-pii-scanner` | A | RPS + CPU | 2 | CPU (regex throughput) |
| `igc-bert-inference` | C | RPS + CPU, slow cool-down | 2 | Memory per replica × pod count |
| `igc-bert-trainer` | E | Pending `BertTrainingJob` rows | 0 (on-demand) | GPU pool |
| `igc-slm-worker` | A + cost gate | RPS + provider quota | 2 | Anthropic Haiku rate limit |
| `igc-llm-worker` | B + cost gate | `igc.pipeline.llm.jobs` depth + budget | 2 | Anthropic concurrency / £ budget |
| `igc-mcp-server` | A | RPS + CPU | 2 | Mongo connection pool |
| `igc-enforcement-worker` | A or B | RPS or queue depth | 2 | Mongo |
| `igc-indexing-worker` | B | `igc.documents.classified` depth | 2 | Elasticsearch ingest |
| `igc-scheduler` | D | None — singleton | 2 (1 active) | n/a |
| `igc-connectors` | D / sharded | Per-source partition + API quota | 2 (HA) | Google / Microsoft API limits |

### Cross-cutting scaling constraints

Replica count does not help when the bottleneck is upstream of the pod fleet. Six constraints to pin down before any HPA bounds get committed.

- **Multiplicative resource pools.** Every replica claims Mongo connections, Rabbit channels, MinIO multipart slots, ES bulk indexers. The fleet sum is what matters. Scaling `igc-orchestrator` from 4 → 40 replicas with a 50-connection-per-replica pool means 2,000 Mongo connections — well past most ceilings. **Rule:** the HPA upper bound is itself a sizing decision; per-replica pools must be sized as `ceiling ÷ max replicas`, not in isolation.
- **Vendor API limits trump replica count.** `igc-llm-worker` and `igc-slm-worker` are bound by Anthropic concurrency and rate limits, not their own replicas. Scaling past the quota produces 429s, not throughput. Two controls needed: (a) HPA upper bound matches the quota; (b) an in-process semaphore caps in-flight calls per replica so a burst doesn't trip rate limits before the autoscaler reacts.
- **Cost-bound tiers need a budget gate, not a replica gate.** Autoscaling on queue depth alone will happily empty a 100k-document reclassification backlog at peak Anthropic rates. A daily / per-job spending cap belongs on `igc-llm-worker` and on `igc-classifier-router`'s escalation logic. Same fail-safe spirit as the §6.5 outbox.
- **Cold-start hurts BERT.** Aggressive scale-down on `igc-bert-inference` drops warm replicas; the next spike hits cold ones with full model-load + JIT cost. Scale-down cool-down measured in *minutes*. Optionally pre-warm replicas with a synthetic `/v1/infer` call in the readiness probe so the ONNX session is JIT-compiled before live traffic lands.
- **Singletons cannot be scaled out.** `igc-scheduler`, per-drive Drive watches, and per-mailbox Gmail polls are single-leader by nature. Parallelism comes from sharding the *work* (one leader per source) or decomposing the singleton into independent jobs with separate lock keys — not from adding replicas.
- **RabbitMQ is a tier, not a given.** A single Rabbit node is the choke point at this fleet size. Entry-level shape: 3-node cluster with quorum queues, HA policy on every queue, dedicated channels-per-replica budget. Same multiplicative trap as Mongo — replicas × channels can exhaust the broker.

### Honest health probes are the contract

Two probes per container, doing different jobs:

- **Liveness** — process alive. Restart on failure.
- **Readiness** — *and* downstream dependencies usable: Mongo reachable, Rabbit channel open, model loaded and warm, vendor API circuit closed. Failure pulls the replica from the load balancer without restarting it.

Lie-by-omission readiness (replica returns 200 while its model is still loading, or while its Rabbit connection is broken) breaks every other safety mechanism in this document. The classifier-router's `fallbackOnTierUnavailable` policy (§6.2) only works because `igc-bert-inference` honestly returns 503 during warm-up rather than 200 with garbage.

---

## 4. REST contracts

> Pipeline lifecycle stays on RabbitMQ for durability and retry. REST is used **inside** a pipeline node for synchronous calls. LLM stays Rabbit-async because of its tail latency.

### 4.1 Orchestrator → Extraction

```
POST /v1/extract
{
  "documentId": "...", "storageRef": {"bucket":"...","key":"..."},
  "mimeType": "application/pdf",
  "extractorBlockId": "tika-default", "extractorBlockVersion": 3
}
→ 200
{ "text": "...", "textTruncated": false,
  "piiHits": [{"type":"NI_NUMBER","start":1204,"end":1213,"confidence":0.95}],
  "extractionMs": 412 }
```

### 4.2 Orchestrator → Classifier-Router

```
POST /v1/classify
{
  "documentId": "...", "pipelineRunId": "...", "nodeRunId": "...",
  "text": "...",                                       // or { "textRef": "minio://..." } for big docs
  "mimeType": "application/pdf",
  "hints": { "piiHits": [...], "extractedTitle": "..." },
  "blocks": {
    "routerBlockId": "tier-policy-v4",
    "bertBlockId":   "uk-hr-bert-v2",
    "slmPromptId":   "haiku-classify-v7",
    "llmPromptId":   "sonnet-classify-v3"
  },
  "callback": { "mode": "sync" | "async-rabbit",
                "completedRoutingKey": "pipeline.llm.completed" }
}
→ 200 (sync, BERT or SLM accepted)
{
  "category": "HR > Maternity", "sensitivity": "CONFIDENTIAL",
  "confidence": 0.94, "tier": "BERT" | "SLM",
  "modelUsed": "uk-hr-bert-v2",
  "extractedMetadata": { ... },
  "trace": [ {"tier":"BERT","confidence":0.94,"latencyMs":42,"decision":"ACCEPT"} ],
  "totalLatencyMs": 58, "costUnits": 0
}
→ 202 (escalated to LLM, async)
{ "jobId": "...", "status": "DISPATCHED_LLM" }
```

### 4.3 Router → BERT inference

```
POST /v1/infer
{ "text": "...", "modelVersion": "uk-hr-bert-v2", "topK": 3 }
→ 200
{ "predictions": [
    {"category":"HR > Maternity","probability":0.94},
    {"category":"HR > Parental","probability":0.04} ],
  "modelVersion": "uk-hr-bert-v2", "latencyMs": 42 }

POST /v1/models/reload   (called when a new BERT_CLASSIFIER block version is published)
GET  /v1/models          (reports loaded versions and warm-up status)
```

### 4.4 Router → SLM worker

```
POST /v1/classify
{ "text": "...", "promptBlockId": "haiku-classify-v7", "promptVersion": 7,
  "bertHints": { "top": ["HR > Maternity","HR > Parental"] },
  "model": "claude-haiku-4-5" | "ollama:qwen2.5:7b",
  "mcpEndpoint": "http://igc-mcp-server:8081",
  "timeoutMs": 8000,
  "documentId": "...", "nodeRunId": "..." }
→ 200
{ "category": "...", "sensitivity": "...", "confidence": 0.81,
  "extractedMetadata": {...}, "reasoning": "...",
  "tokensIn": 2104, "tokensOut": 312, "latencyMs": 1840 }
```

The SLM worker calls MCP itself for `get_correction_history`, `get_metadata_schemas`, etc. Router does not pre-fetch — see decision 8.1.

### 4.5 Router → LLM worker (async)

```
POST /v1/classify
{ ... same shape, model: "claude-sonnet-4-6",
  "callback": { "mode": "async-rabbit",
                "completedRoutingKey": "pipeline.llm.completed",
                "correlationId": "<nodeRunId>" } }
→ 202 { "jobId": "..." }
```

Completion arrives as `LlmJobCompletedEvent` on `igc.pipeline.llm.completed`. The orchestrator's existing `PipelineResumeConsumer` resumes the pipeline run.

### 4.6 SLM / LLM → MCP server

Unchanged: SSE tool calls (`get_taxonomy`, `get_correction_history`, `get_org_pii_patterns`, `get_metadata_schemas`, `save_classification`).

---

## 5. Happy path

The document's journey is five conceptual stages. Implementation detail — which container does what, REST vs Rabbit, the BERT/SLM/LLM cascade inside step 3 — lives in §3 and §4. This section is the *intent* that everything else serves, and the contract used to drive the functional and non-functional design of each container.

```
 1. INGEST
    Capture the file and everything that can be known about it without
    opening its body.
       Inputs:   raw bytes, source (upload | connector), source-supplied
                 context (Drive fileId, Gmail messageId, uploader, …)
       Captures: filename, size, hash, mimeType, owner, source path,
                 created/modified timestamps, connector reference
       Stores:   raw bytes in object storage; DocumentModel in MongoDB
       Status:   UPLOADED

 2. PARSE
    Turn the file into searchable text, regardless of format.
       Inputs:   raw bytes + mimeType
       Outputs:  extracted text (inline or textRef for large bodies),
                 page / segment structure where it applies, detected
                 language, parse-time confidence, parse-time metadata
       Format-aware: native text  /  office-doc parsing  /  OCR for
                     image-and-scan PDFs  /  audio transcription  /
                     archive recursion (zip / mbox / pst)
       Status:   PARSED

 3. CLASSIFY
    Decide what this document IS, using rules derived from the system,
    the data we hold, and the people who corrected past decisions.
       Inputs:   parsed text + file metadata + hints
       Rules:    taxonomy (the categories the org cares about),
                 prior human corrections (correction history via MCP),
                 organisation-specific signals (PII patterns, metadata
                 schemas linked to candidate categories)
       Outputs:  category (taxonomy node), sensitivity, confidence,
                 rationale, supporting evidence, tier-of-decision
       Status:   CLASSIFIED

 4. EXTRACT  &  ENFORCE
    With a classification in hand, do the work that depends on knowing
    what the document is. Both halves are driven by the taxonomy node
    chosen in step 3, not hardcoded.
       Extract:  PII per the patterns for this category;
                 structured metadata via the MetadataSchema(s) linked
                 to this taxonomy node (employee_name, leave_type,
                 start_date, contract_value, …)
       Enforce:  retention rules, sensitivity flags, access controls,
                 legal-hold flags — all derived from the taxonomy
       Outputs:  piiHits[], extractedMetadata{}, applied governance
                 state on the document
       Status:   GOVERNANCE_APPLIED

 5. TIDY UP
    Make the document discoverable, durable, and complete.
       Index:     document body + extractedMetadata to the search index
       Finalise:  pipeline run closed, counters incremented, derived
                  views invalidated where relevant
       Close:     final Tier 1 audit event hash-chained for this
                  document's lifecycle entry
       Status:    INDEXED


 — at every step —
    audit_outbox is committed in the same Mongo transaction as state.
    outbox-relay publishes to igc.audit.{tier1.domain | tier2.system}.
    igc-audit-collector  drains, hash-chains Tier 1 → WORM, fans Tier 2 → OpenSearch.
    traceparent is propagated end-to-end; otel-collector receives spans.
```

The order matters. **Classify before extract** — PII patterns and metadata schemas are *taxonomy-scoped*, so running them blind on every document wastes effort and misses category-specific signals. The taxonomy node from step 3 selects which extractors and which governance rules apply in step 4.

---

## 6. Unhappy path

Every tier and every container must explicitly handle failure and never leave a document in an in-flight state. The status flow extends the existing pattern from `CLAUDE.md`:

```
UPLOADED → PROCESSING → PROCESSED → CLASSIFYING → CLASSIFIED → GOVERNANCE_APPLIED → INDEXED
              ↓                          ↓                          ↓                 ↓
       PROCESSING_FAILED        CLASSIFICATION_FAILED       ENFORCEMENT_FAILED    INDEX_FAILED
              ↓                          ↓                          ↓                 ↓
           (retry)                    (retry)                    (retry)            (retry)
```

`CLASSIFICATION_FAILED` covers all three tiers — the `lastErrorStage` field is what disambiguates (`BERT`, `SLM`, `LLM`, `ROUTER`).

### 6.1 Extraction worker failure

| Failure | Behaviour |
|---|---|
| Tika OOM on huge PDF | Return HTTP 413. Orchestrator sets `PROCESSING_FAILED`, `lastErrorStage = EXTRACTOR`. Document is **not** retried automatically — same input would OOM again. Human action required (re-upload smaller, or change extractor block to skip large files). |
| Encrypted / corrupt file | Return HTTP 422. Same handling — no auto-retry, surface in monitoring. |
| MinIO unreachable | Return HTTP 503. Orchestrator marks `PROCESSING_FAILED` with retryable flag, scheduler re-queues after backoff. |
| Tika hangs | Hard timeout (45 s) at the orchestrator's REST client. Same as 503. |

### 6.2 Classifier-router failure

| Failure | Behaviour |
|---|---|
| `ROUTER` block missing or invalid | Return HTTP 500 with code `ROUTER_BLOCK_INVALID`. Orchestrator marks `CLASSIFICATION_FAILED`. Admin must fix the block. **Not** retryable until block fixed — block-version pinning means re-run picks up the fix automatically once published. |
| All tiers unreachable | Return HTTP 503. Orchestrator marks `CLASSIFICATION_FAILED` retryable. |
| BERT down, but `fallbackOnTierUnavailable=true` | Skip BERT, go straight to SLM. Trace records `tier=BERT, decision=SKIPPED_UNAVAILABLE`. |
| BERT down, fallback disabled | Return HTTP 503 — fail fast rather than silently downgrading quality. Configurable per category. |
| Router crash mid-cascade after LLM dispatched | LLM job carries `correlationId = nodeRunId` and writes back via Rabbit. Another router replica picks up nothing — the **orchestrator's** resume consumer is what completes the run, not the router. Router state is fully transient. |

### 6.3 BERT inference failure

| Failure | Behaviour |
|---|---|
| Model artifact missing | Refuse start-up; readiness probe fails. K8s does not route traffic to a replica with no model loaded. |
| ONNX runtime crash | Replica returns 503 to router; router applies `fallbackOnTierUnavailable` policy. Container restart reloads model. |
| Predict OOM (extreme text) | Return HTTP 413 with truncation hint. Router truncates and retries once, otherwise escalates to SLM. |
| Cold-start latency spike after `models/reload` | Reload is asynchronous: replica reports `warming` in `/v1/models` and returns 503 to inference until warm. Rolling reload across replicas avoids quality dip. |

### 6.4 SLM worker failure

| Failure | Behaviour |
|---|---|
| Anthropic Haiku 429 (rate limit) | Surface `Retry-After`. Router waits up to 1 s, otherwise escalates to LLM with reason `SLM_RATE_LIMITED`. |
| Anthropic 5xx | Circuit breaker: after 5 consecutive failures, SLM marked degraded for 60 s and router skips it. |
| Ollama container down | Same circuit-breaker path; alert raised. |
| Timeout (8 s default) | Escalate to LLM with reason `SLM_TIMEOUT`. |
| MCP server unreachable | SLM proceeds **without** correction context, logs `mcp.unavailable=true` on the result, and returns confidence capped at `slmAcceptThreshold - 0.05` (forces escalation). This stops a stale-context-induced miscategorisation. |

### 6.5 LLM worker failure

| Failure | Behaviour |
|---|---|
| Anthropic 5xx / circuit breaker open | Job consumed but fails. `LlmJobFailedEvent` emitted on `igc.pipeline.llm.completed` with `status=FAILED`. Orchestrator marks `CLASSIFICATION_FAILED, lastErrorStage=LLM`. Retryable. |
| Timeout (300 s default) | Same as 5xx, with `lastError = "LLM_TIMEOUT"`. |
| MCP server unreachable, `save_classification` cannot run | LLM writes result to a **classification outbox** collection (`classification_outbox`); a small reconciler in the orchestrator drains the outbox once MCP is back. Prevents the "classified but never persisted" footgun. |
| LLM returns empty / malformed JSON | Existing retry-with-stricter-prompt logic. After 2 retries → `CLASSIFICATION_FAILED` with `lastError = "LLM_NO_RESULT"`. |
| Job abandoned (orchestrator died before resume) | `PipelineResumeConsumer` in any orchestrator replica handles `pipeline.llm.completed` events. `correlationId = nodeRunId` makes resume idempotent — Mongo `nodeRun` state machine rejects duplicate transitions. |

### 6.6 MCP server failure

The MCP server is on the LLM/SLM critical path. Failure modes:

| Failure | Behaviour |
|---|---|
| MCP unreachable on tool fetch (`get_correction_history`, etc.) | SLM/LLM proceeds without the tool's data, marks result with `mcp.unavailable=true`, confidence capped (forces escalation or review). |
| MCP unreachable on `save_classification` | Worker writes to `classification_outbox` (see 6.5). Reconciler in scheduler drains outbox. |
| MCP returns stale data (cache invalidation missed) | Out of scope here; covered by the existing `/cache/invalidate` mechanism plus a TTL on Caffeine. |

### 6.7 Orchestrator failure mid-pipeline

| Failure | Behaviour |
|---|---|
| Orchestrator crashes after dispatching LLM, before resume | LLM completion is on Rabbit. Any orchestrator replica picks it up; `nodeRun` idempotency guards against double-resume. |
| Orchestrator crashes mid-Phase-1 (synchronous REST cascade) | `PipelineRun` and `NodeRun` records are persisted before each REST call. `StaleDocumentRecoveryTask` in the scheduler resets stuck runs after 15 minutes and re-queues from the last completed node. |
| Two replicas process the same `document.ingested` message | RabbitMQ at-most-once-per-consumer + Mongo `_id` uniqueness on `pipelineRun` (compound key `documentId + pipelineDefinitionVersion + dispatchSeq`). The second consumer no-ops. |

### 6.8 RabbitMQ unavailable

| Scenario | Behaviour |
|---|---|
| Rabbit down at upload | API returns 503. Connectors (Drive/Gmail) buffer to a Mongo `pending_ingest` collection and replay when Rabbit recovers. |
| Rabbit down mid-pipeline | Synchronous REST cascade still works (extraction → router → router-tier → enforcement). Async LLM dispatch fails fast → orchestrator marks `CLASSIFICATION_FAILED` retryable. Indexing fan-out skipped → indexing worker catches up via reconciliation job. |

### 6.9 Enforcement worker failure

Existing semantics preserved: `ENFORCEMENT_FAILED`, retryable from monitoring page. Now consumed via REST when called by the orchestrator (Phase 2 of the engine), or via Rabbit in legacy mode.

### 6.10 Indexing worker failure

| Failure | Behaviour |
|---|---|
| Elasticsearch down | `INDEX_FAILED` status, retried with exponential backoff. Pipeline run is **not** failed — indexing is async and downstream of the canonical record in Mongo. |
| Mapping conflict | Document quarantined in `index_failures` collection, surfaced in monitoring; needs admin intervention (mapping update or template fix). |

### 6.11 Cross-cutting principles

Every container must:

- Persist state **before** the outbound call, not after.
- Use `correlationId = nodeRunId` end-to-end so retries and resumes are idempotent.
- Emit an audit event for every status transition, including failures.
- Expose `/actuator/health` distinguishing **liveness** (process alive) from **readiness** (downstream deps reachable + warm-up complete).
- Surface `lastError`, `lastErrorStage`, `failedAt`, and `retryCount` on the document.

---

## 7. Audit and event capture

In the monolith, "audit" was a single rule in `CLAUDE.md`: *every status transition writes an audit event in the same transaction as the state change.* With ten or more containers writing into the pipeline, that contract no longer holds — there is no shared transaction, every container becomes a producer, and event volume scales with the number of stages a document traverses (~10–30 events end-to-end). For a governance product, audit is also load-bearing for compliance — not just observability — so the architecture must be explicit, not emergent.

### 7.1 Two audiences — keep them separated

Two distinct things are commonly called "audit," and they have opposite requirements. Conflating them in one collection optimises for neither.

| Audience | Need | Retention | Mutability | Volume |
|---|---|---|---|---|
| **Compliance / legal** | "Who did what to which record, when?" Regulatory record. Discovery, legal hold, exports. | 7+ years (sector / GDPR rules) | **Immutable, tamper-evident** | Lower (business-meaningful events only) |
| **Operations / debugging** | "What did the system do for this run, why did it fail?" | 30–90 days hot, archive cold | Append-only; cryptographic guarantees not required | Very high (every status transition, retry, MCP call) |

Compliance reads against an operational-volume store will be slow and expensive; operational queries against a compliance store will pay for retention they don't need. Two stores, two retention regimes.

### 7.2 Three-tier model

**Tier 1 — Domain audit events** (compliance-grade)

Schema'd, business-meaningful, append-only, hash-chained for tamper-evidence (each event carries the SHA-256 of the previous event for that resource). Event-type cardinality stays small (~30) and is curated:

- `DOCUMENT_INGESTED`, `DOCUMENT_CLASSIFIED`, `DOCUMENT_OVERRIDDEN`, `DOCUMENT_DELETED`, `DOCUMENT_EXPORTED`, `DOCUMENT_RETAINED`
- `GOVERNANCE_APPLIED`, `LEGAL_HOLD_PLACED`, `LEGAL_HOLD_RELEASED`
- `BLOCK_PUBLISHED`, `BLOCK_ROLLED_BACK`, `POLICY_CHANGED`
- `USER_ROLE_GRANTED`, `USER_ROLE_REVOKED`, `SUBSCRIPTION_CHANGED`
- `RECLASSIFICATION_INITIATED`, `RECLASSIFICATION_COMPLETED`

Stored separately from operational data. Two viable backends: (a) a dedicated MongoDB collection with role-based deny on update/delete plus periodic hash-chain validation, or (b) an external WORM store (S3 Object Lock + Athena, or a managed audit service). For a governance product, infrastructure-enforced immutability is the stronger story than "trust us, the collection is read-only."

**Tier 2 — System audit events** (operational)

Every status transition, every tier escalation, every retry, every error, every config refresh, every block-version fetch, every MCP tool call, every vendor API call. Higher event-type cardinality, much higher volume. Hot in OpenSearch / Elasticsearch with 90-day TTL; ILM-rolled to S3 + Athena for the long tail. Queryable by `documentId`, `pipelineRunId`, `nodeRunId`, `actor.id`, `traceparent`.

**Tier 3 — Distributed traces** (debugging — adjacent, not audit)

OpenTelemetry spans across the pipeline. W3C `traceparent` propagation is already mandated in §11.B.2. Sampled at 100% for failures, 1–5% for successes. 7-day retention. Backed by Tempo / Jaeger / Honeycomb. Not audit, but the same `traceId` lands on every Tier 1 and Tier 2 event so cross-tier navigation works.

### 7.3 Event flow — outbox + bus + collector

The naïve shape is "every container writes directly to the audit store." That fails three ways: every container becomes tightly coupled to the store, events are lost on container crash mid-write, and ordering for the hash chain cannot be preserved across parallel writers.

The right shape is the **transactional outbox** at every producer plus a **single collector per tier**:

```
[any container]   (orchestrator, router, BERT, SLM, LLM, extraction, …)
   │
   │  state change  +  audit event   ──── one Mongo transaction
   ▼
 ┌─────────────────────┐
 │  service DB         │
 │  + audit_outbox     │   relay drains outbox, publishes to Rabbit,
 └──────────┬──────────┘   marks sent (at-least-once with idempotent
            │              consumer downstream)
            ▼
 ╔════════════════════════════════════════════════╗
 ║         RabbitMQ — igc.audit  exchange         ║
 ║                                                ║
 ║   audit.tier1.domain  ──▶  [igc-audit-collector] ──▶  Tier 1 WORM store
 ║                              (Class D singleton,        append-only,
 ║                               ShedLock, chain integrity) hash-chained,
 ║                                                          7+ years
 ║
 ║   audit.tier2.system  ──▶  [igc-audit-collector] ──▶  OpenSearch
 ║                              (Class B horizontal,       (90-day hot)
 ║                               competing consumers)      + S3 cold
 ║
 ║   audit.tier3.trace   ──▶  [otel-collector]      ──▶  Tempo / Jaeger
 ╚════════════════════════════════════════════════╝
```

Two patterns earn their complexity here:

- **Outbox pattern at each producer.** The state change and the audit event share one Mongo transaction (same document, or a `_pendingEvents` array on the document). A relay drains the outbox and publishes to Rabbit; on crash, replay is safe because the outbox state survives. This is the only way to guarantee both "no state change without audit event" (the outbox is committed with state) and "no audit event without state change" (because the outbox row never appears unless the state change committed). 2PC is not on the table.

- **Single writer per Tier 1.** `igc-audit-collector` is the only writer to the Tier 1 store. Parallel writers cannot maintain a coherent hash chain. Two replicas with leader election (Class D — singleton, ShedLock); the non-leader stays warm. Tier 2 has no chain, so the same collector binary scales horizontally (Class B) for that tier — two roles in one container.

### 7.4 Common envelope

Every audit event from every container shares one envelope. Without this, cross-container correlation by document, run, or trace becomes impossible. The envelope is itself a versioned contract: `contracts/audit/asyncapi.yaml` for the Rabbit topology and `contracts/audit/event-envelope.schema.json` for the JSON Schema (per the OpenAPI 3.1.1 mandate in `CLAUDE.md`).

```json
{
  "eventId":         "01HMQX...",            // ULID, sortable by time
  "eventType":       "DOCUMENT_CLASSIFIED",
  "tier":            "DOMAIN",                // DOMAIN | SYSTEM
  "schemaVersion":   "1.0.0",
  "timestamp":       "2026-04-26T14:23:45.123Z",
  "documentId":      "doc_a3f2b1",
  "pipelineRunId":   "run_8c12...",
  "nodeRunId":       "node_4d72...",
  "traceparent":     "00-4bf92f3577b34da6...-01",
  "actor": {
    "type":     "SYSTEM",                     // SYSTEM | USER | CONNECTOR
    "service":  "igc-classifier-router",
    "version":  "1.4.2",
    "instance": "pod-abc123"
  },
  "resource": {
    "type":    "DOCUMENT",
    "id":      "doc_a3f2b1",
    "version": 5
  },
  "action":          "CLASSIFY",
  "outcome":         "SUCCESS",                // SUCCESS | FAILURE | PARTIAL
  "details": {
    "tier":       "BERT",
    "confidence": 0.94,
    "category":   "HR > Maternity",
    "modelUsed":  "uk-hr-bert-v2"
  },
  "retentionClass":  "7Y",                     // 7Y | 90D | 30D
  "previousEventHash": "sha256:..."            // Tier 1 only — chain by resource
}
```

A user override carries `actor.type=USER`, `actor.id=user_xyz`, `action=OVERRIDE_CATEGORY`, with original and corrected values in `details`. Same envelope, different payload.

### 7.5 New container — `igc-audit-collector`

A new entry on the §3 roster, dual-role:

| Property | Tier 1 mode | Tier 2 mode |
|---|---|---|
| **Module** | NEW (`igc-audit`) | same binary |
| **Listens on** | AMQP `audit.tier1.domain` | AMQP `audit.tier2.system` |
| **Writes to** | Tier 1 store (WORM / S3 Object Lock / Mongo append-only) | Tier 2 store (OpenSearch + S3 cold) |
| **Scaling class** | D — singleton with leader election (chain integrity) | B — competing consumers, horizontal |
| **Min replicas** | 2 (HA, 1 active) | 2 |
| **HPA signal** | n/a | `audit.tier2.system` queue depth |
| **Failure mode** | Lock expiry → standby promotes; messages return to queue | Stuck consumer's prefetch returns; another replica picks up |

Existing containers each gain a small audit-relay component. JVM services share a `igc-platform-audit` library (single source of truth for the envelope, outbox writer, retry/backoff). Python services (`igc-bert-trainer`, OCR if Python) consume an equivalent module published the same way.

### 7.6 What gets audited at which tier

A worked decision for the most-common pipeline events:

| Event | Tier 1 (Domain) | Tier 2 (System) |
|---|---|---|
| Document uploaded by user | `DOCUMENT_INGESTED` | `STATUS_TRANSITION uploaded → processing` |
| Drive connector picks up new file | `DOCUMENT_INGESTED` (`actor.type=CONNECTOR`) | per-poll source events |
| Extraction succeeds | — | `EXTRACTION_COMPLETED` (latency, mime, extractor) |
| Extraction fails (OOM) | — | `EXTRACTION_FAILED` (`lastError`, `retryable`) |
| BERT classifies, accepted | — | `TIER_DECISION` (BERT, ACCEPT, confidence) |
| Cascade escalates BERT → SLM → LLM | — | three `TIER_DECISION` events |
| Final classification persisted | `DOCUMENT_CLASSIFIED` | `STATUS_TRANSITION` |
| User overrides category | `DOCUMENT_OVERRIDDEN` | (correction stored separately as well) |
| Governance enforcement applies | `GOVERNANCE_APPLIED` | `STATUS_TRANSITION` |
| Document retained / deleted by retention rule | `DOCUMENT_RETAINED` / `DOCUMENT_DELETED` | n/a |
| Block published | `BLOCK_PUBLISHED` | `BLOCK_VERSION_FETCHED` per use |
| MCP tool call (`get_correction_history` etc.) | — | `MCP_TOOL_CALLED` (tool, params, latency) |
| LLM Anthropic 5xx / circuit breaker open | — | `VENDOR_CALL_FAILED` |
| Reclassification job kicked off | `RECLASSIFICATION_INITIATED` | per-document `STATUS_TRANSITION` |
| Reclassification supersedes prior decision | `DOCUMENT_CLASSIFIED` (with `supersedes` link) | — |

The principle: **Tier 1 records the outcome the document ended up with; Tier 2 records how it got there.** A reclassification produces a new Tier 1 event that cryptographically supersedes the previous one (`supersedes` field on the envelope's `details`), so the document's compliance history is a chain of decisions rather than a single mutable value.

### 7.7 Cross-cutting principles

Every container that writes audit events must:

- **Persist before publish** — audit row in `audit_outbox` is committed in the *same* transaction as the state change. The relay publishes after commit. Crash before publish → replay; crash after publish → idempotent consumer downstream.
- **Use `nodeRunId` / `pipelineRunId` as correlation keys** — same as the rest of the pipeline (§6.11). Audit is a parallel timeline anchored to the same IDs; the auditor's "show me everything for this document" query joins on `documentId`.
- **Treat the envelope as a versioned contract** — bump `schemaVersion` on breaking change, never reshape silently. Old events stay readable for the full retention window.
- **Distinguish metadata from content** — `details` may carry document content (text snippets, classification rationale, PII findings). Schema marks each field's class so right-to-erasure can scrub *content* fields without breaking the chain. Tier 1 events therefore store **hashes** of content, never the content itself.
- **Honest health probes again** — the audit relay is part of readiness. A pod whose `audit_outbox` cannot publish is a pod that risks dropping audit events; readiness must reflect that, not just the service's nominal HTTP health.

### 7.8 Open decisions

These will bite if not settled before `igc-audit-collector` is built. Mirrored in §10.

1. **Tier 1 backend.** External WORM (S3 Object Lock + Athena, or managed audit service) vs in-Mongo with role-based deny. Recommendation: external — for a governance product, infrastructure-enforced immutability is a stronger claim than process-enforced. Trade-off is an extra dependency and a separate query surface.

2. **Hash chain granularity.** Per-resource chain (per-document, per-block, per-user) vs single global chain. Recommendation: per-resource. A global chain serialises every audit write through one writer for a guarantee auditors don't actually ask for. Per-resource gives the answer to "is this document's history intact and complete," which is the real question.

3. **Failed classification — Tier 1 or Tier 2?** Compliance may want "we attempted to classify and failed permanently" recorded; operations definitely wants every retry. Recommendation: both, with different event types. `CLASSIFICATION_FAILED` (Tier 1) records the outcome the document ended up with after retries exhausted. `CLASSIFICATION_ATTEMPT_FAILED` (Tier 2) records each attempt.

4. **Content in audit events.** `details` may contain document text snippets, classification rationale, PII findings. This audit data is itself subject to retention rules, redaction on export, and right-to-erasure. The schema must distinguish *metadata* fields (always retained) from *content* fields (subject to erasure, replaced with hashes on deletion request). Tier 1 events therefore cannot embed raw text — only hashed references plus the metadata required for compliance reasoning.

5. **Reclassification & supersession.** When a block-version improvement triggers reclassification of historical documents, fresh Tier 1 events fire. Auditor question: "what was this document's classification on date X?" Schema needs `supersedes` and `supersededBy` links between events; queries reconstruct the as-of-date view by walking the chain. This is where per-resource chaining (decision 2) earns its keep.

6. **MCP tool calls as audit.** Every `get_correction_history` exposes correction context to a model. Compliance question: "what training/context data did the model see for this decision?" Recommendation: yes, audit at Tier 2 — `MCP_TOOL_CALLED` event with `tool`, `paramsHash`, `responseHash`, `latencyMs`. Lets us answer "show me every model decision that saw user X's correction history."

---

## 8. Decisions

### 8.1 Where correction-context fetching lives — **DECIDED: each worker calls MCP itself**

Adopted: the SLM and LLM workers each call MCP for `get_correction_history`, `get_metadata_schemas`, `get_org_pii_patterns`, etc. The router does not pre-fetch and does not proxy.

Rationale:

- Workers are already MCP clients; this preserves the tool-use pattern Anthropic models expect.
- Router stays slim and stateless — no MCP schema coupling.
- Each worker can choose which tools it needs (Haiku may use fewer than Sonnet).
- Caching is moved closer to where the model actually consumes the data; Caffeine in MCP plus per-worker request-scoped caches cover the duplicate-fetch concern.

Trade-off: a small amount of duplicate MCP load when both SLM and LLM run for the same document (escalation case). MCP's existing cache absorbs this; the second call is essentially free.

### 8.2 BERT serving location — **DECIDED: hybrid (train in Python, serve in JVM)**

Adopted: a two-container split.

- **`igc-bert-trainer`** — Python, FastAPI optional, HuggingFace `transformers` + PyTorch. Triggered by `BertTrainingJobRepository` jobs. Reads training samples from Mongo, fine-tunes, **exports to ONNX**, publishes the artifact to MinIO under a versioned key (e.g. `bert-models/uk-hr-bert/v2.onnx`), then writes a new `BERT_CLASSIFIER` block version pointing at it.
- **`igc-bert-inference`** — JVM (Spring Boot) running ONNX Runtime via [DJL](https://djl.ai/). Loads ONNX artifact from MinIO at startup or on `/v1/models/reload`. No Python in the request path.

Rationale:

- **Training side wins from Python** — HuggingFace, easy distillation, well-trodden ONNX export path.
- **Serving side wins from JVM** — homogeneous deploy, same observability stack, same Spring config conventions, lower memory overhead than Python+CUDA in production, simpler IAM/MinIO integration via existing libs.
- ONNX is the contract between the two — portable, well-supported, fast.
- Avoids the failure mode where a Python serving container drifts on dependency versions away from the rest of the Java fleet.

Trade-off: ONNX export quirks for some custom layers — mitigated by sticking to standard transformer architectures (BERT, ModernBERT, DistilBERT) for which export is well-trodden.

---

## 9. Recommended next steps

Ordered by dependency and value.

### Phase A — Carve out the obvious wins (low risk)

1. **Extract `igc-extraction-worker`** as its own Maven app + Docker image.
   *Biggest blast-radius reduction*: a 200 MB PDF can no longer OOM the orchestrator. Smallest schema/contract surface.
2. **Stand up `igc-indexing-worker`** (greenfield) consuming `igc.documents.classified` and writing to Elasticsearch. Independent SLO from classification.

### Phase B — Introduce the router and the multi-tier shape

3. **Define the OpenAPI spec for `igc-classifier-router`** (the contracts in §4). Mock implementation first that just proxies straight to the existing LLM worker — proves the orchestrator integration end-to-end before any model work.
4. **Define the `ROUTER` block content schema** (§2 example) with admin UI + validation. Seed with conservative defaults (`bertAccept=1.01` everywhere — disabled).
5. **Cutover orchestrator** to call `router /v1/classify` instead of dispatching directly to LLM. LLM-only path under the hood; no behaviour change for users.

### Phase C — Add BERT

6. **Build `igc-bert-trainer`** (Python). First job: train the existing top-3 categories. Publish ONNX to MinIO + write `BERT_CLASSIFIER` block.
7. **Build `igc-bert-inference`** (JVM/DJL). Wire `/v1/models/reload` to the block-version observer.
8. **Enable BERT for the categories with most corrections**, one at a time, by raising `bertAccept` from 1.01 to 0.92. Watch escalation rate and accuracy.

### Phase D — Add SLM tier

9. **Build `igc-slm-worker`** with two backends: Anthropic Haiku and Ollama. Same OpenAPI as LLM worker. Make MCP integration mandatory.
10. **Tune `slmAcceptThreshold`** per category against held-out evaluation set.

### Phase E — Operational hardening

11. **Per-tier observability**: tier-of-decision histogram, escalation rate, cost-per-document, p50/p95/p99 latency by tier, MCP availability impact on confidence.
12. **Cost guardrails**: spend cap on LLM tier (org-wide, optionally per business unit / department); auto-degrade to SLM-only when exceeded.
13. **`classification_outbox` reconciler** for the MCP-down case (§6.5).
14. **Singleton scheduling** for `igc-scheduler` and `igc-connectors` (ShedLock on Mongo).
15. **Change-driven cache refresh** (Decision 30): `igc.config.changed` fanout exchange. Every write to a governance entity (taxonomy, blocks, schemas, retention rules, PII / sensitivity / storage / trait definitions, governance policies, AppConfig) fires an event with the changed entity IDs; every replica subscribes and invalidates its in-memory cache granularly. Hub `PackImportService` MUST fire the same event after a pack import so hub-driven updates propagate identically to local writes. Replaces the existing MCP Caffeine TTL cache for governance reference data.
16. **Readiness vs liveness** split in every container's actuator config.

### Phase F — Enforcement and connector splits (deferred)

17. Carve out `igc-enforcement-worker`. Lower urgency — current load is modest.
18. Carve out `igc-connectors`. Worth doing once mailbox/folder count grows past current scale.

---

## 10. Open questions for the next session

1. **Block-version pinning vs latest** — should a `pipelineRun` pin every block version at dispatch time (full reproducibility, but stale runs drift from current best practice), or always use `activeVersion`? Recommendation: pin for replays, use active for fresh runs.
2. **Escalation cost ceiling** — at what point does the router refuse to escalate to LLM and instead route to human review? Org-wide cap, per top-level category, or per business unit?
3. **Confidence calibration** — BERT, SLM, and LLM produce confidence scores on different scales. Need a calibration layer or per-tier thresholds (currently the latter).
4. **BERT model specialisation** — one general model across all categories, or domain-specialised models (HR-bert, Legal-bert, Finance-bert) loaded by `igc-bert-inference` and selected per document? Affects training cost, cold-start time, and warm-pool sizing.
5. **GPU sizing for `igc-bert-inference`** — CPU is sufficient for ModernBERT-base at expected QPS, but if we move to larger encoders or batch real-time inference, GPU pool becomes a separate concern.

**Audit and event capture** (mirrored from §7.8 — same wording is canonical there)

6. **Tier 1 audit backend** — external WORM (S3 Object Lock + Athena, or managed audit service) vs in-Mongo with role-based deny? Recommendation: external — infrastructure-enforced immutability is a stronger compliance claim than process-enforced. Trade-off is an extra dependency and a separate query surface.
7. **Audit hash-chain granularity** — per-resource chain (per-document, per-block, per-user) or single global chain? Recommendation: per-resource. Global serialises every audit write through one writer for a guarantee auditors don't actually ask for; per-resource answers the real question — "is this document's history intact and complete?"
8. **Where do failed classifications get audited** — Tier 1, Tier 2, or both? Recommendation: both, with different event types. `CLASSIFICATION_FAILED` (Tier 1) for the final outcome after retries exhausted; `CLASSIFICATION_ATTEMPT_FAILED` (Tier 2) per attempt.
9. **Content in audit `details`** — how does right-to-erasure square with append-only Tier 1? Recommendation: the envelope schema marks each `details` field as *metadata* (always retained) or *content* (subject to erasure). Tier 1 stores hashes of content, never raw text — right-to-erasure scrubs content fields without breaking the chain.
10. **Reclassification & supersession** — how do auditors answer "what was this document's classification on date X?" Recommendation: every Tier 1 reclassification event carries `supersedes` / `supersededBy` links; queries reconstruct as-of-date views by walking the chain. Per-resource chaining (Q7) is the enabler.
11. **MCP tool calls as audit** — are model-tool exposures auditable? Recommendation: yes, Tier 2 — `MCP_TOOL_CALLED` with `tool`, `paramsHash`, `responseHash`, `latencyMs`. Answers compliance question "what context data did the model see for this decision?"

---

## 11. Classifier-Router OpenAPI — design considerations before drafting

The OpenAPI spec for `igc-classifier-router` is small but it locks in shape decisions that propagate to every other worker (extraction, BERT, SLM, LLM, enforcement, indexing). The same envelope, error model, idempotency rules and trace propagation should be reused everywhere — otherwise the orchestrator becomes a translation layer.

The decisions below are split into:

- **A.** Shape-defining — change the *structure* of the contract; must settle before drafting.
- **B.** Conventions — decide once, reuse across every container.
- **C.** Non-functionals — bake into the spec, not added later.
- **D.** Deferrables — explicitly out-of-scope for v1.

### 11.A Shape-defining decisions (must settle before drafting)

#### A1. Sync-or-async response model

Three viable patterns for the `/v1/classify` response:

1. **Single endpoint, status-code switch** — 200 means BERT/SLM accepted, 202 means escalated to LLM with callback via Rabbit.
2. **`Prefer: respond-async` header** — caller declares what it can tolerate; useful when `igc-api` calls the router for ad-hoc reclassification (no Rabbit consumer to drain).
3. **Two endpoints** (`/classify/sync`, `/classify/async`) — cleanest spec; two SDK methods.

**Recommendation:** option 1 with optional `Prefer` header. Same surface for both call sites; orchestrator's HTTP client must accept both response shapes.

#### A2. Block-version pinning vs latest

Caller passes blocks as `{ id, version }` (pinned) or `{ id }` (router resolves to `activeVersion` from the `PipelineBlock.activeVersion` field).

- **Pinned** → reproducible runs (replays from a year ago use exactly the same prompt, BERT, router policy). Required for audit and for evaluating policy changes against historical traffic.
- **Latest** → orchestrator can't accidentally hold a stale `pipelineRun` open across a publish.

**Recommendation:** contract accepts both — `version` optional, defaults to active. PipelineDefinition decides whether to pin at dispatch time. This unblocks "dry-run a new ROUTER policy against last week's docs". Resolves open question 9.1.

#### A3. Tenancy — **DECIDED: not applicable**

IGC is single-tenant (one organisation per deployment). No `tenantId` in the contract, no tenant-aware routing in the router, no tenant claim in auth tokens. Internal scoping (department, business unit, content domain) is a separate concern handled inside the application's authorisation model — not at the inter-service contract surface.

If a future deployment ever needed multi-tenancy, it would be a major version bump on every contract; defer the design entirely until that requirement actually exists.

#### A4. Idempotency key

`nodeRunId` as correlation. Contract questions:

- Same `nodeRunId` submitted twice → return cached result? Reject as `409 IN_FLIGHT`? Re-run?
- TTL on the cache — minutes or hours?
- Does `StaleDocumentRecoveryTask` re-submit with the **same** `nodeRunId` (idempotent reset) or a new one (fresh attempt)?

**Recommendation:** within TTL, return existing result if completed or `409 IN_FLIGHT` if running. Orchestrator generates a new `nodeRunId` on explicit retry. TTL: 24 h (covers typical replay windows; cheap to store the result hash).

#### A5. Error envelope

RFC 7807 `application/problem+json`:

```json
{ "type": "https://igc.example/errors/slm-rate-limited",
  "title": "SLM tier rate-limited",
  "status": 503,
  "code": "SLM_RATE_LIMITED",
  "lastErrorStage": "SLM",
  "retryable": true,
  "retryAfterMs": 1000,
  "trace": [ { "tier": "BERT", "decision": "REJECT", "confidence": 0.41 },
             { "tier": "SLM",  "decision": "RATE_LIMITED" } ] }
```

**Recommendation:** RFC 7807 across **every** worker (extraction, BERT, SLM, LLM, enforcement, indexing), not just the router. Otherwise the orchestrator translates per-worker shapes — and it shouldn't. Add the IGC-specific fields (`code`, `lastErrorStage`, `retryable`, `retryAfterMs`, `trace`) as extensions.

#### A6. Auth between containers

Three options:

- **mTLS at the mesh** — k8s native (Istio/Linkerd); no app-layer auth.
- **Service-account JWTs** — intent-bearing ("which service is calling, with which scopes"); easier to debug; works without a mesh.
- **Shared API key** — cheap to start, painful to rotate at this many services.

The OpenAPI must declare a `securityScheme`, so this is unavoidable.

**Recommendation:** **service-account JWTs** as the default. mTLS later if/when we adopt a mesh. JWT claims carry the calling service identity and granted scopes — sufficient for inter-service authorisation without an issuer per organisation.

### 11.B Conventions to bake in across every worker

#### B1. Text payload — inline vs reference

A 200 MB extracted-text body across the fleet is wasteful.

**Recommendation:** inline `text` if ≤ 256 KB, otherwise `textRef` with a presigned MinIO URL. Worker fetches lazily. Spec defines both fields, exactly one required.

#### B2. Trace context propagation

W3C `traceparent` header on every inter-container call. Mandate it in the spec — cheap now, painful to retrofit.

#### B3. Capability discovery

`GET /v1/capabilities` exposes which tiers are healthy and which model versions are loaded. Used by:

- Orchestrator pre-flight (skip BERT call if router reports it down).
- Admin UI showing the live tier topology.
- CI smoke tests.

#### B4. Cost attribution

Response carries `costUnits` plus `tokensIn` / `tokensOut`. **Recommendation:** abstract `costUnits` (decoupled from currency) — pricing changes don't break the contract. Per-tier-attempt and rolled up.

### 11.C Non-functionals

#### C1. Backpressure

Router under load → `429` with `Retry-After`. Orchestrator re-queues on Rabbit with delay; **does not** spin-retry over HTTP. Spec must document `Retry-After` semantics explicitly.

#### C2. Timeouts

Sync path: orchestrator's HTTP client has a hard ceiling of **12 s** (covers BERT + SLM cascade comfortably). Beyond that the router should already have returned 202 and switched to async.

#### C3. API versioning

Path-based: `/v1/classify`. When tiers expand (e.g. distillation tier between BERT and SLM), the response grows additively — `tier: "DISTILLED"` is a new enum value, but old clients still parse the rest. Use `x-extensible-enum` or document forward-compat. Avoid `oneOf` discriminators that break on new variants.

#### C4. `extractedMetadata` schema

Two options:

- `Map<String, Any>` — free-form, validated against `MetadataSchema` separately.
- Typed via the schema — but then the OpenAPI must be regenerated when schemas change.

**Recommendation:** free-form in OpenAPI; request optionally references `metadataSchemaId`; worker validates against it. Keeps the API surface stable while metadata schemas evolve in Mongo.

### 11.D Explicitly deferred (not in v1)

- **Streaming partial results (SSE)** for tier-by-tier progress. Useful only if humans are waiting; orchestrator doesn't need it.
- **`dryRun: true`** mode for evaluating policy changes against historical traffic. Worth adding when we want to A/B-test ROUTER policies.
- **Bulk classification** endpoint. Premature; one-doc-per-request is enough until a batch use case materialises.

### 11.E Pickup checklist for the next session

Four concrete answers unlock the rest. Once these are agreed, a single drafting pass produces an OpenAPI spec that won't need rework when we extend it:

| # | Decision | Recommendation |
|---|---|---|
| A1 | Sync/async response model | Single endpoint with optional `Prefer: respond-async` header |
| A2 | Block version pinning | Accept both; `version` optional, defaults to active |
| A5 | Error envelope | RFC 7807 across **every** worker (not just router) |
| A6 | Inter-container auth | Service-account JWTs (service identity + scopes as claims) |

A3 (tenancy) is decided — single-tenant, not in the contract. A4 (idempotency TTL) and the 11.B conventions can take their recommended defaults unless the morning conversation surfaces a reason to change them.

**Then** — with those five answered — draft `igc-classifier-router/openapi.yaml` covering:

- `POST /v1/classify` (sync + async)
- `GET /v1/capabilities`
- `GET /actuator/health` (liveness + readiness split per §6.11)
- The shared error schema
- The shared tenancy / auth scheme

Once the router spec is committed, the same shape gets cloned (with endpoint-specific request/response schemas) for `igc-extraction-worker`, `igc-bert-inference`, `igc-slm-worker`, `igc-llm-worker`, `igc-enforcement-worker`, `igc-indexing-worker`.

---

## Glossary

Acronyms and short-form terms used in this document. Definitions favour *how the term is used here* over the strict literal expansion.

| Term | Stands for | Meaning in this doc |
|---|---|---|
| **ACK** | Acknowledgement | RabbitMQ confirmation that a consumer has successfully processed a message; until ACK, the broker will redeliver. |
| **AMQP** | Advanced Message Queuing Protocol | The wire protocol RabbitMQ speaks. "AMQP `igc.documents.ingested`" = a queue on the RabbitMQ cluster. |
| **API** | Application Programming Interface | A service's public surface — REST/HTTP here unless stated otherwise. |
| **AZ** | Availability Zone | A failure domain within a cloud region; multi-AZ = the cluster survives one zone failing. |
| **BERT** | Bidirectional Encoder Representations from Transformers | The encoder-only model family used for fast, cheap, first-pass classification. ~500 MB ONNX artefact, 20–80 ms inference. |
| **BOM** | Bill of Materials | A Maven POM that pins a coherent set of dependency versions; `backend/bom/` is IGC's. |
| **CI** | Continuous Integration | The automated build/test pipeline (GitHub Actions in this repo). |
| **CPU** | Central Processing Unit | Used here to distinguish from GPU-bound workloads. |
| **DJL** | Deep Java Library | JVM-native ML inference framework; runs ONNX models without leaving the JVM. |
| **DLQ** | Dead-Letter Queue | A RabbitMQ queue that receives messages a primary queue rejected; the safety net for un-ACKable poison messages. |
| **DTO** | Data Transfer Object | A wire-shape Java/TypeScript type, typically generated from an OpenAPI schema. |
| **ES** | Elasticsearch | The search index; system of record stays in MongoDB. |
| **GPU** | Graphics Processing Unit | Used for OCR, audio transcription, and BERT training; *not* required for BERT inference at expected QPS. |
| **HA** | High Availability | Multi-replica deployment such that any single replica failure doesn't take the service offline. |
| **HPA** | Horizontal Pod Autoscaler | The Kubernetes controller that adjusts replica count based on a metric (CPU, RPS, queue depth, …). |
| **HTTP** | HyperText Transfer Protocol | Synchronous transport between containers. REST endpoints are HTTP-based. |
| **IAM** | Identity and Access Management | The permission model for a cloud account or service mesh. |
| **JIT** | Just-In-Time | Runtime compilation (e.g. ONNX session warm-up, JVM hotspot) — first request after start is slower. |
| **JSON** | JavaScript Object Notation | Wire format for REST bodies and the canonical Mongo document shape. |
| **JVM** | Java Virtual Machine | The runtime for every Spring Boot container in this fleet (vs Python for `igc-bert-trainer` and `igc-extraction-ocr`). |
| **JWT** | JSON Web Token | A signed token carrying claims (e.g. service identity, scopes); proposed as the inter-container auth mechanism (§11.A.6). |
| **KEDA** | Kubernetes Event-Driven Autoscaler | An HPA extension that scales on external signals like RabbitMQ queue depth — the right autoscaler for Class B containers. |
| **LLM** | Large Language Model | The expensive, deep-reasoning tier (Claude Sonnet/Opus). 5–30 s, async via RabbitMQ. |
| **MCP** | Model Context Protocol | Anthropic's tool-calling protocol; `igc-mcp-server` exposes governance tools (`get_correction_history`, `get_metadata_schemas`, …) to SLM/LLM workers. |
| **mTLS** | mutual TLS | Both ends of a connection authenticate with certificates; alternative to JWT auth, typically delivered by a service mesh. |
| **OCR** | Optical Character Recognition | Extracting text from images / scanned PDFs. Owned by `igc-extraction-ocr`. |
| **ONNX** | Open Neural Network Exchange | Portable ML model format; the contract between Python `igc-bert-trainer` and JVM `igc-bert-inference`. |
| **OOM** | Out Of Memory | A process death from heap exhaustion; the §6.1 failure mode for huge PDFs hitting Tika. |
| **PII** | Personally Identifiable Information | Data covered by privacy regulation (NI numbers, names, addresses, …); detected by `igc-pii-scanner`. |
| **QPS** | Queries Per Second | Equivalent to RPS for read-heavy services. |
| **RAM** | Random-Access Memory | Where BERT models live once loaded; sizing constraint for `igc-bert-inference`. |
| **REST** | REpresentational State Transfer | HTTP-based synchronous API style; the inter-container contract within a pipeline node. |
| **RFC** | Request For Comments | IETF standards document. RFC 7807 = Problem Details for HTTP APIs (the error envelope, §11.A.5). |
| **RPS** | Requests Per Second | Throughput metric used as an HPA signal for Class A containers. |
| **SDK** | Software Development Kit | Generated client library (per service contract) that consumers import to call that service. |
| **SLM** | Small Language Model | The mid-tier (Haiku, Llama-7B, qwen-7B). 1–3 s, low cost, MCP-aware. |
| **SLO** | Service Level Objective | Target reliability metric (e.g. p95 classification latency); future operational concern (§9 Phase E). |
| **SSE** | Server-Sent Events | One-way HTTP streaming; `igc-mcp-server` exposes its tool surface over SSE. |
| **TLS** | Transport Layer Security | Encryption of network connections; precondition for mTLS. |
| **TTL** | Time To Live | An expiry on a cached value or a distributed lock (e.g. ShedLock leases, idempotency cache). |
| **YAML** | YAML Ain't Markup Language | The serialisation format for OpenAPI/AsyncAPI specs and Kubernetes manifests. |
