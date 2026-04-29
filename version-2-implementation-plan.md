---
title: V2 Implementation Plan
lifecycle: forward
---

# V2 Implementation Plan

**Companion to:** `version-2-architecture.md` (the *what*), `version-2-decision-tree.csv` (the *why*).
**This document:** the *how* and *when* — phased, with acceptance gates, dependencies, and concrete work items.

## How to read this plan

- **Phases run sequentially.** A phase does not begin until the prior phase's acceptance gate is met.
- **Cross-cutting tracks run in parallel** with phases. They never block phase progression but are referenced inside phase work.
- **Every work item is contract-first.** No implementation PR lands without an OpenAPI / AsyncAPI / JSON-Schema entry under `contracts/` first. (Per the API Contracts rule in `CLAUDE.md`.)
- **Every new decision goes in `version-2-decision-tree.csv`** in the same PR that introduces it.
- **Every work item gets a log entry in `version-2-implementation-log.md`** in the same PR. Append-only, chronological, sub-phase granularity. See *Progress tracking* below.
- **Acceptance gates are measurable.** "Done" means the gate passes, not that the code looks finished.
- **Rollback plan is mandatory** before each cutover within Phase 1. Every cutover PR includes a one-paragraph rollback story.

## Progress tracking

Append a dated entry to `version-2-implementation-log.md` after every work item in this plan, in the same PR. The log is the narrative companion to:

- the decision tree (the *why*),
- this plan (the *what's planned*),
- the architecture doc (the *how the system works*).

**Granularity:** per sub-phase (1.1, 1.2, etc.) at minimum. More frequent if the work spans multiple sessions.

**Entry shape:**

```
## YYYY-MM-DD — Phase X.Y — <Sub-phase title>

**Done:** <what was completed>
**Decisions logged:** <CSV row IDs>
**Contracts touched:** <contracts/ paths + VERSION bump notes>
**Files changed:** <other paths>
**Open issues:** <anything blocking, deferred, or unclear>
**Next:** <what comes next>
```

**Rules:** append-only — never edit past entries. If a decision reverses or a stage is redone, append a new entry that references the old one. The log records what actually happened, including detours and reversals — not the polished version.

The per-phase status board at the top of the log gets updated when a phase's status changes (Not started → In progress → Complete).

---

## Phase structure overview

```
Phase 0    Foundation                      (substrate, audit infra, contracts, tooling)
Phase 0.5  Reference implementation        (one service end-to-end, becomes the template)
Phase 1    Happy path services             (per-service: contract → code → audit → ship)
Phase 2    System-wide resilience          (DLQ, recovery, reconcilers, chaos)
Phase 3    Admin UI                        (full configuration experience)

Cross-cutting tracks (parallel):
   • Hub-side changes
   • Migration / cutover
   • Performance baseline & comparison
   • Minimum admin UI per service (Phases 1–2)
```

---

## Phase 0 — Foundation

**Goal:** every piece of substrate that lets new services ship safely is in place. No application logic for new services yet.

### Scope

#### 0.1 Decision-gate close-out
Before contract drafting begins, lock down the four §11 shape decisions still marked RECOMMENDED (CSV rows 13, 14, 17, 18). Hour-long sit-down per decision, update CSV in a single PR.

- [x] A1 (CSV #13) — sync/async response model → DECIDED
- [x] A2 (CSV #14) — block-version pinning → DECIDED
- [x] A5 (CSV #17) — RFC 7807 error envelope → DECIDED
- [x] A6 (CSV #18) — JWT auth → DECIDED

#### 0.2 K8s decision
Single decision: do new services deploy to K8s or stay on Docker Compose for v1? Affects what manifests get written. Log the decision in the CSV (`Topology` or `Scaling` category) before writing manifests.

#### 0.3 `contracts/` skeleton
- [x] Create directory tree: `contracts/_shared/`, `contracts/messaging/`, `contracts/audit/`, `contracts/blocks/`, `contracts/<service>/` placeholders for known services. (Per-service placeholders deferred until each service's phase begins — see 2026-04-26 log entry.)
- [x] Per-folder: `VERSION` (semver, start at `0.1.0`), `CHANGELOG.md`, `README.md` describing scope.

#### 0.4 `contracts/_shared/` content (hand-authored)
- [x] `error-envelope.yaml` — RFC 7807 with GLS extensions (`code`, `lastErrorStage`, `retryable`, `retryAfterMs`, `trace[]`).
- [x] `security-schemes.yaml` — JWT scheme definition (per A6 decision).
- [x] `common-headers.yaml` — `traceparent`, `Idempotency-Key`, `X-Request-Id`, `Prefer` semantics.
- [x] `capabilities.yaml` — `GET /v1/capabilities` response shape.
- [x] `text-payload.yaml` — inline-vs-`textRef` payload pattern (per CSV #19).
- [x] `pagination.yaml`, `idempotency.yaml`, `retry.yaml` — common conventions.

#### 0.5 OpenAPI 3.1.1 tooling
- [x] Pick lint tool (Spectral or Redocly) — log decision in CSV. Establish ruleset. (Spectral; CSV #40.)
- [x] Pick generator (`openapi-generator-maven-plugin` recommended for JVM, with TypeScript support for the frontend) — log decision. (CSV #39.)
- [x] Wire into root `pom.xml` as a build phase.
- [x] CI: contract diff check, version-bump enforcement, regenerate-on-PR, fail on drift.
- [x] Pre-commit hook: validate any spec edited under `contracts/` against its ruleset.

#### 0.6 AsyncAPI 3.0 setup
- [x] `contracts/messaging/asyncapi.yaml` — declare existing Rabbit topology as it stands today (`gls.documents.{ingested, classified}`, `gls.pipeline.llm.*`, `gls.audit.*`, `gls.config.changed`). (`gls.config.changed` declared as forward-looking in `info.description` only — full channel lands with 0.8.)
- [x] `contracts/audit/asyncapi.yaml` — Tier 1, Tier 2, Tier 3 audit channels.

#### 0.7 Audit infrastructure (foundation, not unhappy path)
- [x] `contracts/audit/event-envelope.schema.json` — JSON Schema 2020-12 for the common envelope (per §7.4).
- [x] `audit_outbox` MongoDB collection schema + indexes.
- [ ] **`gls-platform-audit` shared library** (JVM): envelope construction, outbox writer, relay-to-Rabbit, retry/backoff. Single dependency every service imports. (Envelope, outbox writer, schema validation, Spring Boot starter auto-config, and the outbox-to-Rabbit relay all landed; **leader election (ShedLock for Tier 1 single-writer) + comprehensive metrics + circuit breaker still outstanding**.)
- [ ] Equivalent Python module sketch (for `gls-bert-trainer` later) — design doc only at this phase.
- [x] Audit relay pattern documented in CLAUDE.md.

#### 0.8 `gls.config.changed` cache-invalidation infrastructure (per CSV #30)
- [x] AsyncAPI for the `gls.config.changed` channel.
- [x] `gls-platform-config` shared library: cache-with-invalidation primitive, event publisher, event subscriber.
- [x] Existing `AppConfigService` migrated to use it (replaces Caffeine TTL).
- [x] MCP server's Caffeine cache for governance entities replaced with the new pattern. (Substrate intentionally stays Caffeine — TTL replaced with change-driven; CSV #30 specifies the pattern, not the storage. Hub-side publishers tracked under Track A.)

#### 0.9 Maven BOM decoupling
- [x] Introduce per-deployable version properties in `backend/bom/pom.xml`: `gls.api.version`, `gls.orchestrator.version`, etc. All initially set to the current SNAPSHOT — values don't change yet, just the seam exists.
- [x] Document independent-version policy in CLAUDE.md.

#### 0.10 Schema migration tooling
- [x] Pick migration tool — Mongock recommended (MongoDB-native; equivalent to Liquibase/Flyway). Log decision. (CSV #41.)
- [x] Wire into `gls-app-assembly` startup.
- [x] Write a no-op migration as the smoke test. (V001_MongockSmoke.)
- [x] Document migration-on-startup policy.

#### 0.11 Performance + correctness baseline
- [ ] Baseline-capture script: p50/p95/p99 classification latency, throughput (docs/min), error rate, MCP cache hit rate, Mongo query patterns from current production traffic (or a replay against a representative sample). (`scripts/baselines/capture.sh` scaffold landed; **load driver still stub** — no representative workload yet.)
- [ ] Store baseline as a CSV in the repo (`baselines/2026-04-baseline.csv` or similar). (CSV scaffold landed with header + structural row; **no real captured data yet**.)
- [x] Document the measurement methodology so it's repeatable each phase. (`baselines/README.md`.)

#### 0.12 Local dev experience updates
- [x] Update `docker-compose.yml` with placeholder service definitions (commented out) for the new containers.
- [x] `make dev-up` / `scripts/dev-up.sh` — one-command bring-up for the new architecture. (`scripts/dev-up.sh`; `Makefile` deferred — no need yet.)
- [x] Updated `README.md` with the v2 dev instructions.

### Acceptance gate

- All four shape decisions (A1, A2, A5, A6) are DECIDED in the CSV.
- `contracts/` directory exists with `_shared/`, `messaging/`, `audit/` content; CI passes spec-diff and version-bump checks.
- `gls-platform-audit` and `gls-platform-config` libraries published to local Maven repo and consumed by `gls-app-assembly` without behavioural change.
- Existing `AppConfigService` runs on the new `gls.config.changed` cache pattern; integration test proves cache invalidation works end-to-end.
- Performance baseline captured and committed.
- Mongock migration runs on app startup with no errors.
- A "hello-world" OpenAPI spec for a placeholder service compiles to a stub via the generator.

### Risk callouts

- **Spectral rules being too strict** can stall PRs. Start with `recommended` and tighten over time.
- **Generated stubs locking in early conventions** — be willing to regenerate from a corrected spec rather than hand-patching.
- **Over-investing in `_shared/` upfront** — keep it minimal; expand as second and third services need it.

### Estimated size

8–12 PRs over 2–4 weeks depending on team size.

---

## Phase 0.5 — Reference implementation (`gls-extraction-tika`)

**Goal:** one service end-to-end with *every* concern future services will need. The pattern, not the product.

Why this service: it's the simplest member of the extraction family, has a small contract surface, and lets us validate the foundation against real bytes flowing through real Mongo and real Rabbit.

### Scope

#### 0.5.1 Module + contract
- [x] New Maven module `gls-extraction-tika` with its own `pom.xml`, version property, Dockerfile.
- [x] `contracts/extraction/openapi.yaml` — `POST /v1/extract`, `GET /v1/capabilities`, `GET /actuator/health`. References `_shared/` for error envelope, security, headers.
- [x] `VERSION` 0.1.0, `CHANGELOG.md` initial entry.

#### 0.5.2 Implementation
- [x] Generated server stub from contract.
- [x] Tika-based text extraction (port logic from existing `gls-document-processing`).
- [x] Returns text inline if ≤ 256 KB, else `textRef` to MinIO (per CSV #19).
- [x] Idempotency on `nodeRunId` with 24h TTL (per CSV #16). (`extraction_idempotency` Mongo collection with TTL index; `IdempotencyStore` wraps `tryAcquire` / `cacheResult` / `releaseOnFailure`. Cached row → 200 with replayed response; in-flight → 409 `IDEMPOTENCY_IN_FLIGHT`. Failures delete the row so retries can proceed without waiting for TTL.)

#### 0.5.3 Cross-cutting concerns (the template)
- [x] **Audit:** writes to `audit_outbox` for `EXTRACTION_COMPLETED` (Tier 2) and `EXTRACTION_FAILED` (Tier 2). No Tier 1 events for extraction. (Both success and failure paths emit. Failure emission lives in the controller — `try { … } catch (RuntimeException) { emitFailed(); throw; }` — so traceparent + nodeRunId stay in scope without a request-scoped bean. The handler still owns the RFC 7807 mapping.)
- [x] **Tracing:** `traceparent` propagation; spans for `tika.parse`, `minio.fetch`. (`traceparent` is propagated via Spring Boot's `ServerHttpObservationFilter` (auto-wired for free) + by the controller into audit envelopes. `@Observed(name="tika.parse")` and `@Observed(name="minio.fetch")` create nested spans wired through `micrometer-tracing-bridge-otel`. The OTLP exporter to a real collector is deployment-side — `opentelemetry-exporter-otlp` is added per-environment, not in the build.)
- [x] **Health probes:** liveness (process alive), readiness (MinIO reachable, Tika initialised). (`TikaHealthIndicator` parses a tiny synthetic input on every check; `MinioHealthIndicator` calls `listBuckets()` and reports the count. Spring Boot 4 relocates the `Health` / `HealthIndicator` types — see `docs/service-template.md`.)
- [x] **Error returns:** RFC 7807 with `EXTRACTION_OOM`, `EXTRACTION_CORRUPT`, `EXTRACTION_TIMEOUT` codes. (`ExtractionExceptionHandler` maps `DocumentNotFoundException` → 404 `DOCUMENT_NOT_FOUND`, `DocumentEtagMismatchException` → 409 `DOCUMENT_ETAG_MISMATCH`, `UnparseableDocumentException` → 422 `EXTRACTION_CORRUPT`, `DocumentTooLargeException` → 413 `EXTRACTION_TOO_LARGE`, `UncheckedIOException` → 503 `EXTRACTION_SOURCE_UNAVAILABLE`. `EXTRACTION_OOM` / `EXTRACTION_TIMEOUT` not yet thrown — they apply once async parsing / circuit breakers land.)
- [x] **Metrics:** Prometheus counters, latency histogram, error rate. (`ExtractMetrics` records `gls_extraction_duration_seconds` (Timer) and `gls_extraction_result_total` (Counter) tagged by `outcome` / `source` / `mime_family` / `error_code` from a closed taxonomy. Idempotency short-circuits report `outcome=cached` / `outcome=in_flight`. `gls_extraction_bytes_processed` summary captures the per-extract byte count. Surfaced via `/actuator/prometheus`.)
- [ ] **JWT validation** middleware (per A6 decision).

#### 0.5.4 Tests
- [ ] Contract test: spec lints, generated stub compiles.
- [ ] Unit tests: Tika parsing for office docs, native PDFs, text, HTML, .eml.
- [ ] Integration test: `docker-compose up gls-extraction-tika minio rabbitmq mongo` → POST a real document → assert response shape + audit event in Tier 2 store.
- [ ] Failure-mode tests: 200MB PDF → 413; corrupt file → 422; MinIO down → 503.
- [ ] Smoke test: deploys, takes traffic, returns 200s under nominal load.

#### 0.5.5 Deployment
- [x] Dockerfile, Docker Compose service definition, K8s manifest (Deployment, Service, HPA shell — values empty, just the structure). (Multi-stage Dockerfile + Docker Compose placeholder block landed; **K8s manifests still outstanding** — defer until 0.2's deployment-target reasoning genuinely calls for K8s.)
- [ ] CI/CD: builds image on PR, pushes to `ghcr.io/.../gls-extraction-tika:${VERSION}` on tag.

#### 0.5.6 Documentation: the cloneable pattern
- [x] `gls-extraction-tika/README.md` — points at the contract, describes the pattern.
- [x] `docs/service-template.md` — generic version of the above; explains how to clone for the next service. (Bring-up checklist, substrate use (`gls-platform-audit`, `gls-platform-config`, generator), conventions (RFC 7807, idempotency, capabilities), repo-root Docker build context, generator + Mockito gotchas accumulated across 0.5.1–0.5.5.)

### Acceptance gate

- Service deploys to local Compose and to K8s (if Phase 0 chose K8s).
- Posts return correct text for a battery of sample documents.
- Audit events visible in Tier 2 store with correct envelope shape.
- Distributed traces show full call from caller through Tika.
- All RFC 7807 error codes exercised in tests.
- `docs/service-template.md` exists and is referenced from CLAUDE.md as the canonical pattern.
- Performance: extraction latency within 20% of current `gls-document-processing` baseline.

### Risk callouts

- **The contract changes mid-implementation.** Expected. Bump VERSION and regenerate. The pattern *is* learning where the contract is wrong.
- **The pattern bakes in something wrong.** This is why this phase exists separately — fix the pattern here, not after five services have copied it.

### Estimated size

3–5 PRs over 1–2 weeks.

---

## Phase 1 — Happy path services

**Goal:** build out the v2 services. Each ships with happy path *and* per-service error handling. Cross-cutting recovery is Phase 2.

**Order matters** — sequential, dependency-driven. Don't fan out parallel new-service work.

### 1.1 Extraction family completion

Clone the Tika pattern.

- [ ] **`gls-extraction-archive`** — handles `.zip`, `.mbox`, `.pst`. On encountering archives, fans out child documents back to step ① ingest. Recursive ingest pattern documented. Decision: CSV #43 — caller owns fan-out (one-level walk, children returned inline; orchestrator publishes per-child ingest events).
  - [x] Module + contract (`contracts/extraction-archive/`, BOM property, reactor entry).
  - [x] Generated server stub + parser dispatch (ZIP, MBOX, PST via `java-libpst` per CSV #44) + MinIO source / sink.
  - [x] Idempotency (`archive_idempotency` Mongo TTL), audit (`EXTRACTION_*` Tier 2), health (parser dispatcher + MinIO), metrics, tracing (`archive.walk`, `minio.fetch`, `minio.put`).
  - [ ] JWT validation (blocked on JWKS infra, same as Tika).
  - [x] Tests — unit only (37 in module). Integration tests blocked on issue #7.
  - [x] Dockerfile + Compose entry.
  - [x] Per-service README.
- [x] **`gls-extraction-ocr`** — Tesseract via Tess4J (CSV #45). Contract + module + impl + Dockerfile (apt-installs `tesseract-ocr` + per-build language packs) + Compose entry. JWT and integration tests blocked (same as Tika / archive).
- [x] **`gls-extraction-audio`** — Whisper backend (CSV #46) with `Prefer: respond-async` semantics (CSV #47). Pluggable backend interface in this repo: `OpenAiWhisperService` (cloud) + `NotConfiguredAudioTranscriptionService` (default fallback). Sync + async paths share idempotency via the `audio_jobs` Mongo collection. JWT and integration tests blocked (same as Tika / archive / OCR).
- [x] Mime-detection logic — where does it live? Decision: at ingest in `gls-api` / `gls-connectors` using Tika's detector. Log in CSV. (CSV #42 — DECIDED.)

### 1.2 `gls-classifier-router` (mock implementation)

First iteration is a *proxy* — accepts the new contract, dispatches to the existing LLM worker. Proves orchestrator integration without any model work.

- [x] `contracts/classifier-router/openapi.yaml` per §11.A decisions.
- [x] Mock implementation (Phase 1.2 PR1) — `MockCascadeService` returns deterministic `tierOfDecision=MOCK`.
- [x] Real LLM-worker dispatch (Phase 1.2 PR2) — `LlmDispatchCascadeService` publishes to `gls.pipeline` exchange (routing key `pipeline.llm.requested`); per-replica auto-named queue bound to `pipeline.llm.completed` correlates by `jobId`. Selected via `gls.router.cascade.llm.enabled=true`. Default still mock so the router stays self-contained until the orchestrator cutover (1.3) lands.
- [x] Cascade policy block schema (`ROUTER` block content) — `contracts/blocks/router.schema.json` v0.2.0. Per-tier `enabled` + `accept`, fallback strategy (`LLM_FLOOR` / `ROUTER_SHORT_CIRCUIT`), per-category overrides, optional cost budget.
- [x] Admin migration — Mongock `V003_DefaultRouterBlock` seeds the `default-router` block with `bertAccept=1.01`, `slmAccept=1.01`, `llmAccept=0.0`, fallback `LLM_FLOOR`. Cascade is functionally disabled until per-category tuning lands in 1.4–1.6.

### 1.3 Orchestrator cutover (no behaviour change)

- [x] Replace direct LLM dispatch in `PipelineExecutionEngine` with a call to `gls-classifier-router`. `ClassifierRouterClient` (`@ConditionalOnProperty pipeline.classifier-router.enabled=true`) wraps a JDK `HttpClient` against `POST /v1/classify` and translates the router's `ClassifyResponse` back into the existing `LlmJobCompletedEvent` shape; engine calls `resumePipeline(event)` inline.
- [x] Same outcome from a user's perspective; under the hood, traffic now flows through the new path. The router internally dispatches via the existing LLM worker (`LlmDispatchCascadeService`), so the underlying model call is unchanged — only the orchestrator's transport changes.
- [x] **Rollback plan:** feature flag `pipeline.classifier-router.enabled` gates the new path; flip to `false` to revert. Default `false` — the legacy async-Rabbit path stays primary until explicitly flipped.
- [ ] Performance comparison against baseline: confirm latency is within 10% of current. **Blocked on Phase 0.11** load driver + first captured baseline (no representative-corpus data yet).

### 1.4 BERT trainer + inference

Per CSV #2 (DECIDED hybrid).

- [x] **`gls-bert-trainer`** (Python, k8s Job): reads training samples from Mongo, fine-tunes ModernBERT on the org's top-3 categories, exports ONNX, publishes to MinIO under versioned key. Phase 1.4 PR4 — sketch with real `pymongo` reader (top-N categories with min-samples gate), real Optimum + HF Transformers fine-tune + ONNX export, real `minio` publisher (versioned object key + metadata sidecar shaped to match the BERT_CLASSIFIER block schema's `trainingMetadata` field). Skipped (with a clear log) when fewer than `min_samples_per_class * top_n` samples exist. Tests cover the Mongo aggregation shape + the publisher's object-key construction; the actual training run is gated on a GPU + a populated samples collection.
- [x] **`gls-bert-inference`** (JVM, DJL + ONNX Runtime): loads ONNX from MinIO at startup, exposes `POST /v1/infer`, `POST /v1/models/reload`, `GET /v1/models`. Phase 1.4 PR1 — first cut ships the contract surface, JVM module, DJL classpath, audit / metrics / health / model-readiness wiring, and a stub `NotLoadedInferenceEngine` that returns `MODEL_NOT_LOADED` 503 until the trainer publishes an artefact. The real DJL + ONNX engine swaps in behind the `InferenceEngine` interface in a follow-up PR.
- [x] `BERT_CLASSIFIER` block type: links categories to model version + acceptance threshold. Phase 1.4 PR1 — `contracts/blocks/bert-classifier.schema.json` v0.3.0 carries `modelVersion`, optional `artifactRef` MinIO pointer, `labelMapping[]`, optional `trainingMetadata`, optional `minTextLength`. The bert-inference service consumes it once the trainer's first artefact is wired.
- [x] Wire `gls-bert-inference` into `gls-classifier-router` cascade behind the existing `ROUTER` block. Conservative thresholds (`bertAccept=0.92`). Phase 1.4 PR2 — `BertHttpDispatcher` (synchronous JDK `HttpClient` to `POST /v1/infer`) + `BertOrchestratorCascadeService` (wraps the existing inner cascade — LLM-direct or mock — and dispatches to BERT for `BERT_CLASSIFIER` blocks; falls through to inner on `MODEL_NOT_LOADED` / transport / 5xx). Activated by `gls.router.cascade.bert.enabled=true`; default off. ROUTER block threshold reading is deferred to the per-category tuning PR — for now BERT 200 responses are accepted as-is (the trainer hasn't published an artefact, so `MODEL_NOT_LOADED` is the only realistic response and the cascade always escalates).
- [ ] Enable BERT for the org's top-1 category first; observe escalation rate; widen.

### 1.5 SLM worker

- [x] **`gls-slm-worker`** with two backends: Anthropic Haiku (cloud) and Ollama (local) — selectable via SLM block configuration. Phase 1.5 PR1 (module + contract + stub backend); PR2 (`AnthropicHaikuSlmService` via Spring AI + `claude-haiku-4-5`); PR4 (`OllamaSlmService` via Spring AI + `llama3.1:8b`). Backend selected via `gls.slm.worker.backend=anthropic` / `=ollama`.
- [x] Same OpenAPI contract as LLM worker. Phase 1.5 PR1 — `contracts/slm-worker/openapi.yaml` v0.1.0 sets the shape the LLM worker rework (Phase 1.6) will conform to: `POST /v1/classify` with sync + `Prefer: respond-async`, `GET /v1/jobs/{nodeRunId}`, `GET /v1/backends`, `GET /v1/capabilities`, `GET /actuator/health`. Same `JobStore` shape as `gls-extraction-audio` and `gls-classifier-router`.
- [x] MCP integration mandatory (each worker calls MCP itself per CSV #1). Phase 1.5 PR5 — `spring-ai-starter-mcp-client` added; `SlmBackendConfig` injects all `ToolCallbackProvider` beans and passes them to both `AnthropicHaikuSlmService` and `OllamaSlmService` via a new constructor overload. The ChatClient builder calls `.defaultToolCallbacks(...)` only when there's at least one provider; missing MCP server → starter logs WARN, registers no tools, SLM call still works without them. `spring.ai.mcp.client.sse.connections.governance.url=${GLS_MCP_SERVER_URL:http://gls-mcp-server:8081}`.
- [x] Wire into cascade as the middle tier. Phase 1.5 PR3 — `SlmHttpDispatcher` + `SlmOrchestratorCascadeService` in `gls-classifier-router`. For `PROMPT` blocks: dispatches to `gls-slm-worker`; on `SLM_NOT_CONFIGURED` (or transport / 5xx) falls through to inner. Composes with the BERT tier — full cascade is BERT → SLM → inner (LLM/mock). Activated by `gls.router.cascade.slm.enabled=true`; default off.
- [ ] Tune `slmAcceptThreshold` per category against held-out evaluation set.

### 1.6 LLM worker rework

- [ ] Move existing `gls-llm-orchestration` logic into the new `gls-llm-worker` shape (sync entry point + async path retained).
- [ ] Conform to new contract (RFC 7807 errors, idempotency, traceparent).
- [ ] Cost budget gate: per-day spending cap, configurable.
- [ ] Rate-limit semaphore per replica.

### 1.7 Hub-component-to-taxonomy wiring (CSV #31–34)

- [ ] **PiiTypeDefinition** gains `applicableCategoryIds[]`. Migration: existing definitions → empty array (= global, current behaviour).
- [ ] **StorageTier** gains `applicableCategoryIds[]`.
- [ ] **TraitDefinition** gains `applicableCategoryIds[]`.
- [ ] **SensitivityDefinition** promoted to first-class entity. Migration: existing enum values seed entities with `applicableCategoryIds=[]`.
- [ ] Hub `PackImportService` updated to preserve `applicableCategoryIds[]` on import.
- [ ] Hub `PackImportService` fires `gls.config.changed` events for affected component types (per CSV #30).

### 1.8 `POLICY` block type + interpreter (CSV #35)

- [ ] `POLICY` added to `BlockType` enum.
- [ ] Block content schema: `requiredScans[]` (refs to `PiiTypeDefinition`s), `metadataSchemaIds[]`, `governancePolicyIds[]`, optional `conditions{}` for per-category overrides.
- [ ] In-engine interpreter (Option A from CSV #37): a node in the visual DAG that runs after classification.
- [ ] Per-category POLICY blocks seeded at install time from the imported governance pack.

### 1.9 Stage ④ scan dispatch (CSV #36)

- [ ] PII / PHI / PCI scan PROMPT blocks created per category needing them.
- [ ] Stage ④ node calls `gls-classifier-router` with the scan PROMPT block — same cascade, different content.
- [ ] Metadata extraction PROMPT blocks created per `MetadataSchema`.
- [ ] Results aggregated, passed to enforcement.

### 1.10 `gls-enforcement-worker` split

- [ ] Split out from monolith into its own deployable.
- [ ] Same contract surface as Phase 0.5 reference; happy + per-service errors only.
- [ ] Rollback: feature flag.

### 1.11 `gls-indexing-worker` (NEW)

- [ ] Greenfield service consuming `gls.documents.classified`.
- [ ] Writes document body + `extractedMetadata` to Elasticsearch.
- [ ] Per-service error handling: ES down → INDEX_FAILED with retry; mapping conflict → quarantine collection.

### 1.12 `gls-audit-collector` (Tier 1 + Tier 2)

- [ ] Single binary with two roles (Class D singleton for Tier 1, Class B horizontal for Tier 2).
- [ ] Hash-chain implementation for Tier 1 with per-resource chains (per CSV #4).
- [ ] Tier 1 backend: external WORM (per CSV #3) — e.g. S3 Object Lock or Mongo append-only with role-based deny.
- [ ] Tier 2 backend: OpenSearch hot + S3 cold via ILM.
- [ ] Existing services begin emitting via `gls-platform-audit` library.

### 1.13 Connectors family review

- [ ] `gls-connectors` already exists in some form (Drive, Gmail). Audit current code; conform to new contract surface; add `gls-platform-audit` integration.
- [ ] Per-source watch sharding via ShedLock.

### Acceptance gate (Phase 1 overall)

- End-to-end happy path: a document goes from upload → classified → indexed via the new architecture.
- Audit Tier 1 events recorded for the document's lifecycle (`DOCUMENT_INGESTED`, `DOCUMENT_CLASSIFIED`, `GOVERNANCE_APPLIED`).
- Performance is within 10% of baseline (or better).
- Every service has a working rollback flag.
- Hub pack import propagates to all running replicas within 30 seconds (validates CSV #30).
- Cost-per-document is measured and recorded.

### Per-service acceptance gate

Before moving to the next service in the order:
- Contract committed and CI green.
- Service deployed (local + target environment).
- Happy path integration test passes.
- Per-service error scenarios from §6 covered by tests.
- Audit events emitted correctly.
- Traces visible in OTel collector.
- Performance recorded against baseline.

### Risk callouts

- **Cascade tuning takes longer than expected.** BERT thresholds, SLM thresholds, escalation rates need empirical tuning. Don't gate Phase 1 acceptance on optimal tuning — gate on *correctness*; tune in production with dashboards.
- **Mongock migrations failing in environments with existing data.** Test every migration against a snapshot of production data before merging.
- **The cutover (1.3) goes wrong.** This is the first irreversible point. The feature flag is the safety net; treat it as load-bearing.

### Estimated size

25–40 PRs over 8–12 weeks.

---

## Phase 2 — System-wide resilience

**Goal:** the system survives failures gracefully. No more "happy path with error returns" — full recovery, replay, and chaos handling.

### Scope

#### 2.1 Recovery jobs

- [ ] `StaleDocumentRecoveryTask` in `gls-scheduler`: detects pipeline runs stuck > 15 min; resets to last completed node; re-queues.
- [ ] `classification_outbox` reconciler (per §6.5): drains pending classifications when MCP recovers.
- [ ] Audit outbox replay: on `gls-audit-collector` restart, drains any unacked outbox rows.
- [ ] ES reconciliation job: rebuild ES from Mongo if index is corrupted or behind.
- [ ] Hub pack import retry on `gls.config.changed` failure.

#### 2.2 Vendor / external API resilience

- [ ] Anthropic circuit breaker — open after N consecutive failures, half-open with backoff.
- [ ] Anthropic 429 handling — `Retry-After` honoured; jitter added; cascade falls back to Ollama where configured.
- [ ] Ollama unreachable → circuit + fallback to Anthropic.
- [ ] MCP unreachable → workers proceed with cap on confidence (per §6.6).
- [ ] Cost budget gate enforcement — daily/job spending cap with auto-degrade to SLM-only.

#### 2.3 Backpressure + rate limiting

- [ ] Per-worker semaphore on in-flight calls (per service spec § Scaling).
- [ ] Router 429 with `Retry-After` honouring orchestrator-side backoff.
- [ ] RabbitMQ quorum queues + DLQ wiring for every channel.
- [ ] DLQ reprocessing UI hook (the data path; UI in Phase 3).

#### 2.4 Failover + leader election

- [ ] `gls-audit-collector` Tier 1 leader election via ShedLock.
- [ ] `gls-scheduler` leader election (already singleton-style; ensure HA).
- [ ] Connector per-source watches: ShedLock keys per shared drive / per mailbox.

#### 2.5 Chaos + integration tests

- [ ] Per-service: kill a replica → no document is lost or stuck.
- [ ] Anthropic 5xx for 1h → queues build, recover when back; no data loss.
- [ ] MongoDB primary failover → pipeline pauses gracefully and resumes.
- [ ] RabbitMQ node loss in 3-node cluster → no message loss; consumers continue.
- [ ] MinIO unreachable → extraction fails fast, stale recovery picks up later.
- [ ] Hub pack import → all replicas pick up new rules; in-flight documents use the version they started with.
- [ ] Every §6 scenario from architecture doc has a corresponding test.

#### 2.6 Observability uplift

- [ ] Tier-of-decision histogram per category.
- [ ] Escalation rate dashboard.
- [ ] Cost-per-document, cost-per-tier, daily spend running total.
- [ ] p50/p95/p99 latency by tier and by mime type.
- [ ] MCP availability impact on confidence (the metric mentioned in §9 Phase E).
- [ ] Alerts on circuit breaker open, DLQ depth, stale documents, audit chain breaks.

### Acceptance gate

- All §6 scenarios from architecture doc are exercised in automated integration tests.
- Chaos test suite passes (kill any single pod → recover within SLO).
- Vendor-outage simulation: 1h Anthropic 5xx → no document permanently failed; system catches up post-recovery.
- Performance under failure: degraded mode latency p95 within agreed bounds.
- Audit chain integrity verified by a periodic validator job.

### Risk callouts

- **Chaos tests are flaky.** Treat flakes as bugs in the system, not the test.
- **Recovery jobs themselves can fail.** Each has its own observability and retry; they're not infallible.

### Estimated size

15–25 PRs over 4–8 weeks.

---

## Phase 3 — Admin UI (full configuration experience)

**Goal:** all v2 features are configurable by an admin without database access.

> **Note:** minimum admin UI ships during Phase 1 alongside the services that need it (e.g. POLICY block editor before stage ④ goes live). Phase 3 is the polished, complete experience.

### Scope

#### 3.1 Pipeline configuration

- [ ] Visual DAG editor for `PipelineDefinition` (drag-drop nodes and edges).
- [ ] Node-level config: prompt block selection, threshold tuning, retry policy.
- [ ] Pipeline versioning timeline + rollback.
- [ ] Per-category pipeline assignment.

#### 3.2 Block library

- [ ] Universal block list with type filter (PROMPT, ROUTER, BERT_CLASSIFIER, EXTRACTOR, ENFORCER, POLICY).
- [ ] Per-type editors (prompt content, regex sets, model config, scan rules, enforcement rules, taxonomy dispatch).
- [ ] Version history per block + rollback.
- [ ] Draft / Publish workflow.
- [ ] Feedback aggregation views per block.
- [ ] "Improve with AI" button for prompts with sufficient feedback.

#### 3.3 Taxonomy management

- [ ] Tree editor for `ClassificationCategory`.
- [ ] Drag-drop reorganisation (with audit trail).
- [ ] Per-node fields: keywords, sensitivity, retention, metadata schema, owner, custodian.
- [ ] Hub pack browser + import wizard.
- [ ] Pack diff view (what changes if I import this pack version).

#### 3.4 Component management (CSV #31–34)

- [ ] PII type definitions: list, edit, link to categories.
- [ ] Sensitivity definitions: list, edit, link.
- [ ] Storage tiers: list, edit, link.
- [ ] Trait definitions: list, edit, link.
- [ ] Metadata schemas: list, edit, link, field-level config.
- [ ] Governance policies: list, edit, link, conditions.

#### 3.5 Document monitoring + retry

- [ ] Document list with status, classification, audit timeline.
- [ ] Retry / reset actions on stuck or failed documents.
- [ ] DLQ inspection + replay UI.
- [ ] Bulk reclassification trigger (with cost estimate from budget gate).

#### 3.6 Audit explorer

- [ ] Tier 1 timeline per document (compliance view).
- [ ] Tier 2 query interface (operations / debugging view).
- [ ] Tier 3 trace deep-link from any audit event.
- [ ] Export for legal hold, with redaction options.

#### 3.7 Subscriptions, roles, features

- [ ] Existing UI in `gls-app-assembly` carried forward; ensure it works against new architecture.
- [ ] Role-feature matrix admin.
- [ ] User assignment.

#### 3.8 Performance dashboards

- [ ] Tier-of-decision histogram (interactive).
- [ ] Escalation rate by category.
- [ ] Cost dashboards: per day, per tier, per category, projected monthly.
- [ ] Latency dashboards: p50/p95/p99 by tier and mime.

#### 3.9 Hub pack management

- [ ] Browse hub catalogue.
- [ ] Import preview (what changes locally).
- [ ] Version history per imported pack.
- [ ] Upgrade and rollback.

### Acceptance gate

- All v2 features can be configured by an admin without database access.
- Audit views are fully queryable.
- Hub pack import is one-click.
- UX usability test passes with internal admin users.

### Estimated size

20–30 PRs over 6–10 weeks.

---

## Cross-cutting tracks (run in parallel with phases)

### Track A — Hub-side changes

Runs alongside Phases 0–2. Hub is treated as an external dependency in this plan, but its evolution is required for v2 to fully work.

- **Phase 0:** AsyncAPI for `gls.config.changed` events that hub will fire. `PackImportService` design doc.
- **Phase 1:** `PackImportService` updated to fire events; pack model gains `applicableCategoryIds[]` plumbing.
- **Phase 2:** Hub-side resilience (retry on event publish failure).
- **Phase 3:** Hub admin UI updates to match local UI conventions.

### Track B — Migration / cutover

The strangler-fig approach. New services run alongside the existing monolith; traffic shifts gradually.

- **Phase 0:** Document the strangler approach. Identify cutover points (1.3 orchestrator, 1.10 enforcement, 1.11 indexing, 1.12 audit). Decide rollback flag mechanism (Spring config + Mongo).
- **Phase 1:** Each cutover PR includes:
  - Feature flag default `false`.
  - Smoke test in staging with flag `true`.
  - Production rollout with flag `true` for 5% → 25% → 100% over a week.
  - Rollback plan with named on-call.
- **Phase 2:** Monolith retirement plan once new path is stable.

### Track C — Performance baseline + comparison

- **Phase 0:** Capture baseline.
- **End of every phase:** Re-measure against baseline. Commit results.
- **Phase 1 acceptance:** Latency within 10% of baseline.
- **Phase 2 acceptance:** Failure-mode latency within agreed bounds.

### Track D — Minimum admin UI per service (Phases 1–2)

UI work *inside* phases 1 and 2 — not waiting for Phase 3:

- **1.2 classifier-router:** ROUTER block editor (minimum viable).
- **1.4 BERT:** BERT_CLASSIFIER block editor + model registry view.
- **1.7 component wiring:** category-link UI for PII / Storage / Trait / Sensitivity.
- **1.8 POLICY block:** POLICY block editor (minimum viable).
- **1.12 audit-collector:** audit timeline view (Tier 1 + Tier 2 — minimum viable).
- **2.3 backpressure:** DLQ inspection (minimum viable).

These minimum views get *replaced* or *enhanced* in Phase 3.

---

## Acceptance gates summary

| Phase | Gate condition |
|---|---|
| 0 | A1/A2/A5/A6 DECIDED; `contracts/_shared/` complete; CI gates green; performance baseline captured. |
| 0.5 | Reference service deployed end-to-end; pattern documented; latency within 20% of `gls-document-processing` baseline. |
| 1 | End-to-end happy path through new architecture; latency within 10% of baseline; hub propagation < 30s; rollback flags work. |
| 2 | All §6 scenarios in automated tests; chaos suite passes; vendor outage simulated; audit chain integrity verified. |
| 3 | Full admin UX; usability test passes; hub pack import is one-click. |

---

## Risk register

| Risk | Mitigation | Phase |
|---|---|---|
| Over-investment in `_shared/` upfront | Keep minimal; expand on demand | 0 |
| Reference pattern bakes in something wrong | Fix in 0.5, not after Phase 1 | 0.5 |
| Cutover (1.3) breaks production | Feature flag gates new path; gradual traffic shift | 1 |
| BERT/SLM thresholds need tuning beyond plan | Gate on correctness, not optimal tuning; tune live | 1 |
| Mongock migration fails in environments with existing data | Test against production snapshot pre-merge | 0 / 1 |
| Chaos tests flaky | Treat flakes as bugs in the system | 2 |
| Phase drag from waiting for decisions | A1/A2/A5/A6 gating Phase 0; lock them at start | 0 |
| Hub work falls behind | Track A is its own commitment; don't let Phase 1 depend on it for non-blocking items | parallel |
| UI work neither shipped during phases nor late | Track D enforces minimum-viable UI per service | 1–2 |

---

## What gets logged where as we go

- **Decision made** → `version-2-decision-tree.csv` (per `CLAUDE.md` decision-log rules).
- **Architecture clarification** → `version-2-architecture.md`.
- **Implementation specifics** → service `README.md` + `CHANGELOG.md`.
- **Contract change** → `contracts/<service>/CHANGELOG.md` + version bump.

The decision tree, architecture doc, and this plan are the three load-bearing documents. Keep them aligned.

---

## What this plan deliberately does *not* include

- **Specific deadlines.** Estimated sizes are PR counts, not weeks-to-team. Pace depends on team size and bandwidth.
- **Headcount or team structure.** Assumed: one or more agents working contracts-first against this plan, with a human reviewer.
- **A v3.** This plan ends when v2 ships. v3 starts when v2 is in production and we know what we got wrong.
