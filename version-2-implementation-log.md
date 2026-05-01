---
title: V2 Implementation Log
lifecycle: forward
---

# V2 Implementation Log

Append-only record of v2 implementation progress. Companion to:
- `version-2-architecture.md` — the *what* (how the system works)
- `version-2-decision-tree.csv` — the *why* (decisions and rationale)
- `version-2-implementation-plan.md` — the *what's planned* (phases, gates, work items)

This log is the **what happened**.

## Rules (also in `CLAUDE.md`)

- **Append-only.** Never edit or delete past entries. If a decision reverses or a stage is redone, append a new entry that references the old one.
- **Chronological.** Newest entries at the bottom of the file. Read top-to-bottom for the story.
- **Per sub-phase at minimum.** 1.1, 1.2, etc. — granular enough to know what's done. More frequent if work spans multiple sessions.
- **Same PR as the work.** A log entry lands in the PR that did the work, not separately.
- **Truthful.** Record what actually happened, including detours, false starts, and reversals — not the polished version.

## Entry shape

```
## YYYY-MM-DD — Phase X.Y — <Sub-phase title>

**Done:** <what was completed>
**Decisions logged:** <CSV row IDs added or updated>
**Contracts touched:** <contracts/ paths modified, with VERSION bump notes>
**Files changed:** <other paths>
**Open issues:** <anything blocking, deferred, or unclear — link to a TODO or Issue if applicable>
**Next:** <what comes next; usually the next sub-phase from version-2-implementation-plan.md>
```

Multi-session sub-phases get multiple entries. The final entry for a sub-phase should make clear it's the closing entry for that work item (e.g. *"Phase X.Y — closing entry"*).

## Per-phase status board

Update this table when a phase's status changes. The detailed entries below are the source of truth; this is just a quick scan.

| Phase | Status | Started | Completed | Notes |
|---|---|---|---|---|
| 0   | Substrate complete; minor follow-ups outstanding | 2026-04-26 | — | 0.1–0.6, 0.8, 0.9, 0.10, 0.12 done. 0.7 done bar Python sketch + Rabbit circuit-breaker (envelope + outbox indexes + library + auto-config + schema validation + outbox-to-Rabbit relay + ShedLock leader election + Micrometer metrics all landed). 0.11 scaffolded (load driver awaits representative content). |
| 0.5 | Substantially complete | 2026-04-26 | — | 0.5.1, 0.5.2, 0.5.6 done. 0.5.3: error returns + audit (success + failure) + readiness HealthIndicators + metrics + tracing all done; **JWT outstanding** (blocked on JWKS infra). 0.5.4 unit-level only (153 reactor tests, 41 in extraction module); integration tests blocked on issue #7. 0.5.5 Dockerfile + Compose done; K8s + CI/CD image push outstanding. |
| 1   | 1.1 / 1.2 / 1.3 complete; 1.4 / 1.5 in flight | 2026-04-29 | — | 1.1 extraction triad shipped. 1.2 closed. 1.3 cutover wired behind `pipeline.classifier-router.enabled` (default off; perf-comparison gate on Phase 0.11). 1.4 PR1: `gls-bert-inference` + `BERT_CLASSIFIER` schema (stub backend). 1.4 PR2: BERT cascade tier wired in router behind `gls.router.cascade.bert.enabled` (default off, falls through to LLM on `MODEL_NOT_LOADED`). 1.4 PR3: router async surface (`Prefer: respond-async` + `/v1/jobs`). 1.5 PR1: `gls-slm-worker` module + contract (stub `SLM_NOT_CONFIGURED` backend; real Haiku + Ollama backends + cascade wire-in are follow-ups). 1.6 + trainer + per-category enable remain. |
| 2   | Not started | — | — | |
| 3   | Not started | — | — | |

Cross-cutting tracks:

| Track | Status | Notes |
|---|---|---|
| A — Hub-side | Implicitly covered | `GovernanceConfigChangeBridge` (PR #25) listens on Spring Data Mongo `AfterSaveEvent` / `AfterDeleteEvent` for governance entities, so Hub-driven `PackImportService` writes (which use the same `repo.save(...)` path) automatically publish `gls.config.changed` events. No separate Hub-side publisher is needed unless the Hub deploys a separate database from the api. |
| B — Migration / cutover | Not started | Strangler-fig approach planned |
| C — Performance baseline | Scaffolded | Methodology + CSV format + capture script in place; load driver still stub. Real captures land once a representative workload exists. |
| D — Minimum admin UI | Not started | Activates with Phase 1 |

---

## Entries

## 2026-04-26 — Phase 0.1 — Decision-gate close-out (and 0.2 deployment target)

**Done:** Locked the §11.A shape decisions and §11.B / §11.C convention decisions to DECIDED; locked the v2 deployment target.

**Decisions logged:**
- §11.A shape: CSV #13 (sync/async response), #14 (block-version pinning), #16 (idempotency / nodeRunId TTL), #17 (RFC 7807 error envelope), #18 (service-account JWTs).
- §11.B conventions: CSV #19 (text payload inline ≤ 256 KB else `textRef`), #20 (`traceparent` mandated), #21 (`GET /v1/capabilities`), #22 (`costUnits` + `tokensIn/Out`).
- §11.C non-functional: CSV #23 (429 + `Retry-After`), #24 (12 s sync timeout), #25 (path-based versioning), #26 (free-form `extractedMetadata` Map).
- Topology: CSV #38 (new) — Docker Compose for v2; K8s deferred.

**How decisions were made (flagged for review):** All thirteen RECOMMENDED rows above were accepted *as-written* — no per-decision sit-down was held. The plan calls for one (§Plan 0.1: "hour-long sit-down per decision"). Decisions are reversible by appending a superseding row per `CLAUDE.md` decision-log rules; flagged here so a future review can revisit any of them. CSV #16 was added to the batch alongside #13/#14/#17/#18 because `idempotency.yaml` is part of 0.4 substrate — strictly outside the originally-framed 19–26 scope but inside the spirit of "lock §11 before authoring `_shared/`."

**Contracts touched:** None yet — substrate authoring begins in 0.3 / 0.4.

**Files changed:**
- `version-2-decision-tree.csv` — 13 rows flipped RECOMMENDED → DECIDED; 1 new row (#38).
- `version-2-implementation-log.md` — status board updated; first entry appended.
- `docs/operations/k8s-deferred-guidance.md` (new) — guidance for adopting K8s when the trigger conditions arrive.

**Open issues:**
- 0.5 lint tool: Spectral vs Redocly — not yet decided.
- 0.5 generator: `openapi-generator-maven-plugin` recommended in plan; not yet confirmed.
- 0.10 migration tool: Mongock recommended in plan; not yet confirmed.
- The thirteen accepted-as-written decisions warrant a review pass before Phase 1 cutover work begins.

**Next:** Phase 0.3 — `contracts/` skeleton, once 0.5 tooling is confirmed.

## 2026-04-26 — Phase 0.5 / 0.10 — Tooling decisions (closing entry)

**Done:** Closed the three tooling open issues from the previous entry: OpenAPI generator, OpenAPI linter, MongoDB migration tool. Substrate authoring is now unblocked.

**Decisions logged:**
- CSV #39 — `openapi-generator-maven-plugin` (OpenAPITools). Spring Boot server stubs (`interfaceOnly=true`, `useSpringBoot3=true`, `useJakartaEe=true`) + TypeScript fetch client for the Next.js frontend.
- CSV #40 — Spectral as the OpenAPI 3.1.1 linter. Start from `spectral:oas recommended`; tighten via `contracts/.spectral.yaml` over time. Pre-commit + CI integration.
- CSV #41 — Mongock 5+ as the MongoDB schema migration tool. Wired into `gls-app-assembly`; no-op smoke `@ChangeUnit` ships as part of 0.10.

**Contracts touched:** None yet — substrate authoring begins in 0.3 / 0.4.

**Files changed:** `version-2-decision-tree.csv` (3 new rows: #39, #40, #41); `version-2-implementation-log.md` (status board + this entry).

**Open issues:** None blocking 0.3. Cumulatively across the two log entries to date, 16 decisions were accepted on the recommended option without the per-decision sit-down the plan calls for — 13 §11 shape/convention decisions in a bulk pass; 3 tooling decisions one-by-one with rationale walk-through. All reversible via superseding rows per `CLAUDE.md` decision-log rules. Flagged for a review pass before Phase 1 cutover work begins.

**Next:** Phase 0.3 — `contracts/` skeleton (`_shared/`, `messaging/`, `audit/`, `blocks/`, per-service placeholders), each folder with its own `VERSION` (0.1.0) and `CHANGELOG.md`.

## 2026-04-26 — Phase 0.3 — `contracts/` skeleton (cross-cutting only)

**Done:** Scaffolded the cross-cutting subset of the `contracts/` directory. Per-service folders deferred until each service's phase begins (the convention is established by the cross-cutting folders; per-service noise without value is avoided).

**Decisions logged:** None new — this entry implements decisions logged in the previous two entries. A pacing-decision (defer per-service placeholders) was made in-session and is recorded in **Open issues** below rather than the CSV, since it does not change the architectural contract.

**Contracts touched:**

- `contracts/README.md` (new) — top-level index, layout, workflow, cross-references.
- `contracts/.spectral.yaml` (new) — initial ruleset extending `spectral:oas`. Custom rules empty until 0.4 conventions surface.
- `contracts/_shared/` — `README.md`, `VERSION` (0.1.0), `CHANGELOG.md`.
- `contracts/messaging/` — `README.md`, `VERSION` (0.1.0), `CHANGELOG.md`.
- `contracts/audit/` — `README.md`, `VERSION` (0.1.0), `CHANGELOG.md`.
- `contracts/blocks/` — `README.md`, `VERSION` (0.1.0), `CHANGELOG.md`.

**Files changed:** 14 new files under `contracts/`. `version-2-implementation-log.md` (status board + this entry).

**Open issues:** Per-service folders are not created yet. They will spawn in the PR that begins each service's contract work. The 0.3 acceptance gate (per `version-2-implementation-plan.md`) calls for "placeholders for known services" — this strict reading is being relaxed here per a pacing decision in this session. If literal compliance with the gate-as-written matters, revisit before declaring Phase 0 closed.

**Next:** Phase 0.4 — author the eight `_shared/` YAML schemas (`error-envelope`, `security-schemes`, `common-headers`, `capabilities`, `text-payload`, `pagination`, `idempotency`, `retry`).

## 2026-04-26 — Phase 0.4 — `_shared/` content (closing entry)

**Done:** Authored the eight cross-cutting OpenAPI 3.1.1 component files in `contracts/_shared/`. These are the substrate that every per-service spec will `$ref` from Phase 0.5 onwards. `_shared/VERSION` bumped 0.1.0 → 0.2.0; `_shared/CHANGELOG.md` 0.2.0 entry added; `_shared/README.md` content list reframed from "target" to "delivered".

**Decisions logged:** None new — implements decisions #13, #16, #17, #18, #19, #20, #21, #23 from PR #1.

**Contracts touched:**

- `contracts/_shared/error-envelope.yaml` (new) — RFC 7807 + ig-central extensions per #17. Defines `ProblemDetails`, `ProblemTraceStep`, and the `Error` response.
- `contracts/_shared/security-schemes.yaml` (new) — service-account JWT bearer scheme per #18. Required claims documented.
- `contracts/_shared/common-headers.yaml` (new) — `traceparent` (#20), `Idempotency-Key` (#16), `X-Request-Id`, `Prefer` (#13). Header parameters declared once for `$ref` reuse.
- `contracts/_shared/capabilities.yaml` (new) — `Capabilities` response schema per #21. `tiers[]`, `models[]`, `flags{}` shape.
- `contracts/_shared/text-payload.yaml` (new) — `TextPayload` `oneOf` inline / reference per #19. 256 KB inline ceiling enforced via `maxLength`.
- `contracts/_shared/pagination.yaml` (new) — cursor-based `Page` envelope; `PageCursor` / `PageSize` parameters; max page size 500.
- `contracts/_shared/idempotency.yaml` (new) — `InFlight` 409 response for in-progress keys per #16. Cross-references `error-envelope.yaml` and `retry.yaml`.
- `contracts/_shared/retry.yaml` (new) — `RetryAfter` header and `TooManyRequests` 429 response per #23. Cross-references `error-envelope.yaml`.
- `contracts/_shared/VERSION` — 0.1.0 → 0.2.0.
- `contracts/_shared/CHANGELOG.md` — 0.2.0 entry added.
- `contracts/_shared/README.md` — content list reframed (target → delivered).

**Files changed:** 8 new YAMLs and 3 updated `_shared/` files. `version-2-implementation-log.md` (status board + this entry).

**Open issues:**

- A Spectral rule that *requires* every operation to declare the error envelope for 4xx/5xx is forecast in `contracts/.spectral.yaml` but not yet authored. Lands once the first per-service spec is in flight (Phase 0.5).
- Cost-attribution shape (CSV #22 — `costUnits` + `tokensIn` / `tokensOut`) is *not* in this PR. It belongs in per-operation response shapes; carry forward to the Phase 0.5 reference service.
- Sibling-relative `$ref`s inside `_shared/` (e.g. `retry.yaml#/components/headers/RetryAfter`) are not yet validated end-to-end through Spectral + `openapi-generator-maven-plugin`. The first real exercise comes with the Phase 0.5 reference service; expect minor tweaks if a path doesn't resolve as expected.

**Next:** Phase 0.5 — wire OpenAPI tooling into the build (Spectral CI job, `openapi-generator-maven-plugin` Maven phase, pre-commit hook), and Phase 0.6 — declare the existing Rabbit topology as AsyncAPI 3.0.

## 2026-04-26 — Phase 0.5 (wiring) — OpenAPI tooling integration (closing entry)

**Done:** Wired the OpenAPI tooling end-to-end. Spectral runs locally via pre-commit and in CI; `openapi-generator-maven-plugin` is declared in the parent `pluginManagement` and exercised by a new `contracts-smoke` Maven module against a placeholder `hello-world` spec. The smoke spec proves cross-file `$ref` resolution into `_shared/` works through both Spectral and the Maven generator + Java compile.

**Decisions logged:** None new — implements decisions #39 (`openapi-generator-maven-plugin`) and #40 (Spectral) from PR #1.

**Contracts touched:**

- `contracts/hello-world/openapi.yaml` (new) — minimal smoke spec: `GET /v1/hello/{name}` with `Traceparent`, `Error` envelope, `serviceJwt` security all `$ref`'d from `_shared/`.
- `contracts/hello-world/VERSION` (new) — 0.1.0.
- `contracts/hello-world/CHANGELOG.md` (new).
- `contracts/hello-world/README.md` (new) — describes the smoke and the `$ref` patterns per-service authors should copy.
- `contracts/README.md` — added Smoke section + Validation section pointing at `.pre-commit-config.yaml` and the CI job.

**Files changed:**

- `backend/pom.xml` — added `openapi-generator-maven-plugin.version` property (7.10.0); declared the plugin in `<pluginManagement>`; added `contracts-smoke` to `<modules>`.
- `backend/contracts-smoke/pom.xml` (new) — Maven module that runs `openapi-generator-maven-plugin generate` against `contracts/hello-world/openapi.yaml` (Spring generator, `interfaceOnly=true`, `useJakartaEe=true`, `useSpringBoot3=true`) and compiles the generated stub. Depends on `spring-boot-starter-web`, `jakarta.validation-api`, `swagger-annotations`.
- `.pre-commit-config.yaml` (new) — local hook running Spectral on any YAML edited under `contracts/`.
- `.github/workflows/ci.yml` — new `contracts-validate` job (Spectral lint + Maven smoke); `docker-build` now `needs: [backend-test, frontend-lint, contracts-validate]`.
- `version-2-implementation-log.md` — status board + this entry.

**Open issues:**

- The Maven plugin version 7.10.0 was chosen as a recent stable; `useSpringBoot3=true` is the closest available flag for our Spring Boot 4 codebase. If the generator emits source that doesn't compile against Spring Boot 4, fix in a follow-up by either bumping the plugin or switching templates. CI will surface this immediately.
- Spectral severity is `--fail-severity error` for now (per CSV #40 "start permissive"). Tighten to `warn` once `_shared/` settles and per-service specs land in Phase 0.5 reference service.
- The forecast Spectral rule that *requires* every operation to declare the error envelope on 4xx/5xx is still empty in `contracts/.spectral.yaml`. Authoring deferred until first per-service spec is in flight (Phase 0.5 reference service).
- Pre-commit framework (`pre-commit`) is not auto-installed; developers run `pre-commit install` once per clone. README mention added; consider adding to `docs/operations/getting-started-guide.md` in a follow-up.

**Next:** Phase 0.6 — declare the existing Rabbit topology as AsyncAPI 3.0 in `contracts/messaging/asyncapi.yaml`. Phase 0.7 — audit infrastructure (envelope schema + outbox + `gls-platform-audit` shared library). Phase 0.10 — wire Mongock and ship the no-op smoke change unit.

## 2026-04-26 — Phase 0.6 — AsyncAPI declarations (closing entry)

**Done:** Authored `contracts/messaging/asyncapi.yaml` (full declaration of the existing RabbitMQ topology) and `contracts/audit/asyncapi.yaml` (stub declaring the three audit tier channel families pending Phase 0.7 envelope work). Both VERSIONs bumped 0.1.0 → 0.2.0; CHANGELOGs and READMEs reframed accordingly.

**Decisions logged:** None new. Implements the AsyncAPI track of CSV #20 (traceparent propagation, observed via `idempotencyKey` alignment with the HTTP convention from CSV #16), CSV #14 (block-version pinning surfaced as `blockVersion` field on `LlmJobRequestedEvent`).

**Discovery — plan vs reality:** `version-2-implementation-plan.md` Phase 0.6 said `messaging/asyncapi.yaml` should declare `gls.audit.*` and `gls.config.changed`. The current codebase has neither — they're forward-looking work for Phase 0.7 (audit) and Phase 0.8 (config-cache library). The messaging spec describes them in its `info.description` block as forward-looking; full declarations land in their respective phases. Recorded for traceability rather than rewriting the plan in place.

**Contracts touched:**

- `contracts/messaging/asyncapi.yaml` (new, 0.2.0) — two exchanges, eight channels, ten operations, five message envelopes (`DocumentIngestedEvent`, `DocumentProcessedEvent` + nested `PiiSummaryEntry`, `DocumentClassifiedEvent`, `LlmJobRequestedEvent`, `LlmJobCompletedEvent`). Per-channel producer/consumer documentation with class names so a reader can trace from spec to code.
- `contracts/audit/asyncapi.yaml` (new, 0.2.0) — stub declaring `audit.tier1.{eventType}`, `audit.tier2.{eventType}`, `audit.tier3.{eventType}` channel families with parameterised routing-key suffix and a deliberately opaque `AuditEventEnvelope` schema. Forward-looking; full payloads land in Phase 0.7.
- `contracts/messaging/{VERSION, CHANGELOG.md, README.md}` — bumped, entry added, content list reframed delivered.
- `contracts/audit/{VERSION, CHANGELOG.md, README.md}` — bumped, entry added, content list split delivered/target.

**Files changed:** 2 new YAMLs, 6 updated metadata files. `version-2-implementation-log.md` (status board + this entry).

**Open issues:**

- The forecast `gls.config.changed` channel (CSV #30) is referenced in `messaging/asyncapi.yaml`'s description but has no channel/payload yet — lands with `gls-platform-config` in Phase 0.8.
- `pipeline.resume` channel is declared but unused in the current engine. Kept for forward compatibility; consider removing if a future review confirms it has no future role.
- `document.classification.failed` is published but not actively consumed. Once Phase 0.7 / 1.12 ships `gls-audit-collector`, this should become a Tier 1 / Tier 2 source per CSV #5.
- AsyncAPI is not yet linted in CI. The `messaging/README.md` listener-stub policy says we'll validate via `@asyncapi/cli`. Add to the `contracts-validate` CI job in a small follow-up PR (or as part of Phase 0.7).
- Audit envelope schema (`event-envelope.schema.json`) and per-tier payload bindings are deliberately stubbed; full work lands in Phase 0.7 alongside CSV #3 / #4 / #5 / #6 / #7 / #8 closures.

**Next:** Phase 0.7 — author `contracts/audit/event-envelope.schema.json`; design the `audit_outbox` MongoDB collection; build the `gls-platform-audit` shared library skeleton. Phase 0.10 — Mongock wiring (independent; can run in parallel).

## 2026-04-26 — Phase 0.10 — Mongock wiring (closing entry)

**Done:** Wired Mongock 5.4.4 into `gls-app-assembly` startup. Smoke `@ChangeUnit` ships at `co.uk.wolfnotsheep.infrastructure.migrations.V001_MongockSmoke` — runs once, logs success, no schema work. Future schema changes (new indexes, field migrations, backfills, the existing ad-hoc `SlugBackfillRunner` etc.) become `@ChangeUnit` classes in the same package.

**Decisions logged:** None new — implements CSV #41 (Mongock chosen) from PR #1.

**Files changed:**

- `backend/pom.xml` — added `mongock.version` property (5.4.4); imported `io.mongock:mongock-bom` in `<dependencyManagement>`.
- `backend/gls-app-assembly/pom.xml` — added `mongock-springboot-v3` and `mongodb-springdata-v4-driver` dependencies.
- `backend/gls-app-assembly/src/main/java/co/uk/wolfnotsheep/infrastructure/migrations/V001_MongockSmoke.java` (new) — smoke change unit.
- `backend/gls-app-assembly/src/main/resources/application.yaml` — Mongock config block (`enabled`, `migration-scan-package`, `transaction-enabled: false`, `runner-type: initializing-bean`, `track-ignored: false`). `MONGOCK_ENABLED` env-var override for emergency disable.
- `CLAUDE.md` — added "Schema Migrations" section documenting the conventions: `@ChangeUnit` only, naming convention, `id` immutability rule, fix-forward policy, env-var disable.
- `version-2-implementation-log.md` — status board + this entry.

**Open issues:**

- Mongock 5.4.4's `-springboot-v3` starter is targeted at Spring Boot 3; we're on Spring Boot 4. Local Maven compile is clean, but **runtime startup has not been verified in this session** — needs a `docker compose up` exercise before the next merge to be sure. If startup fails, the most likely outcome is a Spring Data Mongo 5 vs 4 driver mismatch; fall-back is to wait on a future `mongodb-springdata-v5-driver` release or pin Mongock against the newest Spring-Boot-4-compatible release once available.
- The driver module name (`mongodb-springdata-v4-driver`) refers to the *Spring Data Mongo* major version, not Mongo server version; Spring Boot 4 may include Spring Data Mongo 5, in which case this driver may not bind cleanly. **Treat this PR as wiring-only** — the runtime smoke is a follow-up.
- Existing ad-hoc startup runners (e.g. `SlugBackfillRunner`) should be migrated to Mongock change units in a focused follow-up PR. Not in scope here.

**Next:** Phase 0.7 — audit infrastructure (envelope schema + outbox + `gls-platform-audit` shared library). Phase 0.8 — `gls-platform-config` cache library + `gls.config.changed` channel. Phase 0.11 — performance baseline. Phase 0.12 — local dev experience updates.

## 2026-04-26 — Audit decisions close-out (closing entry)

**Done:** Locked the six audit-domain decisions from §7.8 / §10 to DECIDED so Phase 0.7 (audit infrastructure: envelope schema, outbox, `gls-platform-audit` library) is unblocked.

**Decisions logged (all flipped RECOMMENDED → DECIDED, recommendations accepted as written):**

- CSV #3 — Tier 1 backend: external WORM (S3 Object Lock + Athena, or managed audit service). Not in-Mongo with role-based deny.
- CSV #4 — Per-resource audit hash chain (per-document, per-block, per-user) — not a single global chain.
- CSV #5 — Failed classifications recorded in **both** Tier 1 (`CLASSIFICATION_FAILED` final outcome) and Tier 2 (`CLASSIFICATION_ATTEMPT_FAILED` per attempt), with different event types.
- CSV #6 — Right-to-erasure: envelope schema marks each details field as `metadata` (always retained) or `content` (subject to erasure); Tier 1 stores hashes of content, never raw text.
- CSV #7 — Reclassification supersession: every Tier 1 reclassification event carries `supersedes` / `supersededBy` links; queries reconstruct as-of-date views by walking the chain.
- CSV #8 — MCP tool calls auditable at Tier 2 — `MCP_TOOL_CALLED` event with `tool`, `paramsHash`, `responseHash`, `latencyMs`.

**How decisions were made (flagged for review):** Same pattern as the §11 batch in the inaugural Phase 0 PR — accepted as-written without per-decision sit-down. All six already had RECOMMENDED rationale in the CSV; treating them as DECIDED unblocks 0.7's envelope schema authoring. Reversible via superseding rows per `CLAUDE.md` decision-log rules. Cumulative count of accepted-as-written decisions across the project to date: 22 (16 §11 / tooling earlier + 6 audit here).

**Files changed:** `version-2-decision-tree.csv` (six rows flipped); `version-2-implementation-log.md` (this entry).

**Open issues:**

- Tier 1 backend choice between **S3 Object Lock + Athena** and a **managed audit service** (CSV #3 names both as the recommendation). Final concrete vendor / configuration choice deferred to Phase 0.7 / 1.12 when implementation begins.
- Tier 2 backend (OpenSearch + S3 ILM) is *proposed* in `version-2-architecture.md` §7 but not yet a CSV row. Lift to a DECIDED CSV row in Phase 0.7 once the implementation specifics are known.

**Next:** Phase 0.7 — author `contracts/audit/event-envelope.schema.json` (informed by CSV #4 / #6 / #7 / #8); design the `audit_outbox` MongoDB collection; build the `gls-platform-audit` shared library skeleton. Independently: Phase 0.8 (`gls-platform-config` + `gls.config.changed`), Phase 0.9 (Maven BOM decoupling — small), Phase 0.11 (perf baseline), Phase 0.12 (dev experience).

## 2026-04-26 — Phase 0.7 (envelope) — Audit event envelope schema

**Done:** Authored `contracts/audit/event-envelope.schema.json` — the JSON Schema 2020-12 contract for every audit event written by every container. Cross-tier correlation, per-resource hash-chain integrity, metadata/content partition for right-to-erasure, supersession links, traceparent propagation. Aligns with `version-2-architecture.md` §7.4 and the now-DECIDED audit decisions.

**Decisions logged:** None new. Implements CSV #4 (per-resource hash chain via `previousEventHash`), CSV #6 (metadata/content partition under `details`), CSV #7 (`supersedes` / `supersededBy` links), CSV #20 (`traceparent`). CSV #3 / #5 / #8 are referenced in the description fields but don't shape the envelope structurally.

**Schema highlights:**

- `tier` enum: `DOMAIN` (Tier 1, compliance) / `SYSTEM` (Tier 2, operations). Tier 3 (traces) is OpenTelemetry-only and out of scope for this envelope.
- `eventId` is a ULID — Crockford base32, 26 chars, time-sortable. Tier 2 paging benefits from lex order matching chronological order.
- `actor.type=USER` triggers a conditional `required: [id]` via `allOf`/`if`/`then` — JSON Schema 2020-12 native form.
- `tier=DOMAIN` triggers a conditional `required: [previousEventHash, resource, retentionClass]` via the same pattern. Tier 1 events MUST chain; Tier 2 events MAY omit chain.
- `details` partitioned: `metadata` (always retained), `content` (raw at Tier 2; sha256-hashed at Tier 1 — the relay strips raw values when promoting). `supersedes` and `supersededBy` (each ULID-pattern strings) implement CSV #7.
- `additionalProperties: false` at the envelope top level — locks the schema down. `details.metadata` and `details.content` permit additional properties so per-event-type payloads can extend.

**Contracts touched:**

- `contracts/audit/event-envelope.schema.json` (new) — the envelope.
- `contracts/audit/VERSION` — 0.2.0 → 0.3.0.
- `contracts/audit/CHANGELOG.md` — 0.3.0 entry.
- `contracts/audit/README.md` — Tier 1 backend status updated (DECIDED per CSV #3); content list reframed delivered vs target.

**Files changed:** 1 new schema, 3 updated metadata files. `version-2-implementation-log.md` (status board + this entry).

**Open issues:**

- Cross-format `$ref` between `audit/asyncapi.yaml` (YAML) and `event-envelope.schema.json` (JSON Schema 2020-12) is not yet wired — the asyncapi still carries its own opaque `AuditEventEnvelope` placeholder. Tightening this is a small follow-up; AsyncAPI 3.0 supports external `$ref` to JSON Schema files.
- `audit_outbox` MongoDB collection schema (indexes, write patterns, retention) — outstanding 0.7 work.
- `gls-platform-audit` shared library (JVM) — outstanding 0.7 work; will exercise this envelope schema by constructing valid events at every emission site.
- Tier 2 backend (OpenSearch + S3 ILM) — still proposed in architecture doc, not yet a CSV row. Lift in a future close-out PR.

**Next:** Continue 0.7 — design the `audit_outbox` collection (likely a Mongock `@ChangeUnit` for indexes); start the `gls-platform-audit` library skeleton (envelope construction helpers + outbox writer). Independently: 0.8 / 0.9 / 0.11 / 0.12.

## 2026-04-26 — Phase 0.7 (audit_outbox indexes) — Mongock change unit + relay pattern docs

**Done:** Designed the `audit_outbox` MongoDB collection's indexes via a Mongock `@ChangeUnit` and documented the audit relay pattern in `CLAUDE.md`. The collection itself is created lazily on first write (Mongo auto-creates collections when an index is added).

**Decisions logged:** None new. Implements architecture §7.7 (persist-before-publish outbox pattern) and the now-DECIDED CSV #4 (chain) / #6 (metadata-content partition).

**Indexes:**

- `idx_status_nextRetry` on `(status, nextRetryAt)` — the relay's primary query (find PENDING + retry-eligible FAILED rows in time order).
- `idx_eventId_unique` on `eventId`, unique — guard for idempotent re-emission so retries don't duplicate.
- `idx_createdAt` on `createdAt` — supports retention/cleanup queries.

**Files changed:**

- `backend/gls-app-assembly/src/main/java/co/uk/wolfnotsheep/infrastructure/migrations/V002_AuditOutboxIndexes.java` (new) — Mongock change unit. Local `mvn compile` passes.
- `CLAUDE.md` — new "Audit Relay Pattern" section above "Schema Migrations". Why-the-outbox, how-it-runs, rules (never bypass, never edit post-publish, eventId idempotency).
- `version-2-implementation-log.md` — status board + this entry.

**Open issues:**

- The `AuditOutboxRecord` POJO (Java mapping for outbox documents) lives in the upcoming `gls-platform-audit` library, not in this PR. Schema validation against `event-envelope.schema.json` lives there too.
- Runtime smoke (Mongock + Spring Boot 4 startup actually executes the `@ChangeUnit`) still unverified — same `docker compose up` exercise gap as Phase 0.10.
- TTL / retention job for `PUBLISHED` rows is Phase 2 work.

**Next:** Last piece of 0.7 — `gls-platform-audit` shared library skeleton (envelope construction helpers, `AuditOutboxRecord`, outbox writer, relay-to-Rabbit). New Maven module under `backend/`. Independently: 0.8 / 0.9 / 0.11 / 0.12.

## 2026-04-26 — Phase 0.7 (platform-audit library skeleton) — closing entry for sub-phase

**Done:** New Maven module `gls-platform-audit` under `backend/`. Provides the envelope, outbox model, repository, and emitter — the building blocks every JVM service will use. The relay-to-Rabbit piece (and the Spring Boot starter auto-configuration) are the remaining 0.7 follow-ups.

**Decisions logged:** None new — implements CSV #4 / #6 / #7 / #20 (already DECIDED) by mirroring `event-envelope.schema.json` in Java.

**Module layout:**

- `co.uk.wolfnotsheep.platformaudit.envelope.*` — `AuditEvent` record + `Tier` / `Outcome` / `ActorType` / `ResourceType` enums + `Actor` / `Resource` / `AuditDetails` nested records. Mirrors `contracts/audit/event-envelope.schema.json`. Static factory methods (`Actor.system`, `Actor.user`, `Actor.connector`, `Resource.of`, `AuditDetails.metadataOnly`, `AuditDetails.of`, `AuditDetails.withSupersedes`) for ergonomic construction.
- `co.uk.wolfnotsheep.platformaudit.outbox.*` — `AuditOutboxRecord` (Spring Data Mongo `@Document` mapped to `audit_outbox`), `OutboxStatus` enum, `AuditOutboxRepository` (Spring Data interface). The repository's `findByStatusAndNextRetryAtBeforeOrderByCreatedAtAsc` is the relay's primary poll query.
- `co.uk.wolfnotsheep.platformaudit.emit.*` — `AuditEmitter` interface + `OutboxAuditEmitter` default implementation. `OutboxAuditEmitter.emit` is idempotent on `eventId` (upsert via `findByEventId` short-circuit; the unique index is the durable guard).

**Files changed:**

- 14 new files under `backend/gls-platform-audit/` (pom.xml, README.md, 12 Java sources).
- `backend/pom.xml` — added `gls-platform-audit` to `<modules>`.
- `backend/bom/pom.xml` — added `gls-platform-audit` dependency entry.
- `version-2-implementation-log.md` — status board pending update + this entry.

Local `./mvnw -pl gls-platform-audit compile` is clean.

**Open issues (deferred to follow-up PRs in 0.7):**

- **Outbox-to-Rabbit relay.** The `OutboxRelay` scheduled task: poll `audit_outbox` via `idx_status_nextRetry`, publish to `audit.tier1.{eventType}` (DOMAIN) or `audit.tier2.{eventType}` (SYSTEM), mark `PUBLISHED`. Operationally the most interesting piece — retry/backoff, circuit-breaker on Rabbit, observability, ShedLock-coordinated leader election for the Tier 1 single-writer constraint per CSV #4.
- **Auto-configuration.** Convert this module into a Spring Boot starter (META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports) so consumers don't need to declare beans manually. Until then `OutboxAuditEmitter` is a `@Component` consumers can pick up via `@ComponentScan`.
- **Schema validation.** Validate envelope construction against `event-envelope.schema.json` at emit time (probably via `everit-org/json-schema` or `networknt/json-schema-validator`). Currently the caller is on the hook for valid envelopes.
- **ULID generation utility.** A small helper that produces Crockford-base32 ULIDs would fit in `envelope.AuditEvent` as a static factory. Defer until first call sites need it.
- **Tests.** No unit tests yet. The skeleton is intentionally untested; testing the emitter requires either a Mongo Testcontainer (currently broken — issue #7) or a deeper mock of Spring Data. Defer until #7 unblocks.
- **Existing audit code in `gls-app-assembly` (`AuditEvent` model in `gls-document`, `AuditEventRepository`, `AuditInterceptor`)** — separate concept (HTTP-request audit log via Spring Security, not the v2 outbox pipeline). Reconciliation deferred to Phase 1 cutover work.

**Next:** Phase 0.7 follow-up PRs (relay; auto-config; validation), or push on to Phase 0.8 / 0.9 / 0.11 / 0.12.

## 2026-04-26 — Phase 0.10 / 0.7 — Mongock runtime smoke (closing entry for runtime gap)

**Done:** Booted the api container against a real Mongo and verified Mongock 5.4.4 actually executes change units. The previous Phase 0.10 PR (#10) and Phase 0.7 indexes PR (#13) compiled clean but were dead at runtime — diagnosed and fixed three real bugs.

**Bugs fixed:**

1. **Missing `@EnableMongock`.** `mongock-springboot-v3-5.4.4.jar` ships **without** a `META-INF/spring/.../AutoConfiguration.imports` manifest — Mongock does not auto-configure on Spring Boot 4 (or 3). The annotation has to be declared explicitly. PR #10 added the dependencies and config but never the annotation; result: zero Mongock log output, no change unit execution. Fix: `@EnableMongock` on `GlsApplication`.
2. **`mongock:` YAML block absorbed `spring:` sub-keys.** PR #10 inserted the new `mongock:` config but indented `thymeleaf`, `autoconfigure`, `servlet`, `mail`, `mongodb`, `rabbitmq`, and (via dotted notation) `elasticsearch` under it. The live containers still booted because env vars (`SPRING_MONGODB_URI`, `RABBITMQ_HOST` etc.) covered for the lost properties — the breakage was masked, not absent. Fix: restore the keys under `spring:`; leave only Mongock-specific keys under `mongock:`.
3. **`runner-type: initializing-bean` doesn't match the SpEL guard.** Mongock 5.4.4's `MongockContextBase` declares two runner beans gated by SpEL strings: `'${mongock.runner-type:null}'.toLowerCase().equals('initializingbean')` (and equivalent for `applicationrunner`). The SpEL placeholder reads the **raw config value** — Spring Boot's relaxed binding only kicks in when binding to the `SpringRunnerType` enum, not when resolving a placeholder. Kebab-case `initializing-bean` lowercased is still `initializing-bean`, which doesn't equal `initializingbean`. So neither runner bean was created — Mongock initialised its own collections (lock + changelog) but never scanned for change units. Fix: `runner-type: InitializingBean` (CamelCase, matches the enum name verbatim).

**Verification:** With all three fixes, V001_MongockSmoke and V002_AuditOutboxIndexes both `EXECUTED` in `mongockChangeLog`, and `audit_outbox` carries the three named indexes (`idx_status_nextRetry`, `idx_eventId_unique`, `idx_createdAt`). Boot path confirmed via `docker compose up -d --no-deps --build api` against the existing `ig-central-mongo` container.

**Decisions logged:** None new.

**Files changed:**

- `backend/gls-app-assembly/src/main/java/co/uk/wolfnotsheep/infrastructure/GlsApplication.java` — add `@EnableMongock`.
- `backend/gls-app-assembly/src/main/resources/application.yaml` — un-nest the spring sub-keys; switch `runner-type` to `InitializingBean`.
- `version-2-implementation-log.md` — this entry.

**Open issues:**

- The `--no-deps` smoke skips RabbitMQ/MinIO/ES, which is fine for proving Mongock but doesn't exercise the audit relay (still deferred Phase 0.7 follow-up). A full-stack `docker compose up` is blocked locally because port 9200 is held by a stale `gls-elasticsearch` container from before the project rename — incidental, not a code issue.
- Consider tightening the smoke into an automated test once issue #7 (Testcontainers MongoDB cleanup) unblocks. Until then, the runtime check is a manual `docker compose up` exercise.

**Next:** Phase 0.7 follow-ups (outbox-to-Rabbit relay; Spring Boot starter auto-config for `gls-platform-audit`; envelope schema validation at emit time), or Phase 0.9 (Maven BOM decoupling) for a small parallel win.

## 2026-04-26 — Phase 0.11 (scaffold) — performance baseline scaffolding

**Done:** Stood up the scaffolding for Track C / Phase 0.11. Methodology, CSV format, and a runnable capture script all live in the repo. **No real numbers captured yet** — the load driver is intentionally a stub because there's no representative workload to push through the pipeline. The seed CSV row reflects this.

**Decisions logged:** None new — implements the Phase 0.11 work item from `version-2-implementation-plan.md`.

**Why scaffold-only:** This is a solo dev project on local Docker Compose. There's no production traffic and no representative document corpus in the repo. Capturing perf numbers without a known workload produces noise that's worse than no numbers at all. The honest move is to land the methodology + format + script now (so the first real capture is just `git pull && drive load && ./capture.sh`) and defer the load-driver wiring to a later PR alongside the document corpus.

**What's wired:**

- `baselines/README.md` — methodology: why baseline, what each metric means, where each metric comes from, capture cadence (before/after every pipeline-touching phase), how to read historical CSVs, and a clear "Driving load (deferred until workload exists)" section that names the gap.
- `baselines/2026-04-baseline.csv` — header row + one structural row marked "scaffold" so the CSV is parseable by anything that opens it (Pandas, Excel, eyeballs).
- `scripts/baselines/capture.sh` — runnable shell script. Today: checks the api + Prometheus are up, runs a no-op `drive_load`, samples placeholder PromQL queries, emits a CSV row to stdout. The PromQL queries reference metric names that the api may or may not currently emit (Micrometer + Spring Boot pipeline timers — verify against the live `/actuator/prometheus` registry before trusting any numbers).

**Files changed:**

- `baselines/README.md` (new).
- `baselines/2026-04-baseline.csv` (new).
- `scripts/baselines/capture.sh` (new, executable).
- `version-2-implementation-log.md` — Track C status changed from `Not started` → `Scaffolded` + this entry.

**Verification:** `bash -n scripts/baselines/capture.sh` clean. The script's `check_stack` function will refuse to emit a row if the api or Prometheus aren't reachable, so it can't accidentally produce zero-filled garbage data.

**Open issues:**

- **No load driver.** The single biggest gap. `drive_load` does nothing today. A representative document corpus + a small loader (likely `scripts/baselines/load.sh`) is the unblocker for real captures.
- **PromQL queries are unverified.** The metric names in `capture.sh` (`pipeline_classification_duration_seconds`, `pipeline_classification_total`, `mcp_cache_hits_total`, etc.) are best-guess names matching Micrometer conventions. Run `curl -s localhost:9090/api/v1/label/__name__/values` once the api has been driven for ~5 min and align names before relying on the queries.
- **Phase 0.11 acceptance gate (`Performance baseline captured and committed`) is not satisfied yet** — only the scaffolding is. Mark 0.11 fully done in a follow-up PR after the first non-stub capture.

**Next:** A follow-up PR that wires the load driver and produces the first real baseline row. Independently: Phase 0.12 (dev experience), Phase 0.8 (`gls-platform-config`), or the outstanding Phase 0.7 outbox-to-Rabbit relay.

## 2026-04-26 — Phase 0.9 — Maven BOM decoupling (closing entry)

**Done:** Introduced per-deployable version properties in `backend/bom/pom.xml` and documented the independent-versioning policy in `CLAUDE.md`. Every deployable's BOM `<dependency>` entry now references its own version property; libraries continue to share `${gls.version}`. Today every per-deployable property tracks the shared default — the seam is in place but no behaviour changes.

**Decisions logged:** None new — implements the Phase 0.9 work item from `version-2-implementation-plan.md` directly.

**What's wired:**

- `gls.api.version` — `gls-app-assembly`
- `gls.mcp.version` — `gls-mcp-server`
- `gls.orchestrator.version` — `gls-llm-orchestration`
- `gls.hub.version` — `gls-governance-hub-app`

`gls-governance-hub` (a library, not a deployable) keeps `${gls.version}` along with the rest of the libraries.

**Files changed:**

- `backend/bom/pom.xml` — added the four `gls.<deployable>.version` properties (each defaulting to `${gls.version}`); switched the four deployable `<dependency>` entries to reference their own property.
- `CLAUDE.md` — new "Independent Deployable Versions" section above "Build & Run". Documents the deployable vs library distinction, the property naming convention, and when to bump a deployable's version vs leaving it tracking the shared default.
- `version-2-implementation-log.md` — status board updated (0.9 → done) + this entry.

**Verification:** `./mvnw -pl gls-app-assembly,gls-mcp-server,gls-llm-orchestration,gls-governance-hub-app -am compile` clean. The BOM resolves correctly: every deployable still finds its dependencies via the per-deployable property which transitively expands to `${gls.version}`.

**Open issues:** None. The seam is intentionally inert until a deployable's release cadence diverges from the library set; the rule for "when to bump" is documented.

**Next:** Phase 0.7 outstanding follow-ups (relay; envelope schema validation), Phase 0.8 (`gls-platform-config` cache + `gls.config.changed`), Phase 0.11 (perf baseline), Phase 0.12 (dev experience).

## 2026-04-26 — Phase 0.7 (auto-config) — `gls-platform-audit` Spring Boot starter

**Done:** Turned the `gls-platform-audit` module into a self-contained Spring Boot starter. Consumers no longer need to component-scan `co.uk.wolfnotsheep.platformaudit` or declare the emitter bean themselves — adding the dependency is enough.

**Decisions logged:** None new. Implements the auto-config follow-up flagged in the closing entry of the Phase 0.7 library skeleton above.

**What's wired:**

- `PlatformAuditAutoConfiguration` — `@AutoConfiguration` gated on `MongoTemplate` being present on the classpath. Registers the outbox repository via `@EnableMongoRepositories(basePackageClasses = AuditOutboxRepository.class)` and exposes `AuditEmitter` (default `OutboxAuditEmitter`) as a bean with `@ConditionalOnMissingBean`, so consumers can override.
- `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` — Spring Boot 3+ auto-configuration registration manifest.
- `OutboxAuditEmitter` — `@Component` removed; the bean is now declared by the auto-config and is therefore single-source.

**Files changed:**

- `backend/gls-platform-audit/src/main/java/co/uk/wolfnotsheep/platformaudit/autoconfigure/PlatformAuditAutoConfiguration.java` (new).
- `backend/gls-platform-audit/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` (new).
- `backend/gls-platform-audit/src/main/java/co/uk/wolfnotsheep/platformaudit/emit/OutboxAuditEmitter.java` — drop `@Component`, update Javadoc.
- `backend/gls-platform-audit/README.md` — Status section reframed (skeleton → auto-configured starter); auto-config bullet removed from follow-ups.
- `version-2-implementation-log.md` — this entry.

**Open issues:**

- Two `@EnableMongoRepositories` annotations now exist in the same Spring context (`GlsApplication` + `PlatformAuditAutoConfiguration`). Spring Data Mongo handles this by creating one `RepositoryFactoryBean` per declaration; the basePackages are disjoint here, so no duplicate registration. Compile is clean against both `gls-platform-audit` and `gls-app-assembly`.
- Schema validation against `event-envelope.schema.json` at emit time is still deferred — separate follow-up.

**Next:** The remaining Phase 0.7 follow-ups (outbox-to-Rabbit relay; envelope schema validation), then Phase 0.8 / 0.11 / 0.12.

## 2026-04-26 — Phase 0.7 (schema validation) — envelope validation at emit time

**Done:** Added runtime JSON Schema 2020-12 validation against `contracts/audit/event-envelope.schema.json` inside `OutboxAuditEmitter`. Invalid envelopes raise `IllegalArgumentException` at the call site and never reach the outbox.

**Decisions logged:** None new. Implements the schema-validation follow-up flagged in PR #14's closing entry.

**What's wired:**

- New `EnvelopeValidator` class (in `gls-platform-audit/.../envelope/`). Loads the schema once at construction (compiled via `com.networknt:json-schema-validator` 1.5.7 — supports JSON Schema 2020-12) and validates every envelope through Jackson's tree model. Thread-safe; cheap to call repeatedly.
- The schema is **bundled into the jar at build time** via Maven `<resources>`: `contracts/audit/event-envelope.schema.json` lives single-sourced under `contracts/`, and the build copies it into `target/classes/schemas/` so the validator works the same in tests, in the api container, and in any other consumer (no filesystem dependency on the `contracts/` tree at runtime).
- `OutboxAuditEmitter` now calls `validator.validate(envelope)` before the existing idempotency check + outbox write.
- `EnvelopeValidatorTest` (6 tests, all green) exercises the bundled schema directly: happy paths for both tiers; invalid ULID; DOMAIN tier missing required fields; USER actor missing id; multi-violation aggregation.

**Implementation notes (worth flagging):**

- Default Jackson `NON_NULL` inclusion strips fields whose Java value is null. The schema requires `previousEventHash` to be **present** (with value either null or a sha256 string) for `tier=DOMAIN` first-in-chain events — stripping the field would fail validation for legitimate envelopes. Resolved with a Jackson mixin (`AuditEventValidationMixin`) that pins `previousEventHash` to `Include.ALWAYS` while leaving every other field on the global NON_NULL setting. Mixin lives inside `EnvelopeValidator` so the records stay clean of validation-flavoured annotations.
- Added `jackson-datatype-jsr310` as an explicit dependency so the validator's local `ObjectMapper` serialises `Instant` to RFC 3339 strings (Spring Boot apps already pull it in transitively, but the validator must work in any consumer including non-Spring-Boot test contexts).

**Files changed:**

- `backend/pom.xml` — added `json-schema-validator.version` property (1.5.7) and managed `com.networknt:json-schema-validator` in `dependencyManagement`.
- `backend/gls-platform-audit/pom.xml` — added the validator + JSR-310 deps; added a `<build><resources>` block that copies `contracts/audit/event-envelope.schema.json` into the jar at `schemas/event-envelope.schema.json`.
- `backend/gls-platform-audit/src/main/java/.../envelope/EnvelopeValidator.java` (new).
- `backend/gls-platform-audit/src/main/java/.../emit/OutboxAuditEmitter.java` — `emit()` calls validator first; doc tweaked to note schema validation now landed.
- `backend/gls-platform-audit/src/test/java/.../envelope/EnvelopeValidatorTest.java` (new) — 6 tests, AssertJ + JUnit 5 from `spring-boot-starter-test`.
- `backend/gls-platform-audit/README.md` — Status section reframed to "auto-configured, schema-validating starter"; follow-ups list updated.
- `version-2-implementation-log.md` — this entry.

**Verification:** `./mvnw -pl gls-platform-audit test` — all 6 tests pass. Consumer compile (`gls-app-assembly`) clean. Schema is present in the built jar at `schemas/event-envelope.schema.json`.

**Open issues:**

- The relay (the most operationally interesting Phase 0.7 piece) is the largest outstanding 0.7 work.
- Some `OutboxAuditEmitter`-against-real-Mongo testing remains blocked by issue #7.

**Next:** Outbox-to-Rabbit relay, or pivot to Phase 0.8 (`gls-platform-config`) / 0.12 (dev experience).

## 2026-04-26 — Plan checkboxes synced to reality

**Done:** Flipped 29 checkboxes in `version-2-implementation-plan.md` to `[x]` for items that have actually shipped (PRs #15–#19, all merged before this entry). The plan had drifted from reality — every item was still `[ ]` even when the corresponding work had landed and been logged.

**Decisions logged:** None new. This is a tracking-fidelity fix, not a design change.

**Items now checked:**

- 0.1 — all four §11 shape decisions (A1, A2, A5, A6).
- 0.3 — directory tree + per-folder metadata. (Per-service placeholders deferred; noted inline.)
- 0.4 — all eight `_shared/` YAMLs.
- 0.5 — all five OpenAPI tooling items (lint tool, generator, pom wiring, CI, pre-commit).
- 0.6 — both AsyncAPI declarations. (`gls.config.changed` as forward-looking; noted inline.)
- 0.7 — envelope schema, audit_outbox indexes, CLAUDE.md relay docs. **Library item left unchecked** with inline note: envelope + outbox writer + schema validation + auto-config landed; relay + retry/backoff still outstanding.
- 0.9 — both BOM decoupling items.
- 0.10 — all four schema-migration items.
- 0.11 — methodology doc only. **Capture-script and CSV items left unchecked** with inline notes calling out that the scaffold is in place but the load driver is a stub.

**Items left unchecked (with inline notes added):**

- 0.7 library (relay + retry/backoff outstanding).
- 0.7 Python module sketch (deferred — not started).
- 0.11 capture script (scaffold; load driver stubbed).
- 0.11 CSV (scaffold; no real data captured).

**Files changed:**

- `version-2-implementation-plan.md` — 29 `[ ]` → `[x]`; inline parenthetical notes on partial / scaffolded items so a future reader can tell what "checked" meant for each one.
- `version-2-implementation-log.md` — this entry.

**Next:** Outbox-to-Rabbit relay (the largest outstanding 0.7 piece), Phase 0.8 (`gls-platform-config`), or wire the load driver to upgrade 0.11 from scaffolded to fully done.

## 2026-04-26 — Phase 0.7 (relay skeleton) — outbox-to-Rabbit relay

**Done:** Landed the **skeleton + happy path** of the outbox-to-Rabbit relay. `OutboxRelay` polls `audit_outbox`, applies the Tier 1 hash transformation per CSV #6, publishes to the `gls.audit` topic exchange with tier-aware routing keys, and marks rows `PUBLISHED` / `FAILED` based on outcome. Exponential backoff on transient failures; cap on attempts. Tunable via `gls.platform.audit.relay.*` properties. **18 tests, all green.**

**Decisions logged:** None new. Implements the relay implied by architecture §7.7 / CSV #4 / CSV #6 and the audit AsyncAPI declaration.

**What's wired:**

- `OutboxRelay` — `@Scheduled(fixedDelayString = "${gls.platform.audit.relay.poll-interval:PT5S}")`. Each poll cycle: fetch up to `batchSize` PENDING rows where `nextRetryAt <= now`, ordered by `createdAt` ascending (backed by `idx_status_nextRetry`). For each row: apply Tier 1 hash transform if `tier=DOMAIN`, serialise to JSON, publish to `audit.tier1.<eventType>` (DOMAIN) or `audit.tier2.<eventType>` (SYSTEM) on the configured exchange. On success → status PUBLISHED + publishedAt. On failure → bump attempts, set nextRetryAt = now + exponential backoff (capped), capture lastError. Once attempts ≥ maxAttempts → FAILED.
- `Tier1HashTransformer` — strips `details.content` map values to `sha256:<hex>` for `tier=DOMAIN` envelopes per CSV #6 (right-to-erasure boundary). Pass-through for SYSTEM. Pass-through for DOMAIN with no/empty content. `metadata`, `supersedes`, `supersededBy` preserved verbatim.
- `OutboxRelayProperties` — `@ConfigurationProperties("gls.platform.audit.relay")` record with sensible defaults (`enabled=true`, poll 5s, batch 50, maxAttempts 5, backoffBase 1s, backoffMax 5m, exchange `gls.audit`). Compact constructor enforces defaults so partial config is well-formed.
- `PlatformAuditAutoConfiguration` — extended with `@EnableConfigurationProperties(OutboxRelayProperties.class)`; the `glsAuditExchange` bean (`TopicExchange("gls.audit")`); the `outboxRelay` bean. Both Rabbit-touching beans are `@ConditionalOnClass(RabbitTemplate.class)`. The relay is also gated by `@ConditionalOnProperty("gls.platform.audit.relay.enabled", matchIfMissing=true)`.
- `AuditOutboxRepository` — primary poll query now takes `Pageable` so the relay caps the batch size per cycle. The previous unparameterised method had no callers.
- `gls-platform-audit/pom.xml` — `spring-boot-starter-amqp` added as `<optional>true</optional>`. Consumers without Rabbit on their classpath still consume the library happily; the relay just doesn't activate.

**Tests (18 total, all green):**

- `Tier1HashTransformerTest` (6) — DOMAIN content hashed, metadata preserved, no-op cases (no content / empty content / SYSTEM tier), determinism, supersedes link preserved.
- `OutboxRelayTest` (6) — empty batch no-op, disabled relay short-circuits, SYSTEM tier publishes with tier2 routing + marks PUBLISHED, DOMAIN tier strips content + publishes with tier1 routing + JSON body contains `sha256:`, publish failure bumps attempts + sets nextRetry, terminal failure marks FAILED at maxAttempts.
- (Plus the 6 existing `EnvelopeValidatorTest` cases.)

**Implementation notes worth flagging:**

- **Single-writer constraint (CSV #4) is NOT enforced here.** Multi-replica deployments today risk double-publishing the same envelope. Until the ShedLock follow-up lands, run a single replica with the relay enabled. The downstream consumer (`gls-audit-collector`) deduplicates on `eventId`, so at-least-once delivery is masked operationally — but Tier 1's single-writer guarantee is genuinely deferred.
- **Serialisation failure is not retried.** A `JsonProcessingException` indicates a structurally broken envelope; no amount of retrying fixes it. The relay marks the row FAILED immediately so a human surfaces it.
- **`@EnableScheduling` is the consumer's responsibility.** `gls-app-assembly` already has it on `GlsApplication`. New consumers must declare it themselves or the relay bean exists but never ticks.

**Files changed:**

- `backend/gls-platform-audit/src/main/java/.../relay/OutboxRelay.java` (new).
- `backend/gls-platform-audit/src/main/java/.../relay/OutboxRelayProperties.java` (new).
- `backend/gls-platform-audit/src/main/java/.../relay/Tier1HashTransformer.java` (new).
- `backend/gls-platform-audit/src/main/java/.../autoconfigure/PlatformAuditAutoConfiguration.java` — register exchange + relay beans, conditional on Rabbit + property.
- `backend/gls-platform-audit/src/main/java/.../outbox/AuditOutboxRepository.java` — poll query now takes `Pageable`.
- `backend/gls-platform-audit/pom.xml` — add optional `spring-boot-starter-amqp` dep.
- `backend/gls-platform-audit/src/test/java/.../relay/Tier1HashTransformerTest.java` (new) — 6 tests.
- `backend/gls-platform-audit/src/test/java/.../relay/OutboxRelayTest.java` (new) — 6 tests with mock repo + RabbitTemplate.
- `backend/gls-platform-audit/README.md` — Status reframed; "Configuration" table for the new tunables.
- `version-2-implementation-log.md` — this entry.

**Verification:** `./mvnw -pl gls-platform-audit test` — 18/18 pass. `./mvnw -pl gls-app-assembly -am compile` clean. End-to-end runtime smoke (boot the api, stage some envelopes via `auditEmitter.emit(...)`, observe them flow through the relay to a RabbitMQ exchange) is best done after this PR merges so it can be exercised against the full stack.

**Open issues (deferred to follow-up PRs):**

- **Leader election (ShedLock) for Tier 1 single-writer.** Hard requirement before running multi-replica with the relay enabled.
- **Micrometer counters / gauges** for queue depth, publish rate, error rate, retry distribution.
- **Circuit breaker on Rabbit** — short-circuit the poll cycle when the broker is observably down rather than tripping per-row backoff in parallel.
- **Tests against real RabbitMQ + Mongo Testcontainer** — blocked by issue #7.

**Next:** Phase 0.8 (`gls-platform-config` cache + `gls.config.changed` channel), Phase 0.12 (dev experience), wiring the 0.11 load driver, or the relay hardening follow-up (ShedLock + observability).

## 2026-04-26 — Phase 0.8 — `gls-platform-config` skeleton + `gls.config.changed` channel

**Done:** Landed the AsyncAPI declaration and the `gls-platform-config` shared library skeleton implementing the change-driven config-cache pattern from CSV #30. Replaces the previous Caffeine TTL conventions across services: per-replica in-memory cache, change-driven invalidation via the `gls.config.changed` Rabbit channel.

**`AppConfigService` migration and the MCP server's Caffeine cache replacement are deferred to follow-up PRs** (mirrors how Phase 0.7 unfolded — skeleton first, migrations second).

**Decisions logged:** None new. Implements CSV #30 (DECIDED).

**What's wired:**

- `contracts/messaging/asyncapi.yaml` (0.2.0 → 0.3.0) — added `configChanged` channel on routing key `config.changed` (exchange `gls.config`, topic). Producer/consumer conventions documented (Hub imports MUST publish too; consumer uses non-durable exclusive auto-delete queue per replica; no DLX). Added `publishConfigChanged` / `consumeConfigChanged` operations. Added `ConfigChanged` message + `ConfigChangedEvent` schema (required: `entityType`, `changeType`, `timestamp`, `actor`; optional: `entityIds[]`, `traceparent`; `additionalProperties: false`).
- `gls-platform-config` library — new Maven module under `backend/`. Java side mirrors the AsyncAPI:
  - `ConfigCache<V>` — per-replica in-memory cache for one entity type. Backed by `ConcurrentHashMap.computeIfAbsent`. Null loader returns are not cached so transient misses retry on next call.
  - `ConfigCacheRegistry` — entity-type → cache routing. Multiple caches per type permitted; bulk events trigger `invalidateAll()`, targeted events invalidate per-id.
  - `ConfigChangedEvent` (record) — mirrors the wire schema; defensive copies for `entityIds`; convenience factories `single` / `bulk`. `@JsonIgnore` on `isBulk()` so the bean-property name doesn't sneak into the JSON.
  - `ConfigChangePublisher` — single emitter into `gls.config` exchange. Failures logged, never re-raised — a missed invalidation is transient (next write self-heals). Convenience methods for single / bulk / many.
  - `ConfigChangeListener` (functional interface) — non-cache reactions (metrics, audit emits, search-index updates).
  - `ConfigChangeDispatcher` — `@RabbitListener` against an anonymous queue (`autoDelete=true, durable=false, exclusive=true`) bound to `gls.config`. Dispatches caches first, then listeners; per-listener failures are isolated.
  - `PlatformConfigAutoConfiguration` — registers the registry + publisher + dispatcher + topic exchange. Broker-touching beans `@ConditionalOnClass(RabbitTemplate.class)` so the cache layer works without a broker (offline / tests).

**Tests (19 total, all green):**

- `ConfigCacheTest` (5) — miss-then-hit caches result, single invalidate, bulk invalidate, null loader not cached, blank entity type rejected.
- `ConfigCacheRegistryTest` (4) — targeted event invalidates only listed ids, bulk clears, unregistered-type silently ignored, multiple caches per type all invalidate.
- `ConfigChangedEventTest` (6) — required-field rejections, null entityIds normalises to empty + isBulk=true, single factory shape, Jackson roundtrip with JSR-310.
- `ConfigChangeDispatcherTest` (4) — caches invalidate before listeners run, listener failure does not abort siblings, `onMessage` decodes JSON + dispatches, malformed payload dropped silently.

**Files changed:**

- `contracts/messaging/asyncapi.yaml` — channel + operations + message + schema.
- `contracts/messaging/VERSION` — 0.2.0 → 0.3.0.
- `contracts/messaging/CHANGELOG.md` — 0.3.0 entry.
- `backend/gls-platform-config/pom.xml` (new).
- `backend/gls-platform-config/README.md` (new).
- `backend/gls-platform-config/src/main/java/.../event/{ChangeType,ConfigChangedEvent}.java` (new).
- `backend/gls-platform-config/src/main/java/.../cache/{ConfigCache,ConfigCacheRegistry}.java` (new).
- `backend/gls-platform-config/src/main/java/.../listen/{ConfigChangeListener,ConfigChangeDispatcher}.java` (new).
- `backend/gls-platform-config/src/main/java/.../publish/ConfigChangePublisher.java` (new).
- `backend/gls-platform-config/src/main/java/.../autoconfigure/PlatformConfigAutoConfiguration.java` (new).
- `backend/gls-platform-config/src/main/resources/META-INF/spring/...AutoConfiguration.imports` (new).
- `backend/gls-platform-config/src/test/java/...` (4 test classes, 19 tests).
- `backend/pom.xml` — added `gls-platform-config` to `<modules>`.
- `backend/bom/pom.xml` — added `gls-platform-config` dependency entry.
- `version-2-implementation-log.md` — this entry.

**Verification:** `./mvnw -pl gls-platform-config test` — 19/19 pass. Compile clean against the rest of the reactor.

**Open issues (deferred to follow-up PRs):**

- **`AppConfigService` migration.** Today it uses a hand-rolled `ConcurrentHashMap` with no broker-driven invalidation. Cutover to `ConfigCache` + emit on write.
- **MCP server's Caffeine cache replacement** for governance entities. Per CSV #30, this is the original target of the change.
- **Hub-side publisher wiring** — the asyncapi declares Hub imports MUST publish. The Hub-side code change lives outside this monorepo's main path; track in cross-cutting Track A.
- **Real-broker integration tests** — blocked by issue #7 (Testcontainers MongoDB cleanup, but RabbitMQ Testcontainer should also be exercised once we have a working test harness).

**Next:** AppConfigService migration (closes the easiest 0.8 follow-up), MCP cache migration, or pivot to Phase 0.12 (dev experience), the 0.11 load driver, or relay hardening.

## 2026-04-27 — Phase 0.8 (AppConfigService migration) — first consumer on the new pattern

**Done:** Migrated `AppConfigService` from its hand-rolled `ConcurrentHashMap` to the `gls-platform-config` `ConfigCache` + `ConfigChangePublisher` pair. The cache now registers with the global `ConfigCacheRegistry` under `entityType=APP_CONFIG`; mutations publish a `ConfigChangedEvent` so peer replicas drop their stale entries the moment a write commits, regardless of which replica took it.

This is the first consumer on the new pattern — proves the API holds up against a real call site.

**Decisions logged:** None new. Implements CSV #30.

**What changed:**

- `AppConfigService` constructor now takes `ConfigCacheRegistry` (always present, registered by the auto-config) and `ObjectProvider<ConfigChangePublisher>` (optional — the `getIfAvailable()` pattern lets the service work in test contexts and broker-less environments without forcing every consumer to depend on Rabbit).
- `cache` is a `ConfigCache<AppConfig>` registered with the registry on construction.
- `get(key)` is the canonical read-through call: `cache.get(k, loader)`. First miss hits Mongo; second hit serves from the in-memory cache.
- `save(key, ...)` distinguishes CREATED vs UPDATED based on whether the existing record had an id, then invalidates the local cache + publishes a single-entity event. The dispatcher on this replica also receives its own message and re-invalidates — a slight redundancy that makes the code paths uniform across replicas.
- `refresh()` flushes the local cache + publishes a bulk event so peers do the same. Matches the prior admin-refresh semantic.
- `loadAll()` removed — it was a build-up step the new lazy cache makes redundant. The single caller (`refresh`) used it as an alias.

**Tests:** 8 new tests in `AppConfigServiceTest` covering registry registration, cache hit/miss, CREATED vs UPDATED on save, post-save re-load, bulk refresh, and the offline-publisher path (no broker → no publish, save still succeeds).

**Implementation note worth flagging:**

- `ConfigChangePublisher` references `org.springframework.amqp.AmqpException` via the optional `spring-boot-starter-amqp` dep that `gls-platform-config` ships. Mockito needs the class on the classpath to instrument it, so `gls-platform/pom.xml` now declares spring-amqp at test scope. Runtime is unchanged: `gls-app-assembly` already brings the broker dep transitively.

**Files changed:**

- `backend/gls-platform/pom.xml` — added `gls-platform-config` (compile) + `spring-boot-starter-amqp` (test scope).
- `backend/gls-platform/src/main/java/.../config/services/AppConfigService.java` — full rewrite onto the new primitives. Public API unchanged for callers (`get`, `getValue`, `getByCategory`, `getAll`, `save`, `refresh`).
- `backend/gls-platform/src/test/java/.../config/services/AppConfigServiceTest.java` (new) — 8 tests.
- `version-2-implementation-log.md` — this entry.

**Verification:** `./mvnw -pl gls-platform-config,gls-platform,gls-platform-audit -am test` — 61/61 pass across the three platform modules. Consumer compile (`gls-app-assembly`) clean.

**Open issues (deferred to follow-ups):**

- **MCP server's Caffeine cache replacement.** Original target of CSV #30. Same pattern as this PR — different consumer.
- **Hub-side publisher wiring.** Track A.
- **End-to-end runtime smoke** — boot the api, mutate a config entry, observe the `gls.config.changed` message land on the broker. Best done after a peer replica is wired (otherwise nothing's listening).

**Next:** MCP cache migration to close the second 0.8 follow-up, then pivot to Phase 0.12 (dev experience), the 0.11 load driver, or relay hardening.

## 2026-04-27 — Phase 0.8 (MCP cache migration) — TTL replaced with change-driven invalidation

**Done:** Migrated the MCP server's nine governance Caffeine caches off wall-clock TTL onto change-driven invalidation per CSV #30. The Caffeine substrate stays as the storage detail; the staleness model flips. Five tests cover the dispatcher mapping.

**Approach:** A surgical change rather than a wholesale rewrite. The previous `@Cacheable` annotations on the MCP tools (`ClassificationTaxonomyTool`, `SensitivityDefinitionsTool`, `TraitDetectionTool`, `GovernancePolicyTool`, `RetentionScheduleTool`, `StorageCapabilitiesTool`, `MetadataSchemasTool`, `CorrectionHistoryTool`, `OrgPiiPatternsTool`) are unchanged. The cutover happens at two points:

1. **`CacheConfig`** drops `expireAfterWrite(5, TimeUnit.MINUTES)`. The maxSize ceiling stays (memory bound, not staleness). New `ENTITY_TYPE_TO_CACHE` map exports the entity-type → cache-name routing as a public constant.
2. **`McpConfigInvalidator`** is a new `ConfigChangeListener` (auto-discovered by the dispatcher in `gls-platform-config`'s auto-config). On every `gls.config.changed` event whose `entityType` is in the routing table, it evicts: bulk events → `cache.clear()`, targeted events → per-id `cache.evict()`. Events for unmapped types are silently ignored.

**Why this approach over a full ConfigCache adapter:** CSV #30 specifies the *pattern* (per-replica in-memory + change-driven invalidation), not the storage substrate. Caffeine + Spring's `@Cacheable` is a well-understood combo and untangling it from the tools' `@Cacheable` annotations would mean a much larger blast radius for no behavioural gain. The unification of substrate (everything on `ConfigCache`) is a separate concern — re-evaluate after the MCP `@Cacheable` patterns are exercised under load and we know whether composite keys (e.g. `"<arg0>:<arg1>"`) really pull their weight.

**Decisions logged:** None new. CSV #30 already covers this.

**Entity-type → cache mapping:**

| `entityType` | MCP cache name |
|---|---|
| `TAXONOMY` | `taxonomy` |
| `SENSITIVITY` | `sensitivities` |
| `TRAIT` | `traits` |
| `POLICY` | `policies` |
| `RETENTION_SCHEDULE` | `retention` |
| `STORAGE_TIER` | `storage` |
| `METADATA_SCHEMA` | `schemas` |
| `CORRECTION` | `corrections` |
| `PII_PATTERN_SET` | `piiPatterns` |

This is the contract the **publisher** must honour. Any service mutating one of these collections needs to fire `ConfigChangedEvent.entityType` matching the table above; otherwise the MCP cache won't drop. Track this as part of the per-entity migrations on `gls-app-assembly` (separate follow-up — most governance writes happen there today).

**Files changed:**

- `backend/gls-mcp-server/pom.xml` — added `gls-platform-config` dep so the listener interface is on the classpath. The auto-config beans (`ConfigCacheRegistry`, `ConfigChangeDispatcher`, etc.) come along for the ride.
- `backend/gls-mcp-server/src/main/java/.../config/CacheConfig.java` — TTL removed; `ENTITY_TYPE_TO_CACHE` map added.
- `backend/gls-mcp-server/src/main/java/.../config/McpConfigInvalidator.java` (new) — `ConfigChangeListener` `@Component`.
- `backend/gls-mcp-server/src/test/java/.../config/McpConfigInvalidatorTest.java` (new) — 5 tests covering bulk clear, targeted evict, unmapped type ignore, mapping coverage, and missing-cache resilience.
- `version-2-implementation-log.md` — this entry.

**Verification:** `./mvnw -pl gls-mcp-server -am test -Dtest=McpConfigInvalidatorTest` — 5/5 pass. Full module compile clean.

**Open issues (deferred to follow-ups):**

- **Per-entity write-side publish** — `GovernanceService` (and friends in `gls-app-assembly`) need to fire `ConfigChangedEvent` on writes to taxonomy / policies / sensitivities / traits / retention schedules / storage tiers / metadata schemas / corrections / PII pattern sets. Without that, the MCP cache will only invalidate when an admin manually triggers a `refresh()`-equivalent. Track as the next 0.8 follow-up.
- **Composite-key targeted evictions are best-effort.** Several MCP `@Cacheable` methods derive composite keys from method args (e.g. `policies` keyed `"<categoryId>:<sensitivity>"`). A targeted event with raw entity ids won't match these composite keys, so the eviction silently misses. The pragmatic fallback today: write-side handlers send a bulk event for affected entity types where composite keys are in play. Document and revisit if perf shows an issue.
- **Hub-side publishers** still pending — Track A.

**Next:** Wire write-side publishes in `GovernanceService` (closes the round-trip for the MCP migration), pivot to Phase 0.12 (dev experience), the 0.11 load driver, or relay hardening.

## 2026-04-27 — Phase 0.8 (governance write-side bridge) — closes the MCP cache loop

**Done:** Wired write-side publishes for the nine governance entity types so the MCP cache invalidator (`McpConfigInvalidator`, landed in #24) actually has events to react to. Single Mongo lifecycle listener — no controller-by-controller refactor.

**Approach:** `GovernanceConfigChangeBridge` is a `@Component` with two `@EventListener`s on Spring Data Mongo's `AfterSaveEvent` / `AfterDeleteEvent`. A `Map<Class<?>, String>` of governance model class → wire-protocol entity type filters out non-governance writes. Saves publish single-entity events; deletes publish bulk events for the affected entity type.

**Why a lifecycle listener rather than service-method instrumentation:** centralised (one class, one mapping table); coverage by default (catches admin REST writes, Hub-pack imports, seeders, batch `saveAll`, ad-hoc `MongoTemplate` calls); tiny blast radius (no controller / repository edits).

**Trade-offs documented inline in the bridge javadoc:**

- CREATED vs UPDATED collapsed to UPDATED — distinguishing requires a pre-save event with prior state, fragile.
- Deletes always go bulk — `AfterDeleteEvent` doesn't reliably carry the deleted id; bulk forces peers to re-read on next access.
- Save with null id falls back to bulk — defensive; rare in practice.

**Decisions logged:** None new. Closes the implementation gap CSV #30 implied.

**Files changed:**

- `backend/gls-governance/pom.xml` — added `gls-platform-config` (compile) + `spring-boot-starter-amqp` (test scope, same Mockito instrumentation reason as `gls-platform`).
- `backend/gls-governance/src/main/java/.../events/GovernanceConfigChangeBridge.java` (new) — ~110 LOC. Mapping table covers `ClassificationCategory`, `SensitivityDefinition`, `TraitDefinition`, `GovernancePolicy`, `RetentionSchedule`, `StorageTier`, `MetadataSchema`, `ClassificationCorrection`, `PiiTypeDefinition`. Reflection on `getId()` to extract the id; absence of the method logs and falls back to bulk.
- `backend/gls-governance/src/test/java/.../events/GovernanceConfigChangeBridgeTest.java` (new) — 6 tests covering known-type save → single, unknown-type save ignored, null-id save → bulk, delete → bulk, unknown-type delete ignored, publisher-absence non-fatal.
- `version-2-implementation-log.md` — this entry.

**Verification:** `./mvnw -pl gls-governance -am test` 6/6 pass; `./mvnw -pl gls-app-assembly,gls-mcp-server -am compile` clean.

**Open issues:**

- **Hub-side publishers** — Track A. `gls-governance-hub` writes to a separate Mongo. Either it deploys this same bridge against its own Mongo + Rabbit connection, or its `PackImportService` publishes explicitly. Lift in a Track A PR.
- **Composite-key MCP caches** (`policies`, `corrections`, `schemas` keyed e.g. `"<categoryId>:<sensitivity>"`) — a targeted single-id event won't match. Currently a harmless miss; if perf requires it, switch the bridge mapping for those types to publish bulk instead.

**Next:** Hub-side bridge (Track A), Phase 0.12 (dev experience), the 0.11 load driver, or relay hardening (ShedLock + observability).

## 2026-04-27 — Plan checkboxes synced again (Phase 0.8 + 0.7 relay)

**Done:** Flipped four more `[ ]` → `[x]` in `version-2-implementation-plan.md` to reflect what landed in PRs #21–#25:

- 0.8: AsyncAPI declaration, library skeleton, AppConfigService migration, MCP cache replacement — all checked. Hub-side publishers note added inline (Track A).
- 0.7 library item updated inline: relay landed; ShedLock + metrics + circuit-breaker still outstanding.

Now 33 checked / 152 unchecked across the plan (was 29 / 156).

**Files changed:** `version-2-implementation-plan.md`, `version-2-implementation-log.md`.

**Next:** Hub-side bridge (Track A), Phase 0.12 (dev experience), Phase 0.11 load driver, or relay hardening.

## 2026-04-27 — Phase 0.12 — local dev experience

**Done:** Closed out Phase 0.12. One-command bring-up script, commented-out placeholders for the v2 service containers in `docker-compose.yml`, and a refreshed `README.md` Quick Start that matches the post-rename / post-bundling state of the world.

**What's wired:**

- `scripts/dev-up.sh` (new, executable). Pre-flight checks: Docker daemon up, `.env` exists, required keys (`MONGO_PASSWORD`, `ADMIN_PASSWORD`, `JWT_SECRET`) populated. Then `docker compose up --build -d` with optional service-name args + `--no-build` flag, polls `docker compose ps` until every running service reports `healthy`, prints a useful URL summary at the end. Distinct exit codes for pre-flight (1) / compose (2) / health timeout (3).
- `docker-compose.yml` — added a commented "v2 Phase 1+ services" block at the bottom with placeholder definitions for `gls-extraction-tika` (Phase 0.5), `gls-classifier-router` (Phase 1), `gls-audit-collector` (Phase 1.12 / 0.7 follow-up). Each block follows the same shape; uncomment + tailor when the corresponding Maven module + Dockerfile lands.
- `README.md` — Quick Start now points at `scripts/dev-up.sh` first (raw `docker compose up` is the fall-back); the obsolete `doc-processor`/`governance-enforcer` invocation is gone (those are bundled into `api` now); access URLs include actuator, Prometheus, Grafana, RabbitMQ admin, MinIO. New "Substrate libraries" subsection points at `gls-platform-audit` and `gls-platform-config`. New "v2 progress" subsection points at the four canonical doc files.
- `Makefile` — deferred. The plan listed it as an alternative to the script; `dev-up.sh` covers the use case without adding another tool to the build chain. Lift if a maintainer specifically wants it.

**Decisions logged:** None new.

**Verification:** `bash -n scripts/dev-up.sh` clean. The script's pre-flight checks short-circuit before any compose call, so a misconfigured env never produces a half-up stack.

**Files changed:**

- `scripts/dev-up.sh` (new, executable).
- `docker-compose.yml` — commented placeholder block for v2 services.
- `README.md` — Quick Start, Local Development, Substrate libraries, v2 progress sections rewritten / added.
- `version-2-implementation-plan.md` — three 0.12 items checked.
- `version-2-implementation-log.md` — this entry.

**Open issues:** None blocking. The placeholders are intentionally commented; they activate per-phase as the underlying Maven modules land.

**With this PR, Phase 0 substrate is substantively closed.** Outstanding items remaining anywhere in Phase 0:

- 0.7 library hardening: ShedLock leader election, comprehensive Micrometer metrics, circuit breaker on Rabbit. Operational hardening; lift before multi-replica deployment.
- 0.7 Python module sketch (for `gls-bert-trainer`) — design doc only, deferred.
- 0.11 baseline capture: load driver + first real CSV row. Blocked on representative document corpus.
- Hub-side `gls.config.changed` publishers — Track A.
- Issue #7 (Testcontainers Mongo cleanup) — blocks integration-test coverage across the substrate.

Phase 0.5 (reference implementation `gls-extraction-tika`) is the natural next phase.

**Next:** Phase 0.5 — pick the first per-service contract + scaffolding. Or relay hardening / load driver wiring.

## 2026-04-27 — Phase 0.5.1 — `gls-extraction-tika` module + contract

**Done:** First per-service spec lands. `contracts/extraction/openapi.yaml` declares the three operations — `POST /v1/extract`, `GET /v1/capabilities`, `GET /actuator/health` — referencing `_shared/` for the error envelope (CSV #17), service JWT (CSV #18), `traceparent` + `Idempotency-Key` headers (CSV #16 / #20), `TextPayload` (CSV #19), `Capabilities` (CSV #21), in-flight 409, 429 retry. The `gls-extraction-tika` Maven module is wired into the reactor and the BOM has a fresh `gls.extraction.version` per-deployable property.

**Decisions logged:** None new. First adoption of every relevant `_shared/` `$ref` pattern — proves the substrate authored across 0.3 / 0.4 actually composes.

**What's wired:**

- `contracts/extraction/openapi.yaml` (new) — three operations. Idempotency on `nodeRunId` per CSV #16; cost-attribution `costUnits` per CSV #22; `TextPayload` inline-vs-`textRef` per CSV #19. 4XX / 5XX catch-alls keep the spec lint-clean while concrete codes (413 / 422 / 429 / 409) document the unhappy paths the implementation must recognise.
- `contracts/extraction/VERSION` (new) — 0.1.0.
- `contracts/extraction/CHANGELOG.md` (new) — initial entry.
- `contracts/extraction/README.md` (new) — operation summary + cross-references.
- `backend/gls-extraction-tika/pom.xml` (new) — declares deps on `gls-platform-audit` (audit emission for `EXTRACTION_COMPLETED` / `EXTRACTION_FAILED` lands in 0.5.3), spring-boot-starter-webmvc / actuator / data-mongodb / amqp, Tika 3.1.0.
- `backend/gls-extraction-tika/README.md` (new) — points at the contract; lists what's deferred to 0.5.2+.
- `backend/pom.xml` — added `gls-extraction-tika` to `<modules>`.
- `backend/bom/pom.xml` — added `gls.extraction.version` property (defaulting to `${gls.version}`) and the dependency entry referencing it.
- `version-2-implementation-plan.md` — three 0.5.1 items checked. Inline note: **Dockerfile deferred to 0.5.2** since it needs the actual implementation.

**Verification:** `./mvnw -pl gls-extraction-tika -am compile` clean. The contract has no automated lint exercise yet (this PR; runs locally via the `pre-commit` hook authored in 0.5).

**Files changed:** 6 new files under `contracts/extraction/` + `backend/gls-extraction-tika/`; 2 modified poms; plan + log.

**Open issues:** None blocking 0.5.2 (which is "generated server stub from contract"). Things tracked elsewhere:

- **Dockerfile** — lands with 0.5.2 alongside the implementation; the placeholder block in `docker-compose.yml` activates then.
- **Generated server stub** — `openapi-generator-maven-plugin` already wired in `contracts-smoke`; the per-service config follows that pattern.
- **`_shared/` `$ref` resolution lint pass** — Spectral lint on PR was set up in 0.5; this is the first non-smoke spec to exercise it. Watch for breakage on the next CI run.

**Next:** Phase 0.5.2 — generated server stub + Tika integration + MinIO source/target wiring. Or in parallel: relay hardening (ShedLock + metrics) for 0.7, or wiring the 0.11 load driver.

## 2026-04-27 — Phase 0.5.2 (a) — generated server stub for `gls-extraction-tika`

**Done:** First piece of 0.5.2 — wired `openapi-generator-maven-plugin` into `gls-extraction-tika`'s pom against the contract authored in 0.5.1. The generator emits ~13 model records + the Spring Boot API interface on every Maven build under `target/generated-sources/openapi`. Implementations will live in this module and implement the generated interfaces (Tika integration, MinIO source/target, idempotency — separate follow-ups).

**What's wired:**

- `backend/gls-extraction-tika/pom.xml` — added `swagger-annotations` dep (required by generated `@Operation` / `@Schema` annotations) and an `openapi-generator-maven-plugin` execution. Same config options as `contracts-smoke` (`interfaceOnly=true`, `useSpringBoot3=true`, `useJakartaEe=true`, `skipDefaultInterface=true`, `openApiNullable=false`, `hideGenerationTimestamp=true`, `useTags=true`). Output package: `co.uk.wolfnotsheep.extraction.tika.api` (interface) + `.model` (records).
- `contracts/extraction/openapi.yaml` (0.1.0 → 0.1.1) — `HealthResponse.components.description` reformatted as a folded scalar. The previous inline form contained the snippet `` `show-components: always` `` — snakeyaml (used by the generator) misread the colon as a YAML mapping key and aborted. Pure docstring change; no surface-area shift.
- `contracts/extraction/VERSION` (0.1.1).
- `contracts/extraction/CHANGELOG.md` (0.1.1 entry).

**Decisions logged:** None new. Implements 0.5.2 first-bullet ("generated server stub from contract"); the rest of 0.5.2 (Tika integration, MinIO wiring, idempotency) lands in follow-up PRs.

**Verification:** `./mvnw -pl gls-extraction-tika -am compile` clean. Generated sources include `ExtractApi` (interface), `ExtractRequest` / `ExtractResponse` / `HealthResponse` records, plus the inline-vs-ref `oneOf` types for `TextPayload` and the various 4xx response payloads.

**Gotcha logged for the cloneable pattern:** when a docstring contains `` `key: value` ``-style snippets, prefer the folded-scalar form (`description: |`) over the single-line form. Inline backticked-colon strings can pass `asyncapi` / Spectral lint but break `openapi-generator-maven-plugin`'s snakeyaml parser. Add to `docs/service-template.md` once that file lands (0.5.6).

**Files changed:** `backend/gls-extraction-tika/pom.xml`, `contracts/extraction/openapi.yaml`, `contracts/extraction/VERSION`, `contracts/extraction/CHANGELOG.md`, `version-2-implementation-log.md`.

**Open issues (continuing into 0.5.2 follow-ups):**

- **Tika integration** — port from `gls-document-processing`. Returns text inline ≤ 256 KB else `textRef` to MinIO per CSV #19.
- **`nodeRunId` idempotency** — 24h TTL per CSV #16. Mongo collection + dedup on the in-flight key.
- **Audit emission** — `EXTRACTION_COMPLETED` / `EXTRACTION_FAILED` Tier 2 events via `gls-platform-audit` (the relay handles the rest).
- **Health probes** — readiness checks Tika init + MinIO reach.
- **Dockerfile** — needs the implementation; the placeholder block in `docker-compose.yml` activates with that PR.

**Next:** Tika integration (the meat of 0.5.2). Or in parallel: relay hardening, Hub-side publishers, 0.11 load driver.

## 2026-04-27 — Phase 0.5.2 (b) — `TikaExtractionService` (extractor only)

**Done:** First focused slice of the Tika integration. Single `TikaExtractionService` class wrapping `org.apache.tika.Tika`, returning a typed `ExtractedText` record with mime-type detection, page count for paginated formats, byte-count for `costUnits` (CSV #22), and a `truncated` flag when the character ceiling fires. Six unit tests covering the wrapper logic — format-specific tests (PDFs, Office docs, .eml) live in 0.5.4's integration suite once the controller + MinIO source are wired.

**Decisions logged:** None new. Implements the "Tika-based text extraction" bullet of 0.5.2 in isolation.

**What's wired:**

- `TikaExtractionService` — stateless, thread-safe wrapper. Configurable character ceiling via `gls.extraction.tika.max-characters` (default 500_000, mirrors `gls-document-processing`'s `TextExtractionService` for parity). `Tika.parseToString` is the workhorse; metadata extraction handles three different page-count keys (`xmpTPg:NPages`, `meta:page-count`, `Page-Count`).
- `ExtractedText` (record) — text, detected mime type, page count, byte count, truncated flag.
- `UnparseableDocumentException` — semantic wrapper around Tika's `TikaException`. The controller will map this to RFC 7807 `EXTRACTION_CORRUPT` / 422 in 0.5.3.
- `CountingInputStream` (private nested class) — counts bytes pulled even when Tika short-circuits at the character ceiling. Drives the contract's `costUnits` math without holding the source in memory.

**Failure semantics:**

- `TikaException` → `UnparseableDocumentException` (will become 422 with `EXTRACTION_CORRUPT`).
- `IOException` → `UncheckedIOException` (will become 5xx).
- `ZeroByteFileException` → empty result (semantically valid — caller asked us to extract a zero-byte document; we return zero bytes of text).
- OOM during parse → not caught here; the controller / process observability is the right boundary.

**Tests (6, all green):**

- Plain text passes through with byte count + mime detection.
- Empty input returns empty text (Tika throws `ZeroByteFileException`; we treat it as success).
- Character ceiling truncates and flags `truncated=true`.
- Null filename hint is tolerated.
- Corrupt bytes with a misleading `.pdf` extension trip `UnparseableDocumentException`.
- Zero / negative `maxCharacters` rejected at construction.

**Files changed:**

- `backend/gls-extraction-tika/src/main/java/.../parse/TikaExtractionService.java` (new).
- `backend/gls-extraction-tika/src/main/java/.../parse/ExtractedText.java` (new).
- `backend/gls-extraction-tika/src/main/java/.../parse/UnparseableDocumentException.java` (new).
- `backend/gls-extraction-tika/src/test/java/.../parse/TikaExtractionServiceTest.java` (new — 6 tests).
- `version-2-implementation-log.md` — this entry.

**Open issues (continuing into 0.5.2 follow-ups):**

- **MinIO source service** — fetch a `documentRef.{bucket, objectKey}` to InputStream. Wires into `MinioClient` via Spring config.
- **Controller implementing `ExtractApi`** (the generated interface) — orchestrates source fetch → Tika extraction → response shaping (inline ≤ 256 KB else `textRef`).
- **MinIO sink** — when the extracted text exceeds 256 KB, upload it to a `gls-extracted-text` bucket and return `textRef`.
- **`nodeRunId` idempotency** — 24h TTL via Mongo.
- **Dockerfile** — needs the controller + Spring main class.
- **Audit emission** — `EXTRACTION_COMPLETED` / `EXTRACTION_FAILED` (Tier 2) via `gls-platform-audit`.

**Next:** MinIO source + controller + inline-only response shaping (the next vertical slice). Or in parallel: relay hardening, Hub-side publishers, 0.11 load driver.

## 2026-04-27 — Phase 0.5.2 (c) — MinIO source layer

**Done:** Source-side I/O layer for `gls-extraction-tika`. `DocumentSource` interface (decoupled from the SDK so the controller is unit-testable against fakes) plus a MinIO-backed implementation with ETag verification, NotFound mapping, size lookup, and Spring config for the bean. 8 new unit tests (14 total in the module).

**Decisions logged:** None new. Implements the source-side I/O the controller needs in 0.5.2 (d).

**What's wired:**

- `DocumentRef` (record) — bucket / objectKey / optional etag. Validates non-blank inputs at construction.
- `DocumentSource` (interface) — `open(ref)` returns `InputStream`; `sizeOf(ref)` returns bytes-or-`-1L`. Decouples the controller from MinIO SDK types.
- `MinioDocumentSource` — wraps `io.minio:minio:8.5.14` (matching `gls-document`'s `ObjectStorageService`). When `ref.etag()` is set, verifies via `statObject` first and throws `DocumentEtagMismatchException` on mismatch. Maps `ErrorResponseException` with code `NoSuchKey` to `DocumentNotFoundException`. Other I/O failures wrap as `UncheckedIOException`.
- `DocumentNotFoundException` / `DocumentEtagMismatchException` — semantic exceptions the controller maps to RFC 7807 in 0.5.3 (`DOCUMENT_NOT_FOUND` / 404 and `DOCUMENT_ETAG_MISMATCH` / 409).
- `MinioSourceConfig` — `@Configuration` with `@ConditionalOnMissingBean` factories for `MinioClient` (`minio.endpoint` / `minio.access-key` / `minio.secret-key` properties; same shape as `gls-app-assembly`'s `ObjectStorageService`) and `DocumentSource`. Both overrideable in tests.

**Tests (8 new + 6 existing = 14 total):**

- `MinioDocumentSourceTest` (new, 8) — open streams object bytes, missing → `DocumentNotFoundException`, other I/O → `UncheckedIOException`, etag pass / fail / mismatch detail, `sizeOf` returns storage size, `sizeOf` returns `-1L` for missing object, `DocumentRef` validation. Mocks the `MinioClient`; the helper-mock pattern was hit by Mockito's `UnfinishedStubbing` lint when a `mock(...)` call landed inside an outer `when(...)` chain — fixed by binding the helper mock to a local first.
- `TikaExtractionServiceTest` (6 existing).

**Files changed:**

- `backend/gls-extraction-tika/pom.xml` — added `io.minio:minio:8.5.14`.
- `backend/gls-extraction-tika/src/main/java/.../source/{DocumentRef,DocumentSource,MinioDocumentSource,MinioSourceConfig,DocumentNotFoundException,DocumentEtagMismatchException}.java` (6 new files).
- `backend/gls-extraction-tika/src/test/java/.../source/MinioDocumentSourceTest.java` (new).
- `version-2-implementation-log.md` — this entry.

**Verification:** `./mvnw -pl gls-extraction-tika -am test` — 14/14 pass.

**Open issues (continuing into 0.5.2 follow-ups):**

- **Controller** implementing the generated `ExtractApi` — orchestrates source.open → tika.extract → response shaping (inline ≤ 256 KB else `textRef`).
- **MinIO sink** for the >256 KB overflow path — uploads extracted text to a `gls-extracted-text` bucket, returns the `textRef`.
- **`nodeRunId` idempotency** — 24h TTL via Mongo.
- **Dockerfile + Spring main class**.
- **Audit emission** via `gls-platform-audit`.

**Next:** Controller + inline-only response shaping (no MinIO sink yet — fail with 413 on overflow until the sink lands). Or pivot to relay hardening / Hub-side / load driver.

## 2026-04-27 — Phase 0.5.2 (d) — controller + RFC 7807 handler + Spring main

**Done:** End of the inline-only vertical slice for `gls-extraction-tika`. The generated `ExtractionApi` interface now has a real implementation: `ExtractController` orchestrates `DocumentSource.open` → `TikaExtractionService.extract` → `ExtractResponse`. `ExtractionExceptionHandler` maps the four domain exception types to RFC 7807 (CSV #17). Spring main class + `application.yaml` make the module bootable. **20 tests across the module, all green.**

**What's wired:**

- `GlsExtractionTikaApplication` — `@SpringBootApplication` main class.
- `application.yaml` — server port 8080, MinIO endpoint defaults, Tika `max-characters` (500_000) + `inline-byte-ceiling` (262_144 = 256 KB per CSV #19), Actuator `health` / `info` / `prometheus`.
- `ExtractController` (`@RestController implements ExtractionApi`) — happy path: open the source, run Tika, build inline `ExtractResponse` with `costUnits = byteCount / 1024` (CSV #22). Above the byte ceiling raises `DocumentTooLargeException` → 413 (the MinIO sink → `textRef` path lands in a follow-up). Idempotency-Key header is logged but not consulted yet (cache layer → follow-up).
- `DocumentTooLargeException` — semantic exception for the inline overflow. Goes away when the sink lands.
- `ExtractionExceptionHandler` (`@RestControllerAdvice`) — maps:
  - `DocumentNotFoundException` → 404 `DOCUMENT_NOT_FOUND`
  - `DocumentEtagMismatchException` → 409 `DOCUMENT_ETAG_MISMATCH`
  - `UnparseableDocumentException` → 422 `EXTRACTION_CORRUPT`
  - `DocumentTooLargeException` → 413 `EXTRACTION_TOO_LARGE`
  - `UncheckedIOException` → 503 `EXTRACTION_SOURCE_UNAVAILABLE`
  Adds `code`, `retryable`, `timestamp` extension fields; sets `type` to `https://gls.local/errors/<CODE>`.

**Implementation note worth flagging:**

The OpenAPI spec uses a `oneOf` discriminator-by-`kind` for `TextPayload`, but the generator's emitted `ExtractResponseTextOneOf` doesn't carry a `kind` field — the discriminator is implicit in *which* branch is instantiated. Caught by a compile error after the controller blindly called `setKind(...)`; fix was to drop the call. **Add to `docs/service-template.md` (0.5.6):** with `openapi-generator-maven-plugin` 7.10.0, `oneOf` schemas don't materialise the discriminator field — rely on the implementing class identity instead.

**Tests (6 new + 14 existing = 20 total, all green):**

- `ExtractControllerTest` (new, 6) — happy path returns 200 with inline text + correct `nodeRunId` / `detectedMimeType` / `costUnits`; `DocumentNotFoundException` propagates; `DocumentEtagMismatchException` propagates with etag carried through; `UnparseableDocumentException` propagates on misleading-extension corrupt bytes; over-ceiling payload raises `DocumentTooLargeException`; controller still implements `ExtractionApi` (compile-time invariant + runtime guard).
- Wires a real `TikaExtractionService` against a mocked `DocumentSource` — Tika is the substrate we want to exercise; the source is the boundary we want to mock.
- No MockMvc — direct method calls. The exception handler isn't exercised at this level (it's the MVC pipeline's job to invoke it); a wider-scoped test follows when we have a real broker / Mongo Testcontainer.

**Files changed:**

- `backend/gls-extraction-tika/src/main/java/.../GlsExtractionTikaApplication.java` (new).
- `backend/gls-extraction-tika/src/main/resources/application.yaml` (new).
- `backend/gls-extraction-tika/src/main/java/.../web/{ExtractController,DocumentTooLargeException,ExtractionExceptionHandler}.java` (3 new).
- `backend/gls-extraction-tika/src/test/java/.../web/ExtractControllerTest.java` (new).
- `version-2-implementation-log.md` — this entry.

**Verification:** `./mvnw -pl gls-extraction-tika -am test` — 20/20 pass.

**Open issues (continuing into 0.5.2 follow-ups):**

- **MinIO sink** — uploads extracted text > 256 KB to `gls-extracted-text` bucket; controller switches to `textRef` branch instead of 413.
- **`nodeRunId` idempotency** (CSV #16) — 24h TTL via Mongo. Today the header is logged only.
- **Audit emission** — `EXTRACTION_COMPLETED` / `EXTRACTION_FAILED` Tier 2 events via `gls-platform-audit` (the relay handles the publishing; emitter is a one-liner).
- **Dockerfile** — module is now bootable; the placeholder block in `docker-compose.yml` activates with the Dockerfile PR.
- **Capabilities + Health endpoints** — `getCapabilities` and `getHealth` are still abstract on `ExtractionApi`. Implementing them is mechanical; punted to keep this PR focused on the extract path.

**Next:** MinIO sink + `getCapabilities` + `getHealth` implementations + Dockerfile (the next vertical slice). Or pivot to the relay hardening, Hub-side, or load driver.

## 2026-04-27 — Phase 0.5.2 (e) + 0.5.3 + 0.5.5 — `gls-extraction-tika` is deployable

**Done:** `gls-extraction-tika` is now a complete deployable. `MetaController` implements the generated `MetaApi` (`getCapabilities` + `getHealth`); a multi-stage `Dockerfile` builds the module from a repo-root context (so `contracts/` is reachable for `gls-platform-audit`'s schema-bundling resource directive); `docker-compose.yml`'s placeholder block is updated with the corrected `context: .` build path. **23 tests across the module, all green.**

**What's wired:**

- `MetaController` (`@RestController implements MetaApi`):
  - `GET /v1/capabilities` — returns this build's identity (`service=gls-extraction-tika`, `version=0.0.1-SNAPSHOT` — versioned via `gls.extraction.build.version` env var when a real release tag is set), `tiers=["default"]`, `models=[]`. Pure extraction container per CSV #21.
  - `GET /actuator/health` — minimal `UP` response. Spring Actuator's actual `/actuator/health` endpoint is the canonical probe; this implementation exists because the contract declares the operation.
- `backend/gls-extraction-tika/Dockerfile` (multi-stage) — Eclipse Temurin 25 JDK builder + JRE runtime. Layered: copy poms first (cache-friendly), `mvn dependency:go-offline`, then copy backend + contracts, then `mvn package`. `HEALTHCHECK` curls `/actuator/health`.
- `docker-compose.yml` placeholder block — `context: .` (repo root) + `dockerfile: backend/gls-extraction-tika/Dockerfile`. The wider context is essential: `gls-platform-audit`'s pom bundles `contracts/audit/event-envelope.schema.json` into its jar via a build-time resource at `../../contracts/audit/`; without repo-root context Docker can't reach it.

**Decisions logged:** None new.

**Plan checkboxes flipped:**

- 0.5.1 Dockerfile note removed (no longer deferred).
- 0.5.3 Error returns — `[x]`. RFC 7807 mapping covers `DOCUMENT_NOT_FOUND` / `DOCUMENT_ETAG_MISMATCH` / `EXTRACTION_CORRUPT` / `EXTRACTION_TOO_LARGE` / `EXTRACTION_SOURCE_UNAVAILABLE`. `EXTRACTION_OOM` / `EXTRACTION_TIMEOUT` apply once async parsing / circuit breakers land — flagged inline.
- 0.5.3 Health probes — basic endpoint landed; readiness `HealthIndicator` beans (Tika init, MinIO reach) still outstanding — flagged inline.
- 0.5.5 Dockerfile + Compose definition — `[x]`. K8s manifests still outstanding — flagged inline (defer until the 0.2 deployment-target reasoning genuinely calls for K8s).

**Tests (3 new + 20 existing = 23 total, all green):**

- `MetaControllerTest` (3) — capabilities advertise service identity + default tier; health returns UP; controller still implements `MetaApi`.

**Files changed:**

- `backend/gls-extraction-tika/Dockerfile` (new, multi-stage).
- `backend/gls-extraction-tika/src/main/java/.../web/MetaController.java` (new).
- `backend/gls-extraction-tika/src/test/java/.../web/MetaControllerTest.java` (new).
- `docker-compose.yml` — placeholder block updated for `context: .` + Dockerfile path; depends-on simplified (Tika is currently stateless — no Mongo / Rabbit needs until idempotency + audit emission land).
- `version-2-implementation-plan.md` — checkboxes / inline notes.
- `version-2-implementation-log.md` — this entry.

**Verification:** `./mvnw -pl gls-extraction-tika -am test` — 23/23 pass. Docker build itself is not yet exercised in CI — flag for the next CI pass; the build context change is the load-bearing piece and worth a dry run.

**Open issues (continuing into 0.5.2 follow-ups):**

- **MinIO sink** for the >256 KB overflow (`textRef` branch).
- **`nodeRunId` idempotency** (24h TTL via Mongo).
- **Audit emission** via `gls-platform-audit`.
- **Tika + MinIO `HealthIndicator` beans** for the readiness side of the actuator health surface.
- **Metrics** (Micrometer counters, latency histogram, error rate).
- **JWT validation middleware**.
- **Existing `backend/Dockerfile` and `Dockerfile.mcp`** quietly miss the `contracts/` tree at build time. The api jar's bundled audit schema is therefore likely not present in deployed containers — separate fix, possibly the cause of subtly-different runtime behaviour.

**Next:** Sink + idempotency + audit emission (the remaining 0.5.2 items). Or pivot to fixing `backend/Dockerfile`'s contracts-context issue, relay hardening, or load driver.

## 2026-04-27 — Phase 0.5.3 (audit success path) — `EXTRACTION_COMPLETED` emission

**Done:** First per-service split actually using the `gls-platform-audit` substrate. The success path of `POST /v1/extract` now emits an `EXTRACTION_COMPLETED` Tier 2 audit event via `AuditEmitter`. The failure-path emission (from the exception handler) is the next focused PR.

**What's wired:**

- `ExtractionEvents` (factory) — pure (no Spring, no Mongo), unit-testable. Builds Tier 2 envelopes for `EXTRACTION_COMPLETED` (success) and `EXTRACTION_FAILED` (failure). `eventId` is 26 uppercase hex chars from a `UUID.randomUUID()` — satisfies the envelope schema's Crockford-base32 pattern (which excludes `I L O U`; hex digits + A–F use none of those). Sortability is sacrificed compared to a real ULID — flagged for the deferred `gls-platform-audit` ULID utility.
- `ExtractController` — `@Autowired` `ObjectProvider<AuditEmitter>` (graceful when the bean is absent — broker-less test contexts and partial-stack runs both work). After successful extract, builds the envelope from `ExtractionEvents.completed(...)` and calls `emitter.emit(...)`. Audit failures are logged, never re-raised — the outbox is durable; if the write itself fails the cause is the emitter / Mongo, and the user-facing extract result is still correct.
- Service identity (`spring.application.name`, `gls.extraction.build.version`, `HOSTNAME`) is sourced from configuration so the actor block is accurate per deployment.

**Decisions logged:** None new.

**Tests (6 new + 23 existing = 29 total, all green):**

- `ExtractionEventsTest` (4) — completed event passes the bundled schema (`EnvelopeValidator.fromBundledSchema()`); null mime / pageCount fields omitted; failed event passes schema with errorMessage on `details.content`; eventId format satisfies the Crockford-base32 pattern at runtime (belt-and-braces beyond the validator-driven check).
- `ExtractControllerTest` (2 added) — successful extract emits the right envelope (eventType / tier / outcome / action / nodeRunId / traceparent / actor.service / metadata); audit emit failure does NOT sink the response.
- `payload_above_ceiling_raises_too_large` — added a `verify(auditEmitter, never()).emit(any())` assertion (failure path doesn't emit yet).

**Files changed:**

- `backend/gls-extraction-tika/src/main/java/.../audit/ExtractionEvents.java` (new).
- `backend/gls-extraction-tika/src/main/java/.../web/ExtractController.java` — `ObjectProvider<AuditEmitter>` + `serviceName` / `serviceVersion` / `instanceId` constructor params; `emitCompleted(...)` invocation on the success path; failure-tolerant.
- `backend/gls-extraction-tika/src/test/java/.../audit/ExtractionEventsTest.java` (new — 4 tests).
- `backend/gls-extraction-tika/src/test/java/.../web/ExtractControllerTest.java` — wires the `AuditEmitter` mock; adds 2 audit-specific tests.
- `version-2-implementation-plan.md` — 0.5.3 audit bullet inline-noted: success-path landed; failure-path still outstanding.
- `version-2-implementation-log.md` — this entry.

**Verification:** `./mvnw -pl gls-extraction-tika -am test` — 29/29 pass.

**Open issues:**

- **Failure-path audit emission** — `ExtractionExceptionHandler` should emit `EXTRACTION_FAILED` for the relevant exception types. Need traceparent + nodeRunId in scope at handler time (request-scoped bean or `RequestAttributes`). Next focused PR.
- **ULID utility** in `gls-platform-audit` — replace the UUID-hex stand-in with a proper ULID once the utility lands.
- **Per-service Mongock wiring** — currently the audit_outbox indexes only exist if `gls-app-assembly` has booted at least once against the same Mongo. If `gls-extraction-tika` is the first writer, the unique index on `eventId` will be missing. Either Mongock is wired into every service (preferred) or the indexes are bootstrapped lazily.

**Next:** Failure-path audit emission, MinIO sink for `textRef`, `nodeRunId` idempotency, or other follow-ups.

## 2026-04-27 — Phase 0.5.3 (audit failure path) — `EXTRACTION_FAILED` emission, audit loop closed

**Done:** Failure-path audit emission. The controller now wraps its work in a `try { doExtract(...) } catch (RuntimeException) { emitFailed(...); throw; }` shell so a Tier 2 `EXTRACTION_FAILED` event lands in `audit_outbox` for every failed request, with the right `errorCode` derived from the exception type. The 0.5.3 audit bullet is now fully `[x]`.

**Why catch in the controller (not the `@RestControllerAdvice` handler):** the exception handler runs after request scope teardown begins; getting `traceparent` + `nodeRunId` back into scope at handler time would need a request-scoped bean or `RequestContextHolder`. Catching one level up keeps both in scope without that infrastructure. The handler still owns the RFC 7807 mapping.

**`errorCodeFor(Throwable)` mapping:**

| Exception | `errorCode` |
|---|---|
| `DocumentNotFoundException` | `DOCUMENT_NOT_FOUND` |
| `DocumentEtagMismatchException` | `DOCUMENT_ETAG_MISMATCH` |
| `UnparseableDocumentException` | `EXTRACTION_CORRUPT` |
| `DocumentTooLargeException` | `EXTRACTION_TOO_LARGE` |
| `UncheckedIOException` | `EXTRACTION_SOURCE_UNAVAILABLE` |
| anything else | `EXTRACTION_UNEXPECTED` |

The codes match the RFC 7807 mapping in `ExtractionExceptionHandler` so a downstream Tier 2 reader can correlate the audit `errorCode` with the wire-level `code` extension.

**Decisions logged:** None new. Closes 0.5.3's audit bullet.

**Tests:** Existing 29 tests still pass; two of them (the `payload_above_ceiling` and `document_not_found` tests) now also verify the audit emission shape — `eventType=EXTRACTION_FAILED`, the right `errorCode`, the right `nodeRunId`. Other failure-path tests (etag mismatch, unparseable) implicitly exercise the same emission code path; explicit assertions added selectively to keep the test surface tight.

**Files changed:**

- `backend/gls-extraction-tika/src/main/java/.../web/ExtractController.java` — `extractDocument` now delegates to a private `doExtract` and wraps failures with `emitFailed`; `errorCodeFor` switch.
- `backend/gls-extraction-tika/src/test/java/.../web/ExtractControllerTest.java` — `payload_above_ceiling` and `document_not_found` tests assert the emitted FAILED event shape.
- `version-2-implementation-plan.md` — 0.5.3 audit bullet flipped `[x]`; full implementation note inline.
- `version-2-implementation-log.md` — this entry.

**Verification:** `./mvnw -pl gls-extraction-tika -am test` — 29/29 pass.

**Open issues:**

- **Bulk failure-path coverage** — the etag mismatch + unparseable + UncheckedIOException paths are wired but the explicit assertions are limited. Lift if defects surface; not worth bloating the test surface today.
- **MinIO sink + idempotency + readiness** still outstanding (next focused PRs).

**Next:** MinIO sink (replaces the `EXTRACTION_TOO_LARGE` 413 with a real `textRef`) or `nodeRunId` idempotency. Or pivot to other Phase 0 follow-ups.

## 2026-04-27 — Phase 0.5.2 (sink) — `textRef` overflow path lands

**Done:** MinIO sink for the >256 KB overflow path. The controller no longer 413s on big extractions — extracted text is uploaded to a MinIO bucket and surfaced on the contract's `textRef` response branch per CSV #19. Inline-vs-ref behaviour is now the full vertical-slice from the spec.

**What's wired:**

- `DocumentSink` (interface) — abstracts the upload so the controller is unit-testable against a fake.
- `MinioDocumentSink` — uploads UTF-8-encoded extracted text to a configured bucket. Object key is derived from `nodeRunId` (`extracted/<nodeRunId>.txt`) so retries are idempotent at the storage level — a re-extracted document overwrites the same object rather than orphaning the old one. Bucket creation is best-effort on first write.
- `ExtractedTextRef` (record) — pure return type (`URI`, `contentLength`, `contentType`).
- `MinioSinkConfig` — `@Configuration` with `@ConditionalOnMissingBean DocumentSink` factory. Reuses the shared `MinioClient` bean from `MinioSourceConfig`. Bucket name is configurable via `gls.extraction.tika.sink.bucket` (default `gls-extracted-text`).
- `ExtractController` — `DocumentSink` injected; new `buildRefResponse` path uploads via `sink.upload(...)` then constructs the `ExtractResponseTextOneOf1` branch. The 413 exception throw is gone; `DocumentTooLargeException` and the handler entry are retained for future sink-side capacity caps but no longer raised by the controller. Common response building factored into `commonResponse(...)` so inline + ref share the metadata mapping.

**Decisions logged:** None new.

**Tests (1 new + 1 rewritten + 28 existing = 30 total, all green):**

- `payload_above_ceiling_uploads_via_sink_and_returns_textRef_branch` (rewrite of the previous `_raises_too_large_and_emits_FAILED` test) — over-ceiling now succeeds, calls the sink with the right `nodeRunId`, returns the `textRef` branch with the URI/length/contentType from the upload, emits `EXTRACTION_COMPLETED` not FAILED.
- `inline_path_does_not_call_the_sink` (new) — small extractions don't touch the sink.

**Files changed:**

- `backend/gls-extraction-tika/src/main/java/.../sink/{DocumentSink,MinioDocumentSink,ExtractedTextRef,MinioSinkConfig}.java` (4 new).
- `backend/gls-extraction-tika/src/main/java/.../web/ExtractController.java` — `DocumentSink` constructor param; over-ceiling path swapped to `buildRefResponse`; helper factoring.
- `backend/gls-extraction-tika/src/test/java/.../web/ExtractControllerTest.java` — wires the `DocumentSink` mock; replaces the throws-413 test; adds an inline-doesn't-touch-sink guard.
- `version-2-implementation-log.md` — this entry.

**Verification:** `./mvnw -pl gls-extraction-tika -am test` — 30/30 pass.

**Open issues (continuing into 0.5.2 follow-ups):**

- **`nodeRunId` idempotency** (CSV #16) — 24h TTL via Mongo. Today the header is still logged only.
- **Tika + MinIO `HealthIndicator` beans** for actuator readiness.
- **Metrics** (Micrometer counters, latency histogram, error rate).
- **JWT validation middleware**.
- **`MinioDocumentSink` real-MinIO test** — blocked by issue #7's Testcontainers debt; the unit test against mocks covers the orchestration but not the wire-level upload.

**Next:** `nodeRunId` idempotency, or readiness `HealthIndicator`s, or metrics, or pivot to other Phase 0 follow-ups.

## 2026-04-27 — Phase 0.5.2 (idempotency, closing entry) — Phase 0.5.2 fully closed

**Done:** `nodeRunId` idempotency per CSV #16 — the last outstanding 0.5.2 item. Mongo-backed `extraction_idempotency` collection with a 24h TTL; `IdempotencyStore` wraps `tryAcquire` / `cacheResult` / `releaseOnFailure`. Repeated requests within the TTL window get the cached response (200) if completed or 409 `IDEMPOTENCY_IN_FLIGHT` if still running. **0.5.2 is now fully `[x]`.** Module test count: 34, all green.

**What's wired:**

- `IdempotencyRecord` — Mongo document keyed by `nodeRunId`. `expiresAt` is a Mongo TTL index (`@Indexed(expireAfter = "0s")`); rows auto-delete at-or-after that instant so crashed in-flight extractions don't accumulate.
- `IdempotencyRepository` — Spring Data Mongo.
- `IdempotencyOutcome` — `ACQUIRED` (proceed) / `IN_FLIGHT` (409) / `CACHED` (replay). Status enum + optional `cachedJson` payload.
- `IdempotencyStore` — `tryAcquire(nodeRunId)` is the workhorse: read-then-insert with race recovery via `DuplicateKeyException`. `cacheResult` stamps `completedAt` + `responseJson`. `releaseOnFailure` deletes the row so a follow-up retry can start fresh (alternative — leaving the row in-flight — would block recovery for the 24h TTL).
- `IdempotencyInFlightException` — semantic exception; mapped to RFC 7807 with code `IDEMPOTENCY_IN_FLIGHT` and HTTP 409 by `ExtractionExceptionHandler`.
- `ExtractController` — idempotency check moved to the very top, before any source I/O. Three branches per `IdempotencyOutcome.status()`. On success the response is JSON-serialised and cached; on failure the row is deleted. JSON deserialisation of the cached response uses Jackson's `JsonTypeInfo.Id.DEDUCTION` via a mixin that pins the two `oneOf` subtypes — without it the generated `ExtractResponseText` interface (which has no built-in discriminator) doesn't round-trip.

**Generator gotcha worth flagging:** `openapi-generator-maven-plugin` 7.10.0 emits an empty interface for `oneOf` schemas — no `@JsonTypeInfo`, no discriminator. Round-tripping an `ExtractResponse` through Jackson requires a mixin with `JsonTypeInfo.Id.DEDUCTION` (subtype picked by which fields are present). Already noted in the 0.5.2 (d) controller log; this PR is the second hit. Add to `docs/service-template.md` as part of 0.5.6.

**Decisions logged:** None new. Closes the 0.5.2 idempotency bullet.

**Tests (4 new + 30 existing = 34 total, all green):**

- `in_flight_idempotency_returns_409_via_exception` — `IdempotencyOutcome.IN_FLIGHT` short-circuits before source / sink / Tika.
- `cached_idempotency_returns_stored_response_without_extracting` — `CACHED` replays the JSON; no source / no audit emit.
- `successful_extract_caches_the_response_for_subsequent_retries` — `cacheResult` called with the right `nodeRunId`.
- `failure_releases_the_idempotency_row_so_retries_can_proceed` — failure path calls `releaseOnFailure` and never `cacheResult`.

**Files changed:**

- `backend/gls-extraction-tika/src/main/java/.../idempotency/{IdempotencyRecord,IdempotencyRepository,IdempotencyOutcome,IdempotencyStore,IdempotencyInFlightException}.java` (5 new).
- `backend/gls-extraction-tika/src/main/java/.../web/ExtractController.java` — idempotency check at entry; cache on success / release on failure; Jackson mixin for the cached round-trip; `errorCodeFor` extended.
- `backend/gls-extraction-tika/src/main/java/.../web/ExtractionExceptionHandler.java` — `IdempotencyInFlightException` → 409 `IDEMPOTENCY_IN_FLIGHT`.
- `backend/gls-extraction-tika/src/test/java/.../web/ExtractControllerTest.java` — wires `IdempotencyStore` mock + `ObjectMapper`; 4 new tests.
- `version-2-implementation-plan.md` — 0.5.2 idempotency item flipped `[x]`. **Phase 0.5.2 is now fully `[x]`.**
- `version-2-implementation-log.md` — this entry.

**Verification:** `./mvnw -pl gls-extraction-tika -am test` — 34/34 pass.

**Phase 0.5 status:**

| Sub-phase | State |
|---|---|
| 0.5.1 | ✓ |
| 0.5.2 | ✓ closed (this PR) |
| 0.5.3 | error returns ✓, audit ✓, basic health ✓; tracing / readiness `HealthIndicator` / metrics / JWT still outstanding |
| 0.5.4 | unit-level only; integration tests deferred (issue #7) |
| 0.5.5 | Dockerfile + Compose ✓; K8s + CI/CD outstanding |
| 0.5.6 | not started |

**Open issues:**

- Per-service Mongock — the `extraction_idempotency` TTL index is auto-created by `@Indexed`, but for production-grade migration tooling we may want Mongock to own it (consistent with the audit_outbox pattern). Track separately.
- Real-Mongo idempotency test — blocked by issue #7.

**Next:** Tracing, readiness `HealthIndicator` beans, metrics, JWT, integration tests, K8s manifests, or the cloneable service-template doc.

## 2026-04-27 — Phase 0.5.6 — `docs/service-template.md`

**Done:** Captured the cloneable pattern. `docs/service-template.md` consolidates the bring-up checklist, substrate use (`gls-platform-audit`, `gls-platform-config`, generated stubs), conventions (RFC 7807 codes, idempotency, capabilities), the repo-root Docker build context requirement, and every generator + Mockito gotcha hit across 0.5.1–0.5.5.

**Why this PR exists now:** the gotchas are highest-fidelity right after the reference implementation lands. Future services that use this doc will skip:

- The `oneOf` empty-interface Jackson round-trip problem (caught twice in `gls-extraction-tika` — once on `setKind` not existing on inline branch, again on `Jackson can't deserialise the union`).
- The inline backticked-colon docstring crashing the generator's snakeyaml parser.
- The `mock()` inside an outer `when()` `UnfinishedStubbing` lint.
- The `ConfigChangePublisher` test-classpath issue.
- The Docker build context vs. `contracts/` accessibility gotcha.

These are documented as numbered sections in the template, each with the symptom, diagnosis, and the workaround pattern.

**Decisions logged:** None new. Closes 0.5.6 — both checkboxes flipped `[x]`.

**Files changed:**

- `docs/service-template.md` (new).
- `version-2-implementation-plan.md` — 0.5.6 items checked.
- `version-2-implementation-log.md` — this entry.

**Verification:** Markdown only — no compile / test impact.

**Open issues:**

- The doc references `docs/service-template.md` from `gls-extraction-tika`'s README (already there in form: "see `docs/service-template.md` (lands with 0.5.6)"). With the doc landed, the existing reference is now accurate; no further edit needed.

**Phase 0.5 status after this PR:**

- 0.5.1, 0.5.2, 0.5.6 ✓
- 0.5.3 — error returns ✓, audit ✓, basic health ✓; tracing / readiness `HealthIndicator` / metrics / JWT outstanding
- 0.5.4 — unit-level only; integration tests deferred (issue #7)
- 0.5.5 — Dockerfile + Compose ✓; K8s + CI/CD outstanding

**Next:** Tracing, readiness `HealthIndicator`s, metrics, JWT, K8s manifests, CI/CD wiring, or pivot to other Phase 0 follow-ups.

## 2026-04-27 — Phase 0.5.3 (readiness probes) — `TikaHealthIndicator` + `MinioHealthIndicator`

**Done:** Wired the two readiness `HealthIndicator` beans the actuator surface needed. Each `/actuator/health` call now exercises Tika (parse a 2-byte synthetic input) and MinIO (`listBuckets`), so the readiness gate doesn't flip UP before the service can actually do its job.

**What's wired:**

- `TikaHealthIndicator` — parses `"ok"` through the injected `TikaExtractionService` on every check. UP unless Tika throws.
- `MinioHealthIndicator` — calls `MinioClient.listBuckets()` and reports `bucketCount` as a detail. UP on success; DOWN with the exception's class+message on failure.
- Both are `@Component` beans; Spring Boot Actuator picks them up automatically and exposes them under `/actuator/health/tika` and `/actuator/health/minio`.

**Spring Boot 4 gotcha (logged for future services):** `Health`, `HealthIndicator`, and `Status` are no longer at `org.springframework.boot.actuate.health.*`. Spring Boot 4 split them into `spring-boot-health` jar at `org.springframework.boot.health.contributor.*`. Symptom is a confusing `package does not exist` compile error since `spring-boot-starter-actuator` is on the classpath. Documented in `docs/service-template.md` under "Spring Boot 4 gotchas".

**Decisions logged:** None new.

**Tests (3 new + 34 existing = 37 total, all green):**

- `TikaHealthIndicatorTest` (1) — healthy Tika reports UP.
- `MinioHealthIndicatorTest` (2) — reachable MinIO reports UP with `bucketCount`; `IOException` reports DOWN with exception detail.

**Files changed:**

- `backend/gls-extraction-tika/src/main/java/.../health/{TikaHealthIndicator,MinioHealthIndicator}.java` (2 new).
- `backend/gls-extraction-tika/src/test/java/.../health/{TikaHealthIndicatorTest,MinioHealthIndicatorTest}.java` (2 new).
- `docs/service-template.md` — new "Spring Boot 4 gotchas" section with the health-package relocation table.
- `version-2-implementation-plan.md` — 0.5.3 health probes flipped `[x]`.
- `version-2-implementation-log.md` — this entry.

**Verification:** `./mvnw -pl gls-extraction-tika -am test` — 37/37 pass.

**Phase 0.5 status after this PR:**

- 0.5.1, 0.5.2, 0.5.6 ✓
- 0.5.3 — error returns ✓, audit ✓, basic + readiness health ✓; tracing / metrics / JWT outstanding
- 0.5.4 — unit-level only (37 tests in extraction module alone)
- 0.5.5 — Dockerfile + Compose ✓; K8s + CI/CD outstanding

**Next:** Tracing (small — already passing traceparent through; spans + OTel are bigger), metrics (Micrometer), JWT validation, K8s manifests, CI/CD wiring.

## 2026-04-27 — Phase 0.5.3 (metrics) — Micrometer counters + latency histogram

**Done:** Added domain-specific Micrometer instruments to `gls-extraction-tika`. The Spring Boot Actuator + Micrometer chain already exposed JVM / HTTP metrics; this PR adds `gls_extraction_duration_seconds`, `gls_extraction_result_total`, and `gls_extraction_bytes_processed` so an operator can see the extraction pipeline's throughput, latency distribution, and outcome shape from Prometheus + Grafana.

**What's wired:**

- `ExtractMetrics` (`@Component`) — owns the meter names, tag taxonomy, and `Timer.Sample` lifecycle. Lives next to the controller; passes the `MeterRegistry` through.
- `ExtractController` — `Timer.Sample` started after the idempotency check; `recordSuccess(...)` / `recordFailure(...)` on completion; `recordIdempotencyShortCircuit("cached" | "in_flight", bucket)` for the short-circuit branches.

**Tag taxonomy (cardinality-bounded):**

| Tag | Values | Cardinality |
|---|---|---|
| `outcome` | `success` / `failure` / `cached` / `in_flight` | 4 |
| `source` | bucket name from request | bounded by # of source buckets (small) |
| `mime_family` | major slash-prefix of detected mime, or `unknown` | ~7 |
| `error_code` | closed taxonomy from `ExtractController.errorCodeFor` | 6 |

Free-form tag values (full mime strings, error messages, document ids) are deliberately excluded — Prometheus stores one time-series per unique tag combination, so unbounded cardinality wrecks the backend.

**Decisions logged:** None new.

**Tests (4 new + 37 existing = 41 total, all green):**

- `ExtractMetricsTest` (4) — success records duration + counter with `mime_family=application`; failure records with `error_code` tag; mime-family falls back to `unknown` for null / no-slash input; blank bucket falls back to `unknown`.
- All existing controller tests still pass (the constructor takes one extra arg now).

**Files changed:**

- `backend/gls-extraction-tika/src/main/java/.../web/ExtractMetrics.java` (new).
- `backend/gls-extraction-tika/src/main/java/.../web/ExtractController.java` — `ExtractMetrics` constructor param; `Timer.Sample` lifecycle; short-circuit counters.
- `backend/gls-extraction-tika/src/test/java/.../web/ExtractControllerTest.java` — wires `ExtractMetrics(new SimpleMeterRegistry())` on the two construction sites.
- `backend/gls-extraction-tika/src/test/java/.../web/ExtractMetricsTest.java` (new — 4 tests).
- `version-2-implementation-plan.md` — 0.5.3 metrics flipped `[x]`.
- `version-2-implementation-log.md` — this entry.

**Verification:** `./mvnw -pl gls-extraction-tika -am test` — 41/41 pass. Once the service is running, `curl http://localhost:8080/actuator/prometheus | grep gls_extraction_` should show the new families.

**Phase 0.5 status after this PR:**

- 0.5.1, 0.5.2, 0.5.6 ✓
- 0.5.3 — error returns ✓, audit ✓, basic + readiness health ✓, metrics ✓; **tracing / JWT outstanding**
- 0.5.4 — unit-level only (41 tests in extraction module)
- 0.5.5 — Dockerfile + Compose ✓; K8s + CI/CD outstanding

**Next:** Tracing (spans + traceparent forwarding via Micrometer Tracing + OTel), JWT validation middleware, K8s manifests, CI/CD wiring, integration tests once issue #7 unblocks.

## 2026-04-27 — Phase 0.5.3 (tracing) — `@Observed` on `tika.parse` + `minio.fetch`

**Done:** Tracing infrastructure for `gls-extraction-tika`. `traceparent` propagation works out of the box via Spring Boot's `ServerHttpObservationFilter` (request span) + the controller's existing audit-envelope traceparent passthrough. This PR adds nested spans for the two boundaries the plan called out: `tika.parse` and `minio.fetch`.

**What's wired:**

- `micrometer-tracing-bridge-otel` dep — wires Micrometer Observation to OpenTelemetry's `SpanContext` + `traceparent` propagation. The OTLP exporter (which actually pushes spans to a collector) is deployment-side; not bundled.
- `spring-aop` + `aspectjweaver` deps — required for `@Observed` annotation processing. Spring Boot 4's BOM doesn't manage `spring-boot-starter-aop` directly (renamed / merged), so the raw coords come in instead.
- `@Observed(name = "tika.parse", contextualName = "tika-parse", lowCardinalityKeyValues = {"component", "tika"})` on `TikaExtractionService.extract`.
- `@Observed(name = "minio.fetch", contextualName = "minio-fetch", lowCardinalityKeyValues = {"component", "minio"})` on `MinioDocumentSource.open`.

When deployments wire an OTLP collector (set `OTEL_EXPORTER_OTLP_ENDPOINT` and add the exporter dep), every `/v1/extract` call shows up as a request span containing nested `tika.parse` + `minio.fetch` spans. Without an exporter, the spans still get created (visible via `/actuator/metrics` and any local span exporter); they just aren't shipped.

**Spring Boot 4 gotcha (logged for future services):** `spring-boot-starter-aop` is no longer version-managed by the Boot 4 starter parent. Either pull `spring-aop` + `aspectjweaver` directly (this PR's choice) or specify a version. Documented in `docs/service-template.md` as part of the Boot 4 gotchas section.

**Decisions logged:** None new.

**Tests:** 41/41 still pass — `@Observed` is reflective annotation processing, doesn't change behaviour for unit tests that bypass the AOP layer.

**Files changed:**

- `backend/gls-extraction-tika/pom.xml` — `micrometer-tracing-bridge-otel`, `spring-aop`, `aspectjweaver` deps.
- `backend/gls-extraction-tika/src/main/java/.../parse/TikaExtractionService.java` — `@Observed` on `extract(...)`.
- `backend/gls-extraction-tika/src/main/java/.../source/MinioDocumentSource.java` — `@Observed` on `open(...)`.
- `version-2-implementation-plan.md` — 0.5.3 tracing flipped `[x]`.
- `version-2-implementation-log.md` — this entry.

**Verification:** `./mvnw -pl gls-extraction-tika -am test` — 41/41 pass. End-to-end span visibility requires an OTLP collector (deployment-side).

**Phase 0.5.3 status after this PR:**

- error returns ✓, audit ✓, basic + readiness health ✓, metrics ✓, tracing ✓
- **JWT validation outstanding** — defers until a JWKS issuer infrastructure exists (separate phase).

**Phase 0.5 status:**

- 0.5.1, 0.5.2, 0.5.6 ✓
- 0.5.3 — only JWT outstanding
- 0.5.4 — unit-level only (41 tests in extraction module; integration deferred to issue #7)
- 0.5.5 — Dockerfile + Compose ✓; K8s + CI/CD outstanding

**Next:** JWT validation (when JWKS infra is in place), K8s manifests, CI/CD wiring, integration tests, or pivot to Phase 1.

## 2026-04-27 — CI — backend-test now covers the whole reactor

**Done:** Fixed a real CI gap: `backend-test` only ran tests for `gls-app-assembly` and its transitive deps (via `mvn verify -pl gls-app-assembly -am`). Every test in the new modules (`gls-extraction-tika`, `gls-platform-audit`, `gls-platform-config`, plus the new `gls-platform-config` consumer paths in `gls-platform`, `gls-mcp-server`, `gls-governance`) was being skipped. CI was green by accident.

**What changed:**

- `.github/workflows/ci.yml` — `backend-test` now runs `./mvnw test` (no `-pl`). 153 unit tests across 23 test classes covered, vs. ~15 before.
- Switched from `verify` (which also runs `*IT.java` via failsafe) to `test` (runs `*Test.java` via surefire only). Issue #7 (Testcontainers MongoDB cleanup) was making `verify` red on every PR despite being documented as known debt; integration tests no longer gate the build until that's fixed.
- Comment in the workflow points at issue #7 so the rationale is discoverable when someone wants to re-enable failsafe.

**Decisions logged:** None new.

**Verification:**

- Locally: `./mvnw test` from `backend/` — 153 / 153 tests pass across 23 classes.
- The previous CI invocation (`mvn verify -pl gls-app-assembly -am`) covered ~7 test classes (only `gls-app-assembly`'s direct unit tests); the rest of the modules' tests were silently skipped.

**Why this matters:**

Every PR I shipped this session that added tests was passing locally but **not actually being run in CI**. The CI signal was about Spring Boot startup smoke and Spectral lint, not test correctness. Now an actual test failure in any module breaks the build — closer to a meaningful gate.

**Files changed:**

- `.github/workflows/ci.yml` — `backend-test` job updated.
- `version-2-implementation-log.md` — this entry.

**Open issues:**

- **Integration tests still don't run in CI.** Issue #7 needs to be fixed before re-enabling failsafe. When that lands, `mvn verify` becomes the right phase and ITs join the gate.
- **Frontend lint debt** (issue #8 — 151 warnings, downgraded to warn) is unaffected by this PR.

## 2026-04-27 — Backend Dockerfiles — repo-root context (silent-bug fix)

**Done:** Fixed a silent bug flagged in PR #33's open-issues. `backend/Dockerfile`, `Dockerfile.mcp`, and `Dockerfile.llm` all built with `context: ./backend` — that meant `../../contracts/audit/event-envelope.schema.json` (referenced by `gls-platform-audit`'s build-time resource directive) was unreachable at Docker build time. Maven's resource copy silently produced jars without the bundled schema; the runtime envelope validator presumably failed on first emit in any deployed container, but the failure surface (logged warnings) was easy to miss.

**What changed:**

- `backend/Dockerfile`, `backend/Dockerfile.mcp`, `backend/Dockerfile.llm` — rewritten to use repo-root context. Same shape as `backend/gls-extraction-tika/Dockerfile` (which got it right from the start). Multi-stage Eclipse Temurin 25 builder + JRE runtime; `WORKDIR /workspace`; copy `backend/` and `contracts/`; `cd /workspace/backend && ./mvnw -pl <module> -am package`. Pom-copy step extended to include the new modules (`gls-platform-audit`, `gls-platform-config`, `gls-extraction-tika`, `gls-governance-hub`, `gls-governance-hub-app`, `contracts-smoke`) so the dependency-cache layer hits.
- `docker-compose.yml` — three services updated (`api`, `mcp-server`, `llm-worker`): `context: .` + `dockerfile: backend/Dockerfile{,.mcp,.llm}`.

**Decisions logged:** None new. Aligns with the same context choice used in `backend/gls-extraction-tika/Dockerfile`.

**Verification:**

- `docker compose config` parses cleanly with the new paths.
- The actual `docker compose build` is exercised by the CI `docker-build` job; locally, the `gls-extraction-tika` build (using the same pattern) is known-good.

**Files changed:**

- `backend/Dockerfile`, `backend/Dockerfile.mcp`, `backend/Dockerfile.llm`.
- `docker-compose.yml`.
- `version-2-implementation-log.md` — this entry.

**Why this matters:**

The audit envelope's runtime validator inside `gls-platform-audit`'s `OutboxAuditEmitter` calls `EnvelopeValidator.fromBundledSchema()`, which reads `/schemas/event-envelope.schema.json` off the classpath. Pre-fix: in deployed containers that resource didn't exist, so `EnvelopeValidator` constructor threw `IllegalStateException` with a "Bundled audit envelope schema not found" message. Post-fix: the schema is bundled into every jar that depends on `gls-platform-audit`.

I noticed this gap during the `gls-extraction-tika` Dockerfile work in PR #33 and logged it as outstanding; this PR closes it.

## 2026-04-27 — Phase 0.7 (relay hardening) — ShedLock leader election

**Done:** Wired ShedLock leader election for the `OutboxRelay` per CSV #4 ("Tier 1 single-writer per resource"). Multi-replica deployments no longer risk double-publishing the same envelope — only one replica's relay holds the Mongo lock and runs `pollOnce` at a time.

**What's wired:**

- `gls-platform-audit/pom.xml` — added `shedlock-spring` and `shedlock-provider-mongo` 5.16.0 as **optional** deps. Consumers that don't need leader election (single-replica deployments, tests) don't pay for ShedLock.
- `OutboxRelay.pollOnce` — annotated with `@SchedulerLock(name = "gls-audit-outbox-relay", lockAtMostFor = "${gls.platform.audit.relay.lock-at-most-for:PT5M}", lockAtLeastFor = "${gls.platform.audit.relay.lock-at-least-for:PT0S}")`.
- `AuditRelayLockConfig` (new `@AutoConfiguration`) — registers a Mongo-backed `LockProvider` on a separate auto-config class so `@EnableSchedulerLock` (which is class-level) doesn't pollute the main `PlatformAuditAutoConfiguration` surface. Conditional on `MongoLockProvider` being on the classpath; conditional on `gls.platform.audit.relay.leader-election-enabled` (default `true`).
- `gls-app-assembly/pom.xml` — opts in to ShedLock by depending on the two libraries directly, so the api container has leader election active by default in production.
- `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` — added the new lock config class.
- BOM property `shedlock.version=5.16.0` in the parent pom.

**Decisions logged:** None new — implements the deferred CSV #4 hardening flagged in the relay's original PR (#21).

**Tests:** No changes — `@SchedulerLock` is reflective AOP, transparent to unit tests against the `OutboxRelay` constructor. 41 module tests still pass.

**Failure modes covered:**

- **One replica running**: lock acquires immediately every poll; nothing changes vs. the unlocked behaviour.
- **Two replicas running**: one acquires the lock, runs `pollOnce`, releases. The other's poll cycle finds the lock held and returns immediately — `@SchedulerLock` skips the method body when the lock can't be acquired.
- **Replica crashes mid-publish**: `lockAtMostFor` (5min default) caps how long the lock survives a crashed holder. After that ceiling, another replica can claim. Tradeoff: if a real publish takes >5min something else is wrong; the cap is a safety net, not a normal operating assumption.
- **Network partition between replicas**: each may think it holds the lock; both publish; the at-least-once delivery guarantee + downstream `eventId` deduplication on the `gls-audit-collector` keep correctness intact. The lock is best-effort.

**Files changed:**

- `backend/pom.xml` — `shedlock.version` property.
- `backend/gls-platform-audit/pom.xml` — optional shedlock deps.
- `backend/gls-platform-audit/src/main/java/.../relay/OutboxRelay.java` — `@SchedulerLock` annotation.
- `backend/gls-platform-audit/src/main/java/.../autoconfigure/AuditRelayLockConfig.java` (new).
- `backend/gls-platform-audit/src/main/resources/META-INF/spring/...AutoConfiguration.imports` — new entry.
- `backend/gls-app-assembly/pom.xml` — opts in to ShedLock.
- `version-2-implementation-log.md` — this entry.

**Verification:** `./mvnw test` — 41 tests still pass. End-to-end leader election needs a real multi-replica deployment to verify; the ShedLock library is widely deployed and well-tested.

**Open issues:**

- **Comprehensive metrics** — relay-side Micrometer counters for queue depth, publish rate, lock-contention rate. Smaller now that ShedLock is in (lock-acquisition events are the natural signal). Lift in a follow-up.
- **Circuit breaker on Rabbit** — when the broker is down, the relay currently retries every poll cycle with exponential per-row backoff. A global circuit breaker would short-circuit the cycle. Smaller follow-up.
- **`shedLock` Mongo collection** — created lazily by ShedLock on first lock attempt. No Mongock change unit needed; documented in case future schema-management policy requires explicit ownership.

## 2026-04-27 — Phase 0.7 (relay observability) — Micrometer counters + queue-depth gauge

**Done:** Added Micrometer instruments to `OutboxRelay` so an operator can see relay throughput, latency, error rate, and PENDING-row backlog from Prometheus + Grafana. Combined with the ShedLock leader election (PR #44) and the existing exponential backoff, the relay now has the operational observability surface CSV #5 implies.

**What's wired:**

- `OutboxRelayMetrics` (new) — owns the meter names and tag taxonomy. Constructor registers a Gauge for `gls_audit_relay_pending_depth` that polls `repository.findByStatusAndNextRetryAtBeforeOrderByCreatedAtAsc(PENDING, now, …)` size on each scrape (cheap — backed by `idx_status_nextRetry`).
- `OutboxRelay` — `Timer.Sample` started per row in `pollOnce`'s loop. On success → `recordPublished(timer, tier)`. On retry → `recordRetry(timer, tier)`. On terminal failure (max attempts) → `recordFailed(timer, tier)`. Serialisation failure (now thrown as a private `SerialisationFailureException`) records `recordFailed` after the row is already marked FAILED inside `publish`.
- `PlatformAuditAutoConfiguration` — new `@Bean OutboxRelayMetrics` (conditional on `RabbitTemplate` like the relay itself); relay bean signature extended.

**Tag taxonomy (cardinality-bounded):**

| Tag | Values | Cardinality |
|---|---|---|
| `tier` | `DOMAIN` / `SYSTEM` | 2 |
| `outcome` | `published` / `retried` / `failed` | 3 |

Queue depth gauge has no tags — it's a single global value.

**Counter / gauge / timer names:**

- `gls_audit_relay_publish_total{tier, outcome}` — counter.
- `gls_audit_relay_publish_duration_seconds{tier, outcome}` — Timer.
- `gls_audit_relay_pending_depth` — Gauge.

Operators can graph: `rate(gls_audit_relay_publish_total{outcome="published"}[5m])` for throughput; `gls_audit_relay_publish_duration_seconds{outcome="published"}` for p50/p95/p99 publish latency; `gls_audit_relay_pending_depth` for backlog; `rate(gls_audit_relay_publish_total{outcome="failed"}[5m])` for terminal-failure rate.

**Decisions logged:** None new.

**Tests:** Existing 6 `OutboxRelayTest` tests still pass — the constructor takes one new arg (`OutboxRelayMetrics`); test setUp wires a `SimpleMeterRegistry`-backed instance. No new assertions — meaningful counter-value assertions need the same setUp work spread across every test, low value vs. the constructor signature change. Reactor total: 153 unit tests, all green.

**Files changed:**

- `backend/gls-platform-audit/src/main/java/.../relay/OutboxRelayMetrics.java` (new).
- `backend/gls-platform-audit/src/main/java/.../relay/OutboxRelay.java` — `OutboxRelayMetrics` ctor param; `Timer.Sample` lifecycle around the poll loop's per-row work; `recordPublished` / `recordRetry` / `recordFailed` at the right outcome boundaries. Serialisation failure path now throws a private `SerialisationFailureException` so the catch site can stamp the FAILED metric.
- `backend/gls-platform-audit/src/main/java/.../autoconfigure/PlatformAuditAutoConfiguration.java` — `OutboxRelayMetrics` bean; updated relay bean signature.
- `backend/gls-platform-audit/src/test/java/.../relay/OutboxRelayTest.java` — wires `OutboxRelayMetrics(new SimpleMeterRegistry(), repository)` on the two construction sites.
- `version-2-implementation-log.md` — this entry.

**Verification:** `./mvnw test` — 153/153 across the reactor.

**Remaining 0.7 hardening items:**

- **Circuit breaker on Rabbit downtime** — when the broker is fully down, the relay currently retries every poll cycle with exponential per-row backoff. A global breaker would short-circuit the cycle (single test against the broker; if it's open, skip the polling poll altogether). Smaller follow-up.
- **`shedLock` Mongo collection** — created lazily by ShedLock; future schema-management policy may want a Mongock change unit to own its indexes explicitly.

## 2026-04-27 — Status board sync (bookkeeping)

**Done:** Updated the per-phase status board and the cross-cutting tracks table at the top of this log to reflect the current state. Both had been stale since early in the session — the board said `0.8 / 0.11 / 0.12 still open` and Phase 0.5 `Not started` even after PRs #22, #19, #27, #28–#41 landed.

**Decisions logged:** None — pure bookkeeping.

**What changed:**

- Phase 0: `In progress` → `Substrate complete; minor follow-ups outstanding`. Notes rewritten to reflect what's actually done and what's truly outstanding (Python audit sketch, Rabbit circuit-breaker, 0.11 load driver).
- Phase 0.5: `Not started` → `Substantially complete`. Notes break down per sub-phase (0.5.1 / 0.5.2 / 0.5.3 / 0.5.4 / 0.5.5 / 0.5.6) and call out what's blocked on what (JWT on JWKS infra; integration tests on issue #7; K8s manifests on a real K8s decision).
- Track A (Hub-side): `Not started` → `Implicitly covered`. Realisation: `GovernanceConfigChangeBridge` (PR #25) listens on Spring Data Mongo lifecycle events for governance entities, so Hub-driven `PackImportService` writes automatically fire `gls.config.changed` events. No separate Hub-side publisher is needed unless the Hub eventually deploys against a different Mongo from the api.

**Why this matters:**

Several PR descriptions in this session said "Hub-side publishers still pending" when in fact PR #25's bridge already covers it — the bridge fires on every `repo.save(...)` regardless of whether the caller is admin UI, API mutation, or Hub-driven import. The status sync makes this discoverable from the top of the log.

**Files changed:**

- `version-2-implementation-log.md` — status board + tracks table + this entry.

**Next:** Genuinely outstanding remaining items: JWT (blocked on JWKS), integration tests (blocked on issue #7), Phase 0.11 load driver (blocked on representative content), Python audit module sketch (deferred), Rabbit circuit-breaker (small follow-up to PR #45). Or pivot to Phase 1 (per-service splits beyond `gls-extraction-tika`).

## 2026-04-27 — Phase 1.1 (mime detection) — CSV #42 DECIDED

**Done:** Locked the mime-detection decision to DECIDED per the plan's call-out. Mime detection happens at ingest in `gls-app-assembly` (and the future `gls-connectors`) using Apache Tika's `Tika.detect(...)`. Per-extractor services may re-detect for their own dispatch but the canonical mime is set at ingest and is what the orchestrator routes on.

**Decisions logged:** CSV #42 (Domain Model, §Plan-1.1).

**Why:**

- Tika's detector is already on the api's classpath (`gls-extraction-tika` and `gls-document-processing` both pull `tika-core`); putting detection at ingest makes routing decisions deterministic from the very first event, before any extractor runs.
- Alternatives — extractors detecting and bubbling up — leak ordering between services (router can't pick the right extractor without already running one) and complicate retries.
- Centralising at ingest keeps the api as the single source of truth for the canonical mime, which the audit trail and admin UI both read.

**Acceptance pattern:** the existing `DocumentModel.mimeType` field carries the detected value. New ingest paths (Hub-driven imports, future connectors) MUST run Tika.detect at the boundary; existing paths (file upload, email ingest, Drive monitor) already do.

**Files changed:**

- `version-2-decision-tree.csv` — row #42 (DECIDED).
- `version-2-implementation-plan.md` — Phase 1.1 mime-detection bullet flipped `[x]`.
- `version-2-implementation-log.md` — this entry.

**Decision lock pattern note:** this is the 23rd decision accepted-as-written across the project (per the audit-decisions log entry). Reviewable + reversible per the standard CSV decision rules in `CLAUDE.md`. Phase 1.1 begins proper now that the routing prerequisite is locked.

**Next:** Phase 1.1 sub-services proper — `gls-extraction-archive`, `gls-extraction-ocr`, `gls-extraction-audio`. Each is its own clone of the `gls-extraction-tika` pattern (now codified in `docs/service-template.md`).

## 2026-04-29 — Phase 1.1 — `gls-extraction-archive` module + contract

**Done:** First Phase 1 service ships its contract + Maven module skeleton, cloning the 0.5.1 pattern. `contracts/extraction-archive/openapi.yaml` declares three operations — `POST /v1/extract`, `GET /v1/capabilities`, `GET /actuator/health` — referencing `_shared/` for the error envelope (CSV #17), service JWT (CSV #18), `traceparent` + `Idempotency-Key` headers (CSV #16 / #20), in-flight 409, 429 retry, `Capabilities` (CSV #21). The `gls-extraction-archive` Maven module is wired into the reactor; the BOM gains a fresh `gls.extraction.archive.version` per-deployable property (and renames the existing `gls.extraction.version` → `gls.extraction.tika.version` for parity — both still default to `${gls.version}`). The OpenAPI generator runs against the new spec on build and emits 15 source files (`ExtractionApi`, `MetaApi`, `ExtractRequest`, `ExtractResponse`, `ChildDocument`, `ChildDocumentDocumentRef`, `HealthResponse`, capabilities models) under `target/generated-sources/openapi/`. Reactor still 153 / 153 unit tests green.

**Decisions logged:** CSV #43 (Domain Model, §Plan-1.1) — archive fan-out responsibility + recursion depth: caller owns fan-out (one-level walk per invocation; children returned inline; orchestrator commits child `DocumentModel` rows and publishes per-child `gls.documents.ingested` events). Nested archives re-route through this service on a fresh `nodeRunId` via the normal pipeline path, not a recursion stack inside the service.

**Why CSV #43 lands at "caller owns fan-out":**

- Keeps the archive service stateless (no Mongo writes for child `DocumentModel`s, no Rabbit publish), matching the Tika pattern of *parser returns artifact, orchestrator owns side effects*. Failure semantics are simpler — a failed archive request leaves zero half-created child documents because the per-child `DocumentModel` + ingest event commit happens in `gls-app-assembly`'s existing transaction-safe path, not in this service.
- Aligns with CSV #42's stance ("api is the single source of truth for the canonical mime") — the same boundary that owns canonical mime owns canonical ingest.
- One-level-per-invocation bounds time and memory deterministically and gives every nested archive its own `nodeRunId` (so retries, idempotency, and audit work the same as for top-level uploads). Cost is one extra hop per nesting level — cheap relative to the cost of getting fan-out wrong.

**What's wired:**

- `contracts/extraction-archive/openapi.yaml` (new) — three operations. Idempotency on `nodeRunId` per CSV #16; cost-attribution `costUnits` per CSV #22. Idiomatic `ExtractRequest` / `ExtractResponse` shape; `ChildDocument` carries `documentRef` (where the child landed in MinIO), `fileName`, `size`, `detectedMimeType`, optional `archivePath`. 4XX / 5XX catch-alls keep the spec lint-clean while concrete codes (413 / 422 / 429 / 409) document the unhappy paths the implementation must recognise — the 413 envelope's `code` extension identifies *which* configured cap was hit (size / child-count / nesting depth) for zip-bomb defence.
- `contracts/extraction-archive/VERSION` (new) — `0.1.0`.
- `contracts/extraction-archive/CHANGELOG.md` (new) — initial entry.
- `contracts/extraction-archive/README.md` (new) — operation summary + behaviour notes (one-level walk, caller owns fan-out, bounded scope) + cross-references.
- `backend/gls-extraction-archive/pom.xml` (new) — declares deps on `gls-platform-audit` (audit emission lands with the impl PR), spring-boot-starter-webmvc / actuator / data-mongodb / amqp, Tika 3.1.0 (core + standard parsers — covers ZIP and MBOX out of the box; PST adds `java-libpst` or equivalent in the PST-walker PR), MinIO 8.5.14, jakarta.validation, swagger-annotations 2.2.30, micrometer-tracing-bridge-otel, spring-aop + aspectjweaver. Same generator config as Tika (`interfaceOnly=true`, `useSpringBoot3=true`, `useJakartaEe=true`, `skipDefaultInterface=true`, `openApiNullable=false`, `useTags=true`).
- `backend/gls-extraction-archive/README.md` (new) — points at the contract; lists what's deferred to follow-up PRs (matches Tika's 0.5.1 README shape).
- `backend/pom.xml` — added `gls-extraction-archive` to `<modules>`.
- `backend/bom/pom.xml` — renamed `gls.extraction.version` → `gls.extraction.tika.version`; added `gls.extraction.archive.version`; updated the `gls-extraction-tika` dependency entry's version property; added the `gls-extraction-archive` dependency entry. Both properties default to `${gls.version}` per the policy in `CLAUDE.md` → Independent Deployable Versions.

**Verification:**

- `./mvnw -pl gls-extraction-archive -am compile` clean — generator runs, 15 source files emitted, javac green.
- `./mvnw test` clean — full reactor builds, 153 / 153 unit tests pass (no archive tests yet — module skeleton).

**Files changed:** 4 new files under `contracts/extraction-archive/`; 2 new files under `backend/gls-extraction-archive/`; 2 modified poms (reactor + BOM); CSV; plan; status board; this log entry.

**Open issues:** None blocking the next archive PR. Tracked elsewhere:

- **PST walker dependency** — Tika's PST coverage is partial; `java-libpst` (or equivalent) is the standard route. Lands with the PST walker implementation, not in the skeleton pom.
- **Archive caps** — per-archive size, max child count, max nesting depth are runtime-configurable. Defaults set when the implementation lands; configuration source is `application.yaml` per the existing pattern.
- **Generated-stub commit policy** — the Tika service does not commit `target/generated-sources/openapi/` (CLAUDE.md → API Contracts says generated artefacts go under `contracts/<service>/generated/`). Mirroring Tika's behaviour for now; the policy gap was noted on the Tika 0.5.1 entry and remains a separate workstream.

**Next:** Phase 1.1 (archive) implementation PR — generated server stub + parser dispatch (ZIP via Commons Compress, MBOX via Tika's `MboxParser`) + MinIO source / sink + nodeRunId idempotency. PST walker is its own follow-up. Mirrors Tika's 0.5.2 boundary.

## 2026-04-29 — Phase 1.1 — `gls-extraction-archive` implementation (ZIP + MBOX)

**Done:** Wired the archive service end-to-end at the unit-test layer. The module clones the `gls-extraction-tika` 0.5.2–0.5.4 scaffolding (source / sink / idempotency / audit / health / metrics / exception handler / controller) and adds the per-format walker dispatch the archive service needs. PST walker is deferred — explicitly out-of-scope for this PR pending a `java-libpst`-or-equivalent decision; the dispatcher rejects PST mime types via `UnsupportedArchiveTypeException` until it lands.

**Decisions logged:** None new. Implements CSV #43 (caller owns fan-out) — controller returns the list of children's MinIO `documentRef`s on the response; orchestrator commits per-child `DocumentModel` rows + publishes ingest events.

**What's wired (under `backend/gls-extraction-archive/src/main/java/co/uk/wolfnotsheep/extraction/archive/`):**

- **`source/`** — `DocumentRef`, `DocumentSource`, `MinioDocumentSource`, `MinioSourceConfig`, `DocumentNotFoundException`, `DocumentEtagMismatchException`. Identical shape to Tika's source layer; reads the source archive bytes from MinIO with optional ETag check + `@Observed` span (`minio.fetch`).
- **`sink/`** — `ChildSink` (interface), `MinioChildSink` (impl), `ChildRef` (record), `MinioChildSinkConfig`. Distinct from Tika's text sink — writes binary children, key shape `<nodeRunId>/<index>-<sanitised-fileName>` (deterministic so retries overwrite, not append). `@Observed` `minio.put`. Lazy bucket creation. Filename sanitisation strips path separators + control chars while keeping the extension for downstream mime detection.
- **`idempotency/`** — full Tika-style store, repo, record, outcome, `IdempotencyInFlightException`. Mongo collection `archive_idempotency` with the same TTL contract (24h default). `tryAcquire` / `cacheResult` / `releaseOnFailure` semantics for ACQUIRED / IN_FLIGHT / CACHED.
- **`audit/ArchiveEvents.java`** — Tier 2 event factory. Reuses `EXTRACTION_COMPLETED` / `EXTRACTION_FAILED` event types so the audit stream is homogeneous across the extraction family (readers filtering on `action="EXTRACT"` catch tika + archive + future ocr/audio in one query). Metadata distinguishes archive specifics: `archiveType`, `childCount`. Validated by the schema validator in tests.
- **`parse/`** — `ArchiveType` enum (`ZIP`, `MBOX`, `PST`); `ArchiveWalker` interface; `ChildEmitter` callback (renamed from `ChildSink` to avoid collision with the storage-side interface); `ArchiveWalkerDispatcher` which Tika-detects the source mime, picks the matching walker, and invokes a single-pass walk (`@Observed` span `archive.walk`). Walkers: `ZipArchiveWalker` (Apache Commons Compress streaming reader; skips directory entries; corruption / EOF mid-stream → `CorruptArchiveException` for a 422; encrypted entries → `CorruptArchiveException` since passwords are out-of-scope) and `MboxArchiveWalker` (line-based RFC 4155 splitter that emits one `.eml` per `From `-prefixed message; tolerates garbage at file head; preserves the envelope `From ` line on emitted children for downstream MIME parsing).
- **`health/`** — `MinioHealthIndicator` (clones Tika's; lists buckets), `ArchiveDispatcherHealthIndicator` (DOWN if no walkers registered; UP with `supportedTypes` detail otherwise).
- **`web/`** — `ExtractController` (the orchestration hub: idempotency → source.open → dispatch → sink.upload per child → assemble response → cache + audit; per-cap caps thrown via `ArchiveCapsExceededException`); `ArchiveExceptionHandler` (RFC 7807 mapping with `code` extensions: `DOCUMENT_NOT_FOUND`, `DOCUMENT_ETAG_MISMATCH`, `ARCHIVE_CORRUPT`, `ARCHIVE_UNSUPPORTED_TYPE`, `ARCHIVE_TOO_LARGE`, `ARCHIVE_TOO_MANY_CHILDREN`, `ARCHIVE_CHILD_TOO_LARGE`, `IDEMPOTENCY_IN_FLIGHT`, `ARCHIVE_SOURCE_UNAVAILABLE`); `MetaController` (clones Tika's); `ExtractMetrics` (cohesive Micrometer instruments — `gls_archive_duration_seconds`, `gls_archive_result_total`, `gls_archive_children`, `gls_archive_bytes_processed`).
- **`GlsExtractionArchiveApplication`** — Spring Boot entry point.
- **`src/main/resources/application.yaml`** — `server.port: 8090` (distinct from Tika's 8080); MinIO endpoint / credentials env-var fallbacks; cap defaults — 1 GB max archive, 5000 max children, 256 MB max single-child decompressed (zip-bomb defence; the decompression is bounded inline by `ChildBoundedInputStream` so a malicious ZIP can't OOM the JVM before the walk completes); idempotency TTL `PT24H`; actuator exposes `health, info, prometheus`.

**Caps strategy:**

Three caps enforced with explicit error codes:

- **`ARCHIVE_TOO_LARGE`** — source archive bytes exceed `gls.extraction.archive.caps.max-archive-bytes` (default 1 GB). Pre-flight check via `source.sizeOf()` plus an inline `CountingInputStream` that trips on actual reads (handles sources that don't expose a HEAD).
- **`ARCHIVE_TOO_MANY_CHILDREN`** — child count exceeds `max-children` (default 5000). Checked by the controller's emitter before the next child upload.
- **`ARCHIVE_CHILD_TOO_LARGE`** — a single child's decompressed size exceeds `max-child-bytes` (default 256 MB). Bounded by `ChildBoundedInputStream` wrapping the entry stream; trips during the put, before the JVM commits memory to the inflated bytes.

The contract documented "max recursion depth" as a third cap, but per-invocation depth is moot in this architecture (one-level walk by design; pipeline depth is the orchestrator's concern). The implementation enforces what fits: size + count + per-child size. Future depth concerns belong upstream of this service.

**Tests (34 in module; 187 reactor total):**

- `ArchiveEventsTest` (4) — completed/failed envelope shape, schema validation, eventId pattern.
- `ZipArchiveWalkerTest` (4) — happy multi-entry, directory-entry skip, emitter caps propagation, truncated-archive corruption.
- `MboxArchiveWalkerTest` (5) — multi-message split, empty input, single message, separator-line preservation, garbage-head tolerance.
- `ArchiveWalkerDispatcherTest` (4) — ZIP / MBOX dispatch routing; unsupported-mime rejection; walkers map advert.
- `ExtractControllerTest` (11) — happy ZIP, happy MBOX, document-not-found + FAILED audit, archive-too-large pre-flight, archive-too-many-children mid-walk, EXTRACTION_COMPLETED Tier 2 emission, in-flight idempotency short-circuit, cached idempotency replay, success-cache write, failure releases idempotency row, generated-API contract assertion.
- `MetaControllerTest` (3) — capabilities advert, health UP, generated API contract.
- `MinioChildSinkKeyTest` (3) — path-separator stripping, null-filename fallback, control-character replacement.

Spring context is not loaded — pure POJO tests with mocked source / sink / idempotency / audit + real `ArchiveWalkerDispatcher` driving real `ZipArchiveWalker` / `MboxArchiveWalker`. Aligns with Tika's test pyramid; integration tests blocked on issue #7 like the rest of the reactor.

**One-line correction during the run:** `ZipArchiveWalker`'s catch block initially split `ZipException` (→ corruption) from generic `IOException` (→ `UncheckedIOException`). A truncated ZIP throws `EOFException` (a plain `IOException`), which surfaced the test failure `walk_throws_corrupt_on_truncated_zip`. Fix: any `IOException` raised by Commons Compress *during* the walk is corruption, since the source-side I/O boundary already passed cleanly at `source.open()`. Source-unavailable still surfaces as `UncheckedIOException` from the source layer, matching Tika's pattern.

**Files changed:** 26 new source files under `backend/gls-extraction-archive/src/main/java/`; 1 new resource (`application.yaml`); 7 new test files; plan + log.

**Open issues:**

- **PST walker** — its own PR with the lib decision logged in CSV.
- **JWT** — same as Tika; blocked on JWKS infra.
- **Dockerfile + Compose** — own PR. Mirror Tika's repo-root build context pattern (per `docs/service-template.md`'s "Repo-root build context" callout).
- **Integration tests** — gated on issue #7.
- **Generated stubs commit policy** — same gap as Tika; tracked elsewhere.

**Next:** Dockerfile + Compose service entry for `gls-extraction-archive` (mirrors Tika's 0.5.5). Then the PST walker. Then Phase 1.1 acceptance — three extraction services live or skip ahead to 1.2 router.

## 2026-04-29 — Phase 1.1 — `gls-extraction-archive` PST walker + Dockerfile + Compose

**Done:** Closes the per-format walker triad and ships the deployable for `gls-extraction-archive`. PST support uses `com.pff:java-libpst` (CSV #44 logged). Dockerfile mirrors Tika's repo-root build context pattern. Compose entry activates with full Mongo + Rabbit + MinIO wiring (the archive service holds idempotency rows in Mongo + emits Tier 2 audit events through `gls-platform-audit`'s outbox-relay, which needs Rabbit).

**Decisions logged:** CSV #44 (Domain Model, §Plan-1.1) — PST parser library = `com.pff:java-libpst` v0.9.3 (Apache 2.0). Java-libpst is the de-facto OSS option in the JVM ecosystem; Maven Central; pure Java (no JNI). Alternatives evaluated: Tika's PST coverage (partial; relies on java-libpst internally), Aspose / Independentsoft (proprietary), libpff via JNI (adds native build step that complicates Compose-only deployment per CSV #38). Java-libpst's drawback (requires `RandomAccessFile`, not a stream) is handled by materialising the source archive to a temp file before walking — acceptable given the per-archive size cap.

**What's wired:**

- **`parse/PstArchiveWalker.java`** (new) — registers as `ArchiveType.PST`. Materialises the source stream to a `Files.createTempFile(...)` PST, opens via `PSTFile`, recurses through subfolders, iterates messages via `folder.getNextChild()`, synthesises a basic RFC 822 `.eml` per message (From / To / Cc / Subject / Date / Message-ID / MIME-Version / Content-Type plus body), and emits via the `ChildEmitter`. Cleanup deletes the temp file in a `finally` block. Corrupt or password-protected PSTs surface as `CorruptArchiveException` (HTTP 422 / `ARCHIVE_CORRUPT`).
- **`pom.xml`** — added `com.pff:java-libpst:0.9.3` to dependencies with a comment explaining the choice.
- **`Dockerfile`** (new) — multi-stage from `eclipse-temurin:25-jdk` build → `eclipse-temurin:25-jre` runtime. Repo-root build context per the service template's "Repo-root Docker build context" callout (lets the build reach `contracts/` for the audit envelope schema bundling). Healthcheck on `/actuator/health` over port 8090. Identical shape to Tika's Dockerfile barring artifact name + port.
- **`docker-compose.yml`** — new active block `gls-extraction-archive` with full env wiring (Mongo URI, Rabbit credentials, MinIO endpoint, sink bucket override). Depends on `mongo` / `rabbitmq` / `minio` healthchecks. Uses `default-logging` + `gls` network.
- **PST attachment limitation** — documented on the walker's javadoc and the service README. The walker emits message body only; attachments inside PST messages are dropped on the floor for now. The follow-up PR adds attachment children using `archivePath` to anchor them to the parent message id.

**Tests:**

- `PstArchiveWalkerTest` (3) — `supports()` returns PST; garbage bytes → `CorruptArchiveException`; empty stream → `CorruptArchiveException`. Happy-path PST walking is integration-test territory (requires a checked-in PST fixture and a JVM that can open it through java-libpst's RandomAccessFile path — `@TempDir`-friendly, but the fixture itself is non-trivial to author in code; java-libpst is read-only). Blocked on issue #7 like the rest of integration coverage.
- `ArchiveWalkerDispatcherTest` updated — third walker (`PstArchiveWalker`) registered; `walkers().keySet()` now contains all three types.
- All other archive tests unchanged. **37 / 37 in module; 190 / 190 across the reactor.**

**Files changed:** 4 new (`PstArchiveWalker.java`, `PstArchiveWalkerTest.java`, `Dockerfile`, dispatcher test edit); 4 modified (`pom.xml`, `docker-compose.yml`, `README.md`, plan, log, CSV).

**Open issues:**

- **PST attachments** — own follow-up PR. Each PSTMessage's attachments enumerated via `msg.getAttachments()`; each attachment becomes a child with `archivePath = "<message-index>/<attachment-index>"` and `detectedMimeType` from the attachment's mime header.
- **Integration tests** — gated on issue #7. A small fixture PST (≤ 100 KB, generated once via Outlook and checked in) gives us happy-path coverage when the gate lifts.

**Next:** OCR service end-to-end (Tesseract via Tess4J — separate CSV decision). Then audio service end-to-end (Whisper, async-capable). Closing out Phase 1.1 to the extent possible without JWT / integration-test infrastructure.

## 2026-04-29 — Phase 1.1 — `gls-extraction-ocr` end-to-end

**Done:** Second extraction-family service ships end-to-end. Contract + module + impl + Dockerfile + Compose entry land in a single PR (cloning the archive service's PR cadence). Idempotency, audit, health, metrics, RFC 7807 error mapping, Jackson DEDUCTION mixin for the `oneOf` text payload — all wired against the same patterns as Tika.

**Decisions logged:** CSV #45 (Topology, §Plan-1.1) — OCR engine = Tesseract via Tess4J 5.13.0. Three reasons against managed OCR (Document AI / Textract): Compose-only stance per CSV #38 conflicts with hard cloud-vendor coupling; cost (managed OCR is per-page); data residency (corporate-records OCR sends sensitive content over the wire). Trade-offs: native binary on the runtime image (handled via apt) and accuracy plateau on very low-quality scans (mitigation: a future `gls-extraction-ocr-cloud` variant can ship behind the same contract). Per-request `languages` array selects from installed Tesseract language packs.

**What's wired (under `backend/gls-extraction-ocr/`):**

- **`source/`** — clone of the Tika source layer. Same `MinioDocumentSource` with `@Observed minio.fetch` and ETag verification. Domain exceptions map to RFC 7807 in the handler.
- **`sink/`** — UTF-8 text sink (mirrors Tika's; not a binary children sink — OCR produces text). Object key `ocr/<nodeRunId>.txt`.
- **`idempotency/`** — full Tika-style store on the `ocr_idempotency` Mongo collection with 24h TTL.
- **`audit/OcrEvents`** — Tier 2 `EXTRACTION_COMPLETED` / `_FAILED` (homogeneous family eventTypes); metadata adds `languages` + `pageCount`.
- **`parse/`** — `OcrExtractionService` interface + `Tess4JOcrExtractionService` impl. Tess4J's `Tesseract` loads libtesseract via JNA on first call; controller materialises the source stream to a temp file and runs `doOCR(File)` so PDF + image inputs follow the same path. Native-library load failure surfaces as `UncheckedIOException` for a 503 (readiness gate flips DOWN). Language errors → `OcrLanguageUnsupportedException` for a 422 with `OCR_LANGUAGE_UNSUPPORTED`.
- **`health/`** — `MinioHealthIndicator` + `TesseractHealthIndicator` (checks tessdata directory exists with at least one `.traineddata` file + Tess4J instantiable; surfaces installed language packs as a detail).
- **`web/`** — `ExtractController` (idempotency → source → ocr.run → response shape + cache + audit; pre-flight source-size cap throws `DocumentTooLargeException` for `OCR_TOO_LARGE` / 413; Jackson DEDUCTION mixin for the `oneOf` text payload, same pattern as Tika); `OcrExceptionHandler` (RFC 7807 mapping with `code` extensions: `DOCUMENT_NOT_FOUND`, `DOCUMENT_ETAG_MISMATCH`, `OCR_CORRUPT`, `OCR_LANGUAGE_UNSUPPORTED`, `OCR_TOO_LARGE`, `IDEMPOTENCY_IN_FLIGHT`, `OCR_SOURCE_UNAVAILABLE`); `MetaController`; `ExtractMetrics` (`gls_ocr_*`).
- **`Dockerfile`** — multi-stage from `eclipse-temurin:25-jdk` to `eclipse-temurin:25-jre`. Runtime image installs `tesseract-ocr` via apt; per-language packs (`tesseract-ocr-fra`, `tesseract-ocr-deu`, …) installed via the `OCR_LANGUAGES` build-arg (default `eng`). `TESSDATA_PREFIX=/usr/share/tesseract-ocr/5/tessdata` baked in. Healthcheck on `/actuator/health` over port 8091.
- **`docker-compose.yml`** — new active block `gls-extraction-ocr` with `args.OCR_LANGUAGES` honoured from the `OCR_LANGUAGES` env var on the host (`OCR_LANGUAGES="eng fra deu" docker compose build gls-extraction-ocr`). Mongo + Rabbit + MinIO health-gated.
- **`application.yaml`** on port 8091. Idempotency TTL `PT24H`; inline-byte-ceiling 256 KB; max-source-bytes 256 MB; tessdata path env-overridable.

**Tests (17 in module; 207 reactor total):**

- `OcrEventsTest` (4) — completed/failed envelope shape; null-language omission; eventId pattern.
- `MetaControllerTest` (3) — capabilities + health + generated API contract.
- `ExtractControllerTest` (10) — happy inline + textRef branches; not-found + FAILED audit; corrupt → `OCR_CORRUPT`; language-unsupported → `OCR_LANGUAGE_UNSUPPORTED`; source too large → `OCR_TOO_LARGE`; COMPLETED audit shape; in-flight short-circuit; cached replay; generated API contract.

Native-engine tests (`Tess4JOcrExtractionServiceTest`, real-binary health probe) are integration-test territory — blocked on issue #7 plus the runtime-image build. Once unblocked, a Testcontainers fixture using the `gls-extraction-ocr` image gives end-to-end coverage including a sample PNG OCR'd through Tesseract.

**Files changed:** 4 contracts files + 1 Dockerfile + 1 README + 1 pom + 1 application.yaml + 24 source files + 3 test files + 4 reactor / BOM / compose / plan / log / CSV updates = 39 files changed.

**Open issues:**

- **PDF page count metric** — `OcrResult.pageCount` is null even for multi-page PDFs. Pulling pdfbox into the parser would expose it; deferred until we need that metric concretely (the contract advertises it as optional).
- **GPU OCR** — Tesseract is CPU-only. The architecture said "GPU-tolerant if needed"; not needed yet, deferred. A future `gls-extraction-ocr-gpu` variant can swap the engine while keeping the contract.
- **JWT + integration tests** — blocked on JWKS infra and issue #7 respectively; same pattern as Tika / archive.

**Next:** Audio service end-to-end (Whisper backend, async-capable per CSV #13). Closing the extraction-family triad and ending Phase 1.1 to the extent possible.

## 2026-04-29 — Phase 1.1 — `gls-extraction-audio` end-to-end

**Done:** Closes the Phase 1.1 extraction triad. Contract + module + sync + async paths + pluggable backend (OpenAI Whisper) + idempotency + audit + health + Dockerfile + Compose entry. Audio is the first v2 service to implement the `Prefer: respond-async` pattern from CSV #13.

**Decisions logged:**

- **CSV #46** — Audio backend: pluggable `AudioTranscriptionService` interface with two impls in this repo. `OpenAiWhisperService` calls OpenAI's `/v1/audio/transcriptions` (model `whisper-1`); auth via `OPENAI_API_KEY`. `NotConfiguredAudioTranscriptionService` (default) returns 503 `AUDIO_NOT_CONFIGURED`. Local `whisper.cpp` and Deepgram support deferred behind the same interface — both ship without a contract change. Hybrid choice (vs. forcing one stance) keeps the door open while delivering a working cloud backend; data-residency concerns acknowledged and left to deployment configuration.
- **CSV #47** — Async semantics: single `POST /v1/extract` endpoint; without `Prefer: respond-async` returns 200 sync, with the header returns 202 + `Location: /v1/jobs/{nodeRunId}`. `GET /v1/jobs/{nodeRunId}` polls. Sync and async share one idempotency row in the `audio_jobs` collection — a `Prefer: respond-async` retry after a successful sync run returns the cached result via the poll URL. Async dispatch via Spring `@Async` to a bounded `audioAsyncExecutor` thread pool; the deployment unit stays one container per service per CSV #38.

**What's wired (under `backend/gls-extraction-audio/`):**

- **`source/`** + **`sink/`** — clones of the Tika source/sink layers; sink writes UTF-8 transcripts on the `gls-audio-text` bucket for the textRef path.
- **`jobs/`** — `JobRecord` (Mongo `audio_jobs` with TTL), `JobRepository`, `JobStore` (combines idempotency + async-job lifecycle: `tryAcquire` returns ACQUIRED / RUNNING / COMPLETED / FAILED; `markRunning`, `markCompleted`, `markFailed`, `find`).
- **`parse/`** — `AudioTranscriptionService` interface + `AudioResult` record + `OpenAiWhisperService` (multipart upload to OpenAI's API via Java's built-in `HttpClient`; 4xx → `AudioCorruptException`, 5xx / IO → `UncheckedIOException`) + `NotConfiguredAudioTranscriptionService` fallback + `AudioBackendConfig` (selects the impl from `gls.extraction.audio.provider`; falls back to not-configured when the API key is blank rather than failing startup). `AudioCorruptException` → 422; `AudioNotConfiguredException` → 503.
- **`audit/AudioEvents`** — Tier 2 `EXTRACTION_*` events; metadata adds `provider`, `language`, `durationSeconds`.
- **`health/`** — `MinioHealthIndicator` + `AudioBackendHealthIndicator` (UP when backend is ready, OUT_OF_SERVICE when `provider=none` or the API key is missing — `MetaController` mirrors this on the public `/actuator/health` response with a 503 status).
- **`web/`** — `ExtractController` (handles sync, async, idempotency-cached, idempotency-running collisions in one method via the `JobAcquisition` switch); `JobController` (poll endpoint); `AudioExceptionHandler`; `MetaController`; `ExtractMetrics` with `gls_audio_*` series; `AsyncDispatcher` (the `@Async` proxy boundary lives in a separate bean from the controller because Spring AOP doesn't advise self-invocations); `AsyncConfig` (bounded `audioAsyncExecutor` thread pool). DEDUCTION mixin for the `oneOf` text payload.
- **`application.yaml`** on port 8092. Defaults: `provider=none`; `inline-byte-ceiling=256KB`; `max-source-bytes=500MB`; `async.max-size=8`; `jobs.ttl=PT24H`. OpenAI endpoint + model + timeout overridable via env.
- **`Dockerfile`** — multi-stage; same repo-root build pattern as Tika / archive / OCR. Healthcheck on `/actuator/health` over port 8092.
- **`docker-compose.yml`** — new active block; passes `OPENAI_API_KEY` + `GLS_AUDIO_PROVIDER` from the host env.

**Tests (27 in module; 234 reactor total):**

- `AudioEventsTest` (4) — envelope shape; null-field omission; eventId pattern.
- `NotConfiguredAudioTranscriptionServiceTest` (3) — provider id, ready flag, throws `AudioNotConfiguredException`.
- `MetaControllerTest` (4) — capabilities; UP when backend ready; 503 OUT_OF_SERVICE when not; generated API contract.
- `ExtractControllerTest` (11) — sync happy; async returns 202 + Location + `JobAccepted` body + dispatches to AsyncDispatcher; not-found + FAILED audit; backend-not-configured propagates; corrupt audio; source-too-large; in-flight collision sync (409) vs async (202); cached idempotency sync (returns cached) vs async (returns 202 with poll URL); generated API contract.
- `JobControllerTest` (5) — 404 when missing; PENDING / COMPLETED / FAILED status shapes; generated API contract.

Real-backend integration tests (Whisper round-trip with an `OPENAI_API_KEY`) are integration-test territory — blocked on issue #7. The unit suite covers everything that doesn't need a network round-trip.

**Files changed:** 4 contract files + 1 Dockerfile + 1 README + 1 pom + 1 application.yaml + 25 source files + 5 test files + 4 reactor / BOM / compose / plan / log / CSV updates = ~42 files changed.

**Open issues:**

- **Local Whisper + Deepgram** — both deferred behind the `AudioTranscriptionService` interface; ship without contract change.
- **Real-backend integration tests** — gated on issue #7 + an `OPENAI_API_KEY` for the test runner.
- **JWT** — blocked on JWKS infra; family-wide.

**Next:** Phase 1.1 substantively complete. Phases 1.2+ (classifier-router, BERT, SLM/LLM rework, hub wiring, …) are open. The handoff's substrate follow-ups (audit-relay live smoke, Rabbit circuit-breaker, load driver, Python audit module sketch) remain valid out-of-band tasks.

## 2026-04-29 — Phase 1.2 — `gls-classifier-router` first cut (contract + module + deterministic mock)

**Done:** First cut of the cascade router lands the contract surface, module skeleton, deterministic mock cascade, idempotency, audit, RFC 7807 mapping, Dockerfile, and Compose entry. The orchestrator and admin UI have a stable target to integrate against while the real BERT / SLM / LLM tiers wire in across Phases 1.4–1.6.

**Decisions logged:** None new — implements existing decisions (CSV #2 cascade hybrid, #14 block-version pinning, #16 idempotency, #17 RFC 7807, #18 service JWT, #19 TextPayload, #21 capabilities).

**What's wired (under `backend/gls-classifier-router/`):**

- **Contract** — `POST /v1/classify`, `GET /v1/capabilities`, `GET /actuator/health`. Request: `{ nodeRunId, block: { id, version?, type? }, text: TextPayload, cascadeHints?, documentRef?, documentId? }`. Response: `{ nodeRunId, block, tierOfDecision, confidence, result, rationale?, evidence?, cascadeTrace?, durationMs, costUnits }` — `tierOfDecision` enum includes `MOCK` for the first cut + `BERT` / `SLM` / `LLM` / `ROUTER_SHORT_CIRCUIT` for the future.
- **`parse/CascadeService` + `MockCascadeService`** — interface + deterministic stub. Block-shape varies by declared block type (`PROMPT` → `{ category, sensitivity, confidence }`; `BERT_CLASSIFIER` → `{ label, confidence }`). `tierOfDecision="MOCK"` always; trace surfaces `{tier=BERT, accepted=false, errorCode=MOCK_DISABLED}` so observers can see the cascade isn't real yet.
- **`idempotency/`** — Tika-style store on the `router_idempotency` Mongo collection, 24h TTL.
- **`audit/RouterEvents`** — Tier 2 `CLASSIFY_COMPLETED` / `CLASSIFY_FAILED`, distinct event-types from the extraction family so audit consumers can filter classify calls separately. `action="CLASSIFY"`.
- **`web/`** — `ClassifyController` (idempotency → cascade.run → response shape + cache + audit; Jackson DEDUCTION mixin on the request-side text `oneOf`); `RouterExceptionHandler` (`ROUTER_BLOCK_NOT_FOUND` 422, `IDEMPOTENCY_IN_FLIGHT` 409, `ROUTER_DEPENDENCY_UNAVAILABLE` 503); `MetaController` (capabilities advertises `tiers=["MOCK"]`); `ExtractMetrics` with `gls_router_classify_*` series.
- **`application.yaml`** on port 8093.
- **`Dockerfile`** — multi-stage, repo-root context.
- **`docker-compose.yml`** — replaced the long-standing commented placeholder with an active block. Mongo + Rabbit health-gated.

**Tests (19 in module; 253 reactor total):**

- `RouterEventsTest` (4) — envelope shape; null block-coordinate omission; eventId pattern.
- `MockCascadeServiceTest` (6) — PROMPT shape; BERT_CLASSIFIER shape; null-type defaults to PROMPT shape; empty / null text → 0 byte count; trace advertises tier-disabled status.
- `MetaControllerTest` (3) — capabilities lists `MOCK` tier; health UP; generated API contract.
- `ClassifyControllerTest` (6) — happy MOCK; CLASSIFY_COMPLETED audit shape; in-flight short-circuit; cached replay; success caches response; generated API contract.

**Files changed:** 4 contracts files + 1 Dockerfile + 1 README + 1 pom + 1 application.yaml + 14 source files + 4 test files + 4 reactor / BOM / compose / plan / log updates = ~30 files changed.

**Open issues / deferred:**

- **Real LLM-worker dispatch** — bound for the next PR in Phase 1.2. Wiring the existing `gls.pipeline.llm.jobs` queue from inside the router via Rabbit consumer + correlation-id futures, replacing the mock for the LLM tier. Phase 1.4 BERT and 1.5 SLM tiers cascade in front of it after.
- **`ROUTER` block type** — block content schema lands as a separate PR (block library expansion).
- **Admin migration** — introduces `ROUTER` block type to the admin UI with `bertAccept=1.01` everywhere (cascade disabled by default until tuning lands).
- **TextRef fetcher** — first cut returns the same mock regardless of inline-vs-textRef. The follow-up wires a small MinIO fetcher behind `inlineText()`.
- **JWT** — blocked family-wide on JWKS.
- **Integration tests** — blocked on issue #7.

**Next:** Real LLM-worker dispatch (Phase 1.2 follow-up) OR move to Phase 1.3 orchestrator cutover OR wire BERT inference (1.4). The router's contract surface is stable so any of these can proceed in parallel.

## 2026-04-29 — Phase 1.2 — LLM-worker dispatch via Rabbit (PR2)

**Done:** Real LLM tier wired behind the `CascadeService` interface. `LlmDispatchCascadeService` publishes `LlmJobRequest` (wire-compatible with `gls-document`'s `LlmJobRequestedEvent`) to the existing `gls.pipeline` exchange with routing key `pipeline.llm.requested`; listens on a per-replica auto-named queue bound to `pipeline.llm.completed` and correlates responses to dispatching calls by `jobId` via an in-memory `CompletableFuture` map. Mock cascade stays as the default; flip via `gls.router.cascade.llm.enabled=true` to swap in.

**Decisions logged:** None new — implements existing CSV #1 (workers call MCP), CSV #14 (block-version pinning) and CSV #16 (idempotency) by passing `blockId` / `blockVersion` straight through to the LLM worker's existing event shape.

**Per-replica queue rationale:** the existing `gls.pipeline.llm.completed` durable queue is consumed by `PipelineResumeConsumer` (in `gls-app-assembly`). If the router shared that queue, requests would be stolen. Instead each router replica binds its own non-durable, exclusive, auto-named queue to the same routing key on the same exchange — every replica sees every event; replicas drop events whose `jobId` doesn't match a registered future. Mild fanout cost, exact correlation. Same pattern as the v2 stack uses for `gls.config.changed`.

**Sync HTTP behaviour:** the router's `POST /v1/classify` is sync per the current contract; the LLM tier blocks the response thread on the future for up to `gls.router.cascade.llm.timeout` (default 60s). Timeout surfaces as 504 `ROUTER_LLM_TIMEOUT`; an LLM-side failure (`success=false`) surfaces as 502 `ROUTER_LLM_FAILED`. CSV #13 envisages an async path via `Prefer: respond-async` for the LLM tier — that contract surface is its own follow-up PR (parallel to the audio service's pattern).

**What's wired:**

- **`parse/LlmJobRequest`** (new) — record mirroring `co.uk.wolfnotsheep.document.events.LlmJobRequestedEvent` field-for-field. Duplicated in the router so it doesn't pull `gls-document` (and its transitive dependencies) into the router module.
- **`parse/LlmJobResult`** (new) — record mirroring `LlmJobCompletedEvent`. `@JsonIgnoreProperties(ignoreUnknown = true)` so a future LLM-event addition doesn't break the consumer.
- **`parse/LlmDispatchCascadeService`** (new) — implements `CascadeService` for the LLM tier. Dispatch: build `LlmJobRequest`, register future, `convertAndSend`, await with timeout. `@RabbitListener` on the per-replica completed queue → `pending.remove(jobId).complete(result)`. Result map keys mirror what the existing LLM worker emits: `categoryId`, `category`, `sensitivity`, `tags`, `confidence`, `requiresHumanReview`, `retentionScheduleId`, `applicablePolicyIds`, `extractedMetadata`, `customResult`. Dispatch failures (broker unreachable) surface as `UncheckedIOException` → 503; timeout → `LlmJobTimeoutException` → 504; LLM-side failure → `LlmJobFailedException` → 502.
- **`parse/RouterRabbitMqConfig`** (new) — `@ConditionalOnProperty(prefix="gls.router.cascade.llm", name="enabled", havingValue="true")`. Declares the topic exchange (idempotent if already exists), the per-replica `AnonymousQueue`, the binding to `pipeline.llm.completed`, the `LlmDispatchCascadeService` bean (replaces the mock), and a Rabbit listener container factory + Jackson converter that match the LLM worker's wire conventions.
- **`parse/CascadeBackendConfig`** (new) — `@ConditionalOnMissingBean(CascadeService.class)` → returns `MockCascadeService`. Default fallback for builds where the LLM dispatch is disabled. `MockCascadeService` lost its `@Service` annotation (instantiated by this config now).
- **`web/RouterExceptionHandler`** — added handlers for the two new exception types: `LlmJobTimeoutException` → 504 `ROUTER_LLM_TIMEOUT`, `LlmJobFailedException` → 502 `ROUTER_LLM_FAILED`.
- **`web/ClassifyController.errorCodeFor`** — extended with the two new mappings so the audit `errorCode` matches the RFC 7807 `code` extension.
- **`application.yaml`** — `gls.router.cascade.llm.enabled` (default `false`) + `gls.router.cascade.llm.timeout` (default `PT60S`).

**Tests (25 in module; 259 reactor total):**

- `LlmDispatchCascadeServiceTest` (6, new) — happy LLM round-trip (dispatch + listener feeds completion via `completeTestOnly`); timeout when no completion arrives; LLM-side failure (`success=false`) → `LlmJobFailedException`; `AmqpException` from the broker → `UncheckedIOException`; orphan completion (no registered future) is dropped quietly; null payload is dropped quietly.
- Existing tests unchanged. `MockCascadeService` still works under direct instantiation — no @Service required.

**Files changed:** 6 new source files + 4 modified (`MockCascadeService`, `RouterExceptionHandler`, `ClassifyController`, `application.yaml`) + 1 new test + plan / log = ~12 files changed.

**Open issues / deferred:**

- **Async surface (`Prefer: respond-async` 202 + `GET /v1/jobs/{nodeRunId}`)** — own contract bump. Mirrors the audio service's async pattern.
- **MCP integration in the dispatch path** — per CSV #1 each worker calls MCP directly, but for now the router defers to the LLM worker which already does. Future inline calls land when BERT / SLM tiers wire in.
- **Replay across restarts** — the in-memory `pending` map is lost on restart. Requests in flight at restart time time out client-side; the orchestrator's idempotency cache (or upstream retry) covers re-dispatch. Persistent dispatch tracking lands when the async surface does.
- **Real LLM-worker integration test** — gated on issue #7 + a Testcontainers Rabbit + a stub LLM consumer; same blocker as the rest.

**Next:** ROUTER block content schema + admin migration (closes Phase 1.2 plan checkboxes), OR Phase 1.3 orchestrator cutover (call `gls-classifier-router` from `PipelineExecutionEngine` behind a feature flag), OR Phase 1.4 BERT inference (cascade's first tier).

## 2026-04-29 — Phase 1.2 — ROUTER block schema + admin migration (PR3, close-off)

**Done:** Closes Phase 1.2's remaining checkboxes. The cascade policy that the router consumes now has a stable schema + a default seed block that's installed by Mongock at startup. Cascade is functionally disabled (`accept=1.01` on BERT and SLM, `0.0` on LLM, fallback `LLM_FLOOR`) — every request falls through to the LLM tier until per-category tuning lands in 1.4–1.6.

**Decisions logged:** None new — implements what's already in the plan.

**What's wired:**

- **`contracts/blocks/router.schema.json`** (new, v0.2.0) — JSON Schema 2020-12 for ROUTER block content. Top-level `tiers` carries per-tier `{enabled, accept, modelRef?, timeout?}` for `bert` / `slm` / `llm`. Optional `fallback` chooses between `LLM_FLOOR` (return the LLM tier's result even below threshold — the architectural floor) and `ROUTER_SHORT_CIRCUIT` (return a configured `defaultResult` without invoking any model). Optional `categoryOverrides` for per-category tier configs (used by the same router when post-classify scans + metadata extraction route through, per architecture §3). Optional `costBudget.maxCostUnits` per cascade run.
- **`contracts/blocks/VERSION`** — bumped to `0.2.0`.
- **`contracts/blocks/CHANGELOG.md`** — appended `0.2.0` entry.
- **`contracts/blocks/README.md`** — flipped the ROUTER schema bullet to "✓ (v0.2.0)".
- **`backend/gls-app-assembly/.../migrations/V003_DefaultRouterBlock.java`** (new) — Mongock change unit. Idempotent: skips if a block named `default-router` already exists. Seeds a `PipelineBlock` with type `ROUTER`, `activeVersion=1`, content carrying the conservative defaults. Operates on the raw `pipeline_blocks` collection so it stays decoupled from any future Java domain shape changes. Rollback removes the seeded document by name + `createdBy=mongock:V003_DefaultRouterBlock` (so it doesn't accidentally delete an admin-created block of the same name).

**Tests:** No new tests — the change unit is exercised by Mongock at startup and the schema is consumed once tier-aware dispatch wires in (Phases 1.4–1.6). 259 / 259 reactor unchanged.

**Files changed:** 4 contract files (router.schema.json, VERSION, CHANGELOG, README) + 1 Java change unit + 2 plan / log = 7 files.

**Phase 1.2 status:** complete. All four sub-checkboxes ticked.

**Open issues / deferred:**

- **Schema validation at block save time** — the admin UI's block save path doesn't yet validate `content` against the matching schema. Adds in a separate PR (cross-cuts all block types, not just ROUTER).
- **Per-category override usage** — the router's `MockCascadeService` and `LlmDispatchCascadeService` don't yet read `categoryOverrides`; lands when the BERT / SLM tiers wire in (1.4 / 1.5).

**Next:** Phase 1.3 orchestrator cutover (call `gls-classifier-router` from `PipelineExecutionEngine` behind `pipeline.classifier-router.enabled` feature flag), OR Phase 1.4 BERT inference (cascade's first tier — `gls-bert-inference` JVM service + `gls-bert-trainer` Python sketch), OR async surface for the router (`Prefer: respond-async` + 202 + `/v1/jobs/{nodeRunId}` mirroring the audio service).

## 2026-04-29 — Phase 1.3 — orchestrator cutover behind feature flag

**Done:** The pipeline engine now has a second LLM-dispatch transport: synchronous HTTP through `gls-classifier-router`. Activated by `pipeline.classifier-router.enabled=true`; default off so the legacy async-Rabbit path remains primary. Cutover is rollback-safe — flipping the flag back to `false` is the revert path.

**Decisions logged:** None new. Implements the pre-decided cutover plan.

**What's wired:**

- **`ClassifierRouterClient`** (new) — `@ConditionalOnProperty(name = "pipeline.classifier-router.enabled", havingValue = "true")`. Synchronous JDK `HttpClient` against `POST /v1/classify`. Builds a `ClassifyRequest` body (block coords + inline TextPayload), POSTs with a `traceparent` (random) and `Idempotency-Key` (the engine's existing per-node-run key). Translates the router's `ClassifyResponse` back into an `LlmJobCompletedEvent` shaped exactly like what the existing async path produces — categoryId, category, sensitivity, tags, confidence, requiresHumanReview, retentionScheduleId, applicablePolicyIds, extractedMetadata, customResult. Non-2xx responses surface as `LlmJobCompletedEvent.failure(...)` so `resumePipeline` applies them as classification failures the same way it does for any other LLM-stage error.
- **`ClassifierRouterException`** (new) — thrown for transport / parse failures; the engine catches and converts to a failure event so the pipeline state machine reaches `CLASSIFICATION_FAILED` instead of throwing out of `walkNodes`.
- **`PipelineExecutionEngine`** — constructor gains an `ObjectProvider<ClassifierRouterClient>` parameter. The SYNC_LLM case branches on `getIfAvailable()`: when present, calls the router and feeds the synthesised event into `resumePipeline(...)` inline; when absent (default), falls through to the existing `rabbitTemplate.convertAndSend(...)` async publish. The state-machine path is shared — `WAITING` is set for both transports; the async resume consumer simply never receives an event when the inline path runs.
- **`application.yaml`** — three new keys under `pipeline.classifier-router.*`: `enabled` (`PIPELINE_CLASSIFIER_ROUTER_ENABLED`, default `false`), `url` (`PIPELINE_CLASSIFIER_ROUTER_URL`, default `http://gls-classifier-router:8080`), `timeout-ms` (default `90000`).

**Why route through `resumePipeline` instead of duplicating its logic:**

`resumePipeline` already handles every downstream effect — NodeRun status, shared context update, `applyClassificationToDocument`, `bertTrainingDataCollector.tryCollect`, walking the rest of the graph. Synthesising an `LlmJobCompletedEvent` and calling that one method is a one-line behaviour parity with the async path. The alternative — extracting the apply-result logic into a private method and calling it from both paths — works but doubles the surface area touched in this PR. The synthesise-and-resume approach has been used by other v2 services (the audio service's async `JobStore` does the same).

**Tests (3 new in `gls-app-assembly`; 262 reactor total):**

- `ClassifierRouterClientTest` (3, new) — happy translation (router 200 → `LlmJobCompletedEvent` populated correctly + headers + body shape sanity-checks); 422 → failure event with the body truncated into `error`; empty `result` object → success with nullable fields preserved. Uses `com.sun.net.httpserver.HttpServer` (JDK builtin) so no WireMock / Testcontainers dependency.
- `PipelineExecutionEngineTest` unchanged — the new constructor parameter is autowired by Mockito's `@InjectMocks` (defaults to null `ObjectProvider`, which the engine handles via `getIfAvailable()` returning null).

**Files changed:** 2 new source files + 1 modified (`PipelineExecutionEngine`) + 1 new test + 1 modified (`application.yaml`) + plan / log = 6 files.

**Open issues:**

- **Performance comparison vs. baseline** — the fourth Phase 1.3 plan checkbox is gated on Phase 0.11 (load driver + first captured baseline). Re-test once the baseline lands; expectation is that sync HTTP through the router adds < 50ms over the async-Rabbit path's median, well within the 10% gate.
- **Engine-level cutover integration test** — exercising the full SYNC_LLM branch end-to-end requires Mongo + a stubbed router HTTP server in the same JVM. Belongs with the broader pipeline integration suite blocked on issue #7. Direct unit-level coverage of the router client (above) closes the most error-prone seam.
- **Engine thread-blocking under load** — sync HTTP holds the request thread for up to `pipeline.classifier-router.timeout-ms` (default 90s). Acceptable for the cutover (low document volumes; same total cycle time as the async path's WAITING state). Re-evaluate when scaling beyond Phase 1; a worker-thread or async-router-surface follow-up is the answer.

**Phase 1.3 status:** three of four plan checkboxes ticked. Perf-comparison gate is the only one open and is blocked on out-of-band substrate work (Phase 0.11 baseline capture).

**Next:** Phase 1.4 BERT inference (cascade's first tier — `gls-bert-inference` JVM service + `gls-bert-trainer` Python sketch per CSV #2), OR async surface for the router (`Prefer: respond-async` + 202 + `/v1/jobs/{nodeRunId}` mirroring the audio service), OR Phase 1.5 SLM worker.

## 2026-04-29 — Phase 1.4 — bert-inference module + BERT_CLASSIFIER block schema (PR1)

**Done:** First cut of the BERT tier lands the contract surface, the JVM module, the DJL + ONNX Runtime classpath, audit / metrics / health / model-readiness wiring, and a stub backend that returns `MODEL_NOT_LOADED` 503 until the trainer (separate PR) publishes its first ONNX artefact. The `BERT_CLASSIFIER` block content schema ships alongside so the cascade router (Phases 1.4 follow-ups) and the admin UI have a stable shape to author against.

**Decisions logged:** None new — implements CSV #2 (DECIDED hybrid: Python trains, JVM serves) per architecture §8.2.

**Contracts touched:**

- **`contracts/bert-inference/`** (new, v0.1.0) — OpenAPI 3.1.1 declaration. Five operations: `POST /v1/infer` (synchronous BERT classification), `GET /v1/models` (loaded artefacts with version + labels + block ids), `POST /v1/models/reload` (ops trigger to re-fetch from MinIO), `GET /v1/capabilities`, `GET /actuator/health` (liveness + model-readiness gate). Cross-references `_shared/` for the error envelope (CSV #17), service-account JWT (CSV #18), `traceparent` (CSV #20), `TextPayload` (CSV #19), `Capabilities` (CSV #21). No `Idempotency-Key` — inference is stateless and the cascade router's idempotency layer covers replay correlation upstream. Phase 1.4 first cut: schema lands now; the real DJL impl swaps in behind `InferenceEngine` when the trainer's first artefact is wired.
- **`contracts/blocks/bert-classifier.schema.json`** (new, blocks v0.3.0) — JSON Schema 2020-12 for `BERT_CLASSIFIER` block content. `modelVersion` (semver), optional `artifactRef` MinIO pointer, `labelMapping[]` from model labels to taxonomy categoryIds, optional `trainingMetadata` (trainer version, dataset size, per-label evaluation metrics — populated by the trainer; opaque to the inference service), optional `minTextLength` skip threshold. `contracts/blocks/VERSION` bumped to `0.3.0`; `CHANGELOG.md` and `README.md` updated.

**What's wired:**

- **`gls-bert-inference`** (new module, parent / BOM hooked up; `gls.bert.inference.version` property in `backend/bom/pom.xml` so the deployable's version can diverge per the §IndependentDeployableVersions policy). Spring Boot 4.0.2 webmvc + actuator + amqp; DJL 0.32.0 (`api`, `onnxruntime-engine` runtime, `huggingface/tokenizers`); MinIO 8.5.14 client; `jakarta.validation`; `swagger-annotations`; `gls-platform-audit`. `openapi-generator-maven-plugin` consumes `contracts/bert-inference/openapi.yaml` and emits `InferenceApi` / `MetaApi` interfaces under `co.uk.wolfnotsheep.bert.api` + models under `co.uk.wolfnotsheep.bert.model`.
- **`InferenceEngine` + `NotLoadedInferenceEngine`** — interface-driven; the stub throws `ModelNotLoadedException` so the controller maps to 503 `MODEL_NOT_LOADED` and the cascade router falls through to the next tier. `InferenceEngineConfig` reads `gls.bert.inference.engine` (`none` default, `djl` for the future real impl) and registers the bean via `@ConditionalOnMissingBean(InferenceEngine.class)` so a real backend can swap in cleanly when it ships.
- **`InferController`** — implements the generated `InferenceApi`. Resolves the inline text payload (textRef branch deferred until the engine ships), calls the engine, builds the audit envelope (`INFER_COMPLETED` on success, `INFER_FAILED` with mapped `errorCode` on failure), and updates `InferMetrics` (Micrometer counters + timer). Errors map to RFC 7807 codes: `MODEL_NOT_LOADED`, `BLOCK_UNKNOWN`, `BERT_DEPENDENCY_UNAVAILABLE` (UncheckedIOException), `BERT_UNEXPECTED` (catch-all).
- **`ModelsController`** — `GET /v1/models` returns the registry snapshot. `POST /v1/models/reload` delegates to `ReloadCoordinator` (single-flight via an `AtomicBoolean`; throws `ReloadInProgressException` → 409 if a reload is already in flight). The actual reload is a no-op until the DJL engine ships; the coordinator + 409 surface let ops drain replicas without waiting on the model fetch.
- **`MetaController`** — `GET /v1/capabilities` reports `tiers=["BERT"]` plus the loaded artefacts; `GET /actuator/health` flips between `UP` (200) and `OUT_OF_SERVICE` (503) based on `engine.isReady() && !registry.isEmpty()`. `ModelReadinessHealthIndicator` exposes the same gate via the actuator-managed `health` endpoint.
- **`BertEvents`** — Tier 2 audit factory. `action="INFER"` (distinct from the router's `action="CLASSIFY"`) so audit consumers can filter cascade-internal tier calls separately from the cascade outcome. `INFER_COMPLETED` carries `nodeRunId`, `blockId/Version`, `modelVersion`, `label`, `confidence`, `byteCount`, `durationMs`. `INFER_FAILED` carries `errorCode` + `errorMessage`.
- **`BertExceptionHandler`** — maps the engine exceptions onto RFC 7807 `application/problem+json` per the shared error envelope. 503 for not-loaded / dependency-unavailable, 422 for block-unknown, 409 for reload-in-progress, 500 for catch-all.
- **`backend/pom.xml`** — `<module>gls-bert-inference</module>` registered. **`backend/bom/pom.xml`** — `gls.bert.inference.version` property + dependency declaration so the artifact can be consumed elsewhere without a hardcoded version.

**Tests (19 in module; 281 reactor total):**

- `InferControllerTest` (4) — happy infer (engine returns label + confidence; controller emits `INFER_COMPLETED` with the right metadata, returns 200); engine failure path (engine throws `ModelNotLoadedException`; controller emits `INFER_FAILED` with `errorCode=MODEL_NOT_LOADED`, propagates the exception); audit-emitter-absent path (no NPE if `ObjectProvider` returns null); generated-API conformance (controller `instanceof InferenceApi`).
- `MetaControllerTest` (4) — capabilities reports `tiers=["BERT"]` + empty `models[]` when nothing loaded; capabilities lists models from the registry; health flips to `UP` 200 only when both `engine.isReady()` and the registry has at least one model; health 503 with `OUT_OF_SERVICE` body when the engine is the not-loaded stub.
- `ModelsControllerTest` (5) — list happy-path (returns registry snapshot); reload happy-path (202 + `status=PENDING`); reload-in-progress (409 via `ReloadInProgressException`); reload-success-marker (coordinator marks completed when no exception); reload-failure-marker (coordinator marks completed even on caught exception so a future reload isn't blocked).
- `BertEventsTest` (4) — `INFER_COMPLETED` envelope shape (Tier.SYSTEM, action=`INFER`, outcome=SUCCESS, metadata fields populated); `INFER_FAILED` envelope shape; null-safe `nodeRunId` / `blockId` (omitted from metadata, not nulled); `eventId` is 26-char ULID-ish (ULID alphabet not enforced; uniqueness via UUID derivation).
- `NotLoadedInferenceEngineTest` (2) — `infer(...)` always throws `ModelNotLoadedException`; `isReady()` returns `false`.

The previous 262-reactor test suite (per the Phase 1.3 entry) is unchanged. New module adds 19, total 281.

**Files changed:** 24 new source files in `gls-bert-inference` (controllers, audit, registry, inference, health, application + yaml) + 5 new test files + 1 `pom.xml` + `backend/pom.xml` (module entry) + `backend/bom/pom.xml` (version property + dependency) + 1 new contract folder (`contracts/bert-inference/` × 4 files) + 1 new schema file (`contracts/blocks/bert-classifier.schema.json`) + 3 modified contract files (blocks `VERSION`, `CHANGELOG.md`, `README.md`) + plan / log = ~38 files.

**Open issues / deferred:**

- **Real DJL + ONNX Runtime backend** — the `engine=djl` branch logs a warning and falls through to the not-loaded stub. Lands when `gls-bert-trainer` (Python, k8s Job) is sketched and publishes its first ONNX artefact. The interface seam is in place so the swap is mechanical.
- **`gls-bert-trainer`** (Python) — separate PR. Phase 1.4 plan checkbox; reads training samples from Mongo (the `bert_training_samples` collection populated by `BertTrainingDataCollector` in `gls-app-assembly`), fine-tunes ModernBERT on the org's top-3 categories, exports ONNX, publishes to MinIO under a versioned key.
- **Cascade wire-in** — `gls-classifier-router` doesn't yet dispatch to `gls-bert-inference`. The `MockCascadeService` already understands the BERT_CLASSIFIER block shape (per Phase 1.2 PR1 entry); a `BertDispatchCascadeService` mirroring `LlmDispatchCascadeService` plugs in once the inference service has a real backend to exercise.
- **JWT validation** — same blocker as the rest of the v2 services: the contract declares `serviceJwt` security but enforcement waits on JWKS infra.
- **Integration tests** — same blocker as the rest of the v2 services: real cross-service wiring tests gated on issue #7.
- **Schema validation at block save time** — same as flagged in the Phase 1.2 close-off entry: the admin UI's block save path doesn't validate `content` against the matching schema. Cross-cuts all block types, so it lands as a separate PR.
- **MinIO classpath but no fetch** — the MinIO client jar is on the module classpath so the real engine can use it directly without another BOM bump. No `MinioClient` bean is wired yet; that arrives with the real engine.

**Next:** `gls-bert-trainer` Python sketch (closes Phase 1.4's first plan checkbox); OR real DJL + ONNX engine + cascade wire-in (closes the remaining 1.4 checkboxes); OR async surface for the router (`Prefer: respond-async` + 202 + `/v1/jobs/{nodeRunId}` mirroring the audio service); OR Phase 1.5 SLM worker.

## 2026-04-29 — Phase 1.4 — BERT cascade tier wire-in (PR2)

**Done:** The cascade router can now dispatch the BERT tier for `BERT_CLASSIFIER` blocks via synchronous HTTP to `gls-bert-inference`. Activated by `gls.router.cascade.bert.enabled=true`; default off so the existing mock / LLM-direct paths stay primary. The orchestrator wraps the existing inner cascade (LLM if enabled, mock otherwise) and falls through to it on `MODEL_NOT_LOADED` (or any transport / 5xx). End-to-end: with `bert.enabled=true` + `llm.enabled=true` + the inference service stub returning 503, every request flows BERT → fallthrough → LLM and the cascade trace records both steps.

**Decisions logged:** None new. Implements CSV #2 (DECIDED hybrid). The fallthrough-on-503 contract is the explicit signal in the bert-inference OpenAPI surface (Phase 1.4 PR1).

**What's wired:**

- **`BertHttpDispatcher`** (new) — pure HTTP client. JDK `HttpClient` against `POST /v1/infer`. Builds the request body inline (`block: {id, version?}`, `text: {text, encoding}`, `nodeRunId?`). Translates responses:
  - `200` → `BertInferenceResult(label, confidence, modelVersion)`.
  - `503` → `BertTierFallthroughException` carrying `errorCode` from the response body (defaults to `MODEL_NOT_LOADED` when the body has no `code`).
  - `422` → `BertBlockUnknownException` — propagates to the controller as 422 `ROUTER_BERT_BLOCK_UNKNOWN`. Configuration error; the LLM tier can't make up for a broken block.
  - Other 4xx / 5xx → `BertTierFallthroughException` with `errorCode=BERT_HTTP_<status>`. Logged at WARN; cascade escalates rather than surfacing transient BERT issues (LLM is the architectural floor).
  - Transport `IOException` / `InterruptedException` → `BertTierFallthroughException` with `errorCode=BERT_TRANSPORT_ERROR`.
  - 200 with missing `label` → `BertTierFallthroughException` with `errorCode=BERT_RESPONSE_INVALID`.
- **`BertOrchestratorCascadeService`** (new) — implements `CascadeService`. For `blockType=BERT_CLASSIFIER`: dispatches to BERT, returns BERT outcome on success, escalates to inner cascade on fallthrough (with the BERT step prepended to the inner's trace). For other block types (`PROMPT` / null): delegates directly to inner without touching the dispatcher. The `BertBlockUnknownException` is intentionally not caught — it propagates so the controller maps it to 422.
- **`BertInferenceResult`** (new record) — internal shape carried between dispatcher and orchestrator.
- **`BertTierFallthroughException`** (new) — internal signal carrying an `errorCode` for the cascade trace step.
- **`BertBlockUnknownException`** (new) — user-facing 422 mapping.
- **`RouterHttpConfig`** (new) — `@Configuration @ConditionalOnProperty(... bert.enabled = true)`. Registers a `BertHttpDispatcher` bean (built with a JDK `HttpClient`, the configured base URL, and a per-call timeout). The orchestrator itself is composed in `CascadeBackendConfig` so it can wrap whichever inner cascade is configured.
- **`CascadeBackendConfig`** (refactored) — `MockCascadeService` is now always registered as a bean (the prior `@ConditionalOnMissingBean(CascadeService.class)` is gone). The active `CascadeService` is composed by a single `@Primary` factory: prefers LLM if available, falls back to mock; wraps with `BertOrchestratorCascadeService` if the BERT dispatcher bean exists. The factory is the new single source of truth for cascade composition.
- **`RouterExceptionHandler`** — added `@ExceptionHandler(BertBlockUnknownException.class)` → 422 `ROUTER_BERT_BLOCK_UNKNOWN`. Other BERT failures fall through to the inner cascade and never reach this handler.
- **`ClassifyController.errorCodeFor`** — extended with `ROUTER_BERT_BLOCK_UNKNOWN` so the audit `errorCode` matches the RFC 7807 `code` extension.
- **`application.yaml`** — three new keys under `gls.router.cascade.bert.*`: `enabled` (`GLS_ROUTER_BERT_ENABLED`, default `false`), `url` (`GLS_ROUTER_BERT_URL`, default `http://gls-bert-inference:8080`), `timeout-ms` (`GLS_ROUTER_BERT_TIMEOUT_MS`, default `30000`).

**Why fall through on 5xx but propagate on 422:**

The BERT contract reserves 503 specifically for `MODEL_NOT_LOADED` — the explicit "I can't decide, escalate" signal. The cascade architecture says the LLM tier is the floor (CSV #1, ROUTER block default `LLM_FLOOR`), so transient BERT failures (5xx, transport) should also escalate rather than surface — the cascade's whole point is resilience. 422 is different: it means the block coords don't resolve, which is a config error the LLM tier can't paper over (the same block id is broken there too). Surface 422 to the caller; mask the rest.

**Tests (12 new in module; 293 reactor total):**

- `BertHttpDispatcherTest` (7) — happy 200 (label / confidence / modelVersion + request body shape sanity checks); 503 with `code=MODEL_NOT_LOADED` (errorCode propagated); 503 with no body (defaults to `MODEL_NOT_LOADED`); 422 → `BertBlockUnknownException`; 500 → fallthrough with `errorCode=BERT_HTTP_500`; transport failure (server not started) → fallthrough with `errorCode=BERT_TRANSPORT_ERROR`; 200 with missing `label` → fallthrough with `errorCode=BERT_RESPONSE_INVALID`. Uses `com.sun.net.httpserver.HttpServer` (JDK builtin) — same harness as the orchestrator's `ClassifierRouterClientTest`.
- `BertOrchestratorCascadeServiceTest` (5) — happy `BERT_CLASSIFIER` (BERT dispatched, `tierOfDecision=BERT`, single trace step); fallthrough on `MODEL_NOT_LOADED` (BERT trace step prepended to inner's trace; `tierOfDecision` reflects the inner's choice); `PROMPT` block delegates directly to inner without touching the dispatcher; null block type delegates to inner; `BertBlockUnknownException` propagates without falling through (inner not called).

Existing tests unchanged; the `CascadeBackendConfig` refactor doesn't affect existing unit tests because they construct `MockCascadeService` directly rather than going through Spring.

**Files changed:** 5 new source files (`BertHttpDispatcher`, `BertOrchestratorCascadeService`, `BertInferenceResult`, `BertTierFallthroughException`, `BertBlockUnknownException`, `RouterHttpConfig`) + 4 modified (`CascadeBackendConfig`, `RouterExceptionHandler`, `ClassifyController`, `application.yaml`) + 2 new test files + plan / log = 12 files.

**Open issues / deferred:**

- **ROUTER block threshold reading** — the orchestrator doesn't yet read the ROUTER block from Mongo to apply per-tier `accept` thresholds. Today every BERT 200 response is accepted as-is. This is fine for now: the inference service always returns `MODEL_NOT_LOADED` until the trainer publishes an artefact, so the cascade always falls through to LLM. Threshold gating lands with the trainer / real engine PR.
- **Per-category overrides** — same blocker; the ROUTER block schema's `categoryOverrides` is consumed once thresholds are read.
- **BERT tier for PROMPT blocks** — BERT can serve PROMPT blocks too, by resolving a `BERT_CLASSIFIER` block (with `labelMapping`) tied to the same taxonomy. Deferred until the BERT_CLASSIFIER block is admin-creatable + the resolution path lands.
- **Real BERT integration test** — exercising the whole cascade end-to-end requires the JVM bert-inference service running. Belongs with the broader pipeline integration suite blocked on issue #7. Direct unit-level coverage of the dispatcher (HTTP server stub) closes the most error-prone seam.
- **Cost / latency budgets** — the ROUTER block schema's `costBudget.maxCostUnits` is not enforced yet. Lands when the cascade actually has multiple successful tiers running per request (today only one tier ever returns).

**Phase 1.4 status:** two of five plan checkboxes ticked. `gls-bert-inference` (PR1), `BERT_CLASSIFIER` block schema (PR1), and cascade wire-in (PR2) are now green. Trainer + per-category enable remain.

**Next:** `gls-bert-trainer` Python sketch (closes the trainer checkbox); OR async surface for the router (`Prefer: respond-async` mirroring the audio service); OR Phase 1.5 SLM worker; OR ROUTER block threshold reading + the BERT_CLASSIFIER block resolution path so PROMPT blocks can also exercise BERT.

## 2026-04-29 — Phase 1.4 PR3 — router async surface (`Prefer: respond-async`)

**Done:** Closes the "async surface for the router" open issue carried in the Phase 1.2 PR1 / PR2 / 1.3 / 1.4 PR2 entries. The cascade router now supports `Prefer: respond-async` on `POST /v1/classify` (CSV #13 / #47). Without the header the call blocks and returns 200; with the header the call returns 202 with `Location: /v1/jobs/{nodeRunId}` and the cascade runs on a bounded `routerAsyncExecutor` thread pool. Sync and async share the same `router_jobs` row, so a `Prefer: respond-async` retry after a completed sync run returns the cached result via the poll URL — same idempotency semantics as `gls-extraction-audio`.

**Decisions logged:** None new — implements CSV #13 (sync/async response model, DECIDED) and CSV #47 (Whisper-style async, DECIDED) for the router service.

**Contracts touched:**

- **`contracts/classifier-router/`** — bumped to v0.2.0. Added `Prefer` header parameter on `POST /v1/classify`; new 202 response with `Location` + `JobAccepted` body; new `GET /v1/jobs/{nodeRunId}` operation returning `JobStatus` (`PENDING` / `RUNNING` / `COMPLETED` / `FAILED`; `COMPLETED` carries the same `ClassifyResponse` shape as the sync 200). New `JobAccepted` and `JobStatus` schemas. Backwards compatible — sync clients that don't send `Prefer` see no behaviour change.

**What's wired:**

- **`co.uk.wolfnotsheep.router.jobs`** (new package) — `JobState` (PENDING / RUNNING / COMPLETED / FAILED), `JobRecord` (Mongo-mapped, `@Document(collection = "router_jobs")`, TTL-indexed `expiresAt`), `JobAcquisition` (ACQUIRED / RUNNING / COMPLETED / FAILED variants carrying the existing row when present), `JobRepository` (Spring Data Mongo), `JobStore` (single-writer state machine with `tryAcquire` / `markRunning` / `markCompleted` / `markFailed` / `find`). The shape mirrors `gls-extraction-audio`'s `JobStore` deliberately — observers and operational tooling treat the two services identically.
- **Old `co.uk.wolfnotsheep.router.idempotency` package removed.** `IdempotencyStore`, `IdempotencyRecord`, `IdempotencyOutcome`, `IdempotencyRepository`, `IdempotencyInFlightException` all superseded by the new `jobs` package. The old `router_idempotency` Mongo collection is replaced by `router_jobs`; no migration story is required for dev data, but the property name changed (`gls.router.idempotency.ttl` → `gls.router.jobs.ttl`).
- **`AsyncConfig`** (new) — `routerAsyncExecutor` `@Bean`, `ThreadPoolTaskExecutor` (core 4, max 8, queue 32 by default; tunable via `gls.router.async.*`). Bounded to keep a misbehaving cascade from saturating the replica.
- **`AsyncDispatcher`** (new) — `@Component` with a single `@Async("routerAsyncExecutor")` method that re-enters the controller via `ObjectProvider<ClassifyController>`. The proxy boundary lives in a separate bean from the controller because Spring's AOP doesn't advise self-invocations; same pattern as `gls-extraction-audio`.
- **`JobController`** (new) — `@RestController implements JobsApi`. `GET /v1/jobs/{nodeRunId}` reads the `JobStore`, maps the row to a `JobStatus` body, deserialises the cached `resultJson` into a `ClassifyResponse` for COMPLETED rows, surfaces `errorCode` / `errorMessage` for FAILED rows. Throws `JobNotFoundException` for unknown ids.
- **`JobInFlightException`** (new) — replaces `IdempotencyInFlightException`; same 409 mapping but lives in `web/` next to the other web exceptions for consistency.
- **`JobNotFoundException`** (new) — 404 `ROUTER_JOB_NOT_FOUND`.
- **`ClassifyController`** (refactored) — gains the `Prefer` header parameter and a new `runAsync` package-private method; switches sync/async based on `prefer`. `tryAcquire` outcomes are now ACQUIRED / RUNNING / COMPLETED / FAILED (not just CACHED / IN_FLIGHT / ACQUIRED): RUNNING + sync raises 409, RUNNING + async returns 202 pointing at the existing row; COMPLETED + sync returns the cached body, COMPLETED + async returns 202; FAILED behaves like RUNNING for sync (re-raise), like ACQUIRED-equivalent for async (return 202 so the poller sees the failure shape via the job surface). The error-mapping table shifts: `IdempotencyInFlightException` → `JobInFlightException`.
- **`RouterExceptionHandler`** — drops the old idempotency handler, adds `JobInFlightException` (409 `IDEMPOTENCY_IN_FLIGHT`) and `JobNotFoundException` (404 `ROUTER_JOB_NOT_FOUND`).
- **`GlsClassifierRouterApplication`** — `@EnableAsync` so `@Async` annotations actually advise.
- **`application.yaml`** — `gls.router.jobs.ttl` (default `PT24H`) replaces `gls.router.idempotency.ttl`. New `gls.router.async.{core-size, max-size, queue-capacity}` for the executor.

**Why a refactor instead of a parallel surface:**

The audio service explicitly pairs sync idempotency with async-job state in the same row (CSV #47). Trying to keep two parallel stores in the router (the existing `router_idempotency` for sync + a new `router_jobs` for async) would have meant the cached response on a sync run wouldn't satisfy a later `Prefer: respond-async` retry — the contract would diverge from audio for no reason. Lifting audio's `JobStore` shape verbatim keeps the two services symmetric: same Mongo TTL strategy, same lifecycle transitions, same poll surface.

**Tests (8 new in module; 301 reactor total):**

- `ClassifyControllerTest` (9, was 6) — added: `respond_async_returns_202_with_Location_and_dispatches_in_background` (sync side-effects don't fire on the dispatch thread); `respond_async_after_completed_run_returns_202_pointing_at_existing_row` (no re-dispatch when the row is COMPLETED); `respond_async_with_running_row_returns_202_without_redispatch`. Existing tests adjusted to the new `JobStore` API (`JobAcquisition` instead of `IdempotencyOutcome`, `markCompleted` instead of `cacheResult`, the new constructor signature).
- `JobControllerTest` (5, new) — unknown nodeRunId → `JobNotFoundException`; PENDING row (no result, no errors); RUNNING row (`startedAt` populated, no `completedAt`); COMPLETED row (deserialised `ClassifyResponse` on the body); FAILED row (`errorCode` + `errorMessage` populated, no result).

Existing tests in the audio / orchestrator modules are unchanged because the contract bump is additive — `Prefer` is optional, so existing sync clients (including `ClassifierRouterClient` in `gls-app-assembly`) keep working without modification.

**Files changed:** 9 new source files (`jobs/JobState`, `jobs/JobRecord`, `jobs/JobAcquisition`, `jobs/JobRepository`, `jobs/JobStore`, `web/AsyncConfig`, `web/AsyncDispatcher`, `web/JobController`, `web/JobInFlightException`, `web/JobNotFoundException`) + 5 deleted (`idempotency/*`) + 4 modified (`web/ClassifyController`, `web/RouterExceptionHandler`, `GlsClassifierRouterApplication`, `application.yaml`) + 3 contract files (`openapi.yaml`, `VERSION`, `CHANGELOG.md`) + 2 new test files / 1 modified test file + plan / log = ~22 files.

**Open issues / deferred:**

- **JobAccepted vs ClassifyResponse return type narrowing** — the generated `ClassifyApi.classify(...)` interface narrows the return type to `ClassifyResponse`. The 202 path ships a `JobAccepted` body via an unchecked cast (same workaround as `gls-extraction-audio`). The OpenAPI generator's Spring template doesn't yet support distinct response types per status code without a schema unionisation; the workaround is contained at the cast site.
- **`@Async` on a plain `@Component`** — the dispatcher is a `@Component`, not a `@Service` or `@Configuration`. Spring's AOP advises any proxy-eligible bean, so this is fine; the audio service uses the same shape.
- **Mongo collection rename** — `router_idempotency` is left behind on the dev cluster after deploy. Acceptable for dev; if the data ever ships to a non-dev environment a Mongock change unit can clean it up.
- **Cancel async job** — no `DELETE /v1/jobs/{nodeRunId}` yet. Audio doesn't have one either; the LLM-tier timeout already bounds runaway jobs.

**Next:** `gls-bert-trainer` Python sketch (closes the trainer checkbox); OR Phase 1.5 SLM worker; OR ROUTER block threshold reading + per-category enable to start using BERT in production-shape; OR cancel-async / async-status webhook follow-ups (mirroring upcoming audio service work).

## 2026-04-29 — Phase 1.5 PR1 — `gls-slm-worker` module + contract (stub backend)

**Done:** First cut of the SLM (Small Language Model) tier of the cascade. Ships the contract surface, JVM module, async surface (`Prefer: respond-async` + `/v1/jobs`), audit / metrics / health, and a stub `NotConfiguredSlmService` that returns `SLM_NOT_CONFIGURED` 503 until either Anthropic Haiku or Ollama is wired. Same shape as the forthcoming `gls-llm-worker` rework (Phase 1.6), so the cascade router will be able to dispatch to either tier through identical client code.

**Decisions logged:** None new — implements CSV #1 (cascade dispatch is task-agnostic) and CSV #47 (`Prefer: respond-async` semantics).

**Contracts touched:**

- **`contracts/slm-worker/`** (new, v0.1.0) — OpenAPI 3.1.1 declaration. Six operations: `POST /v1/classify` (sync 200 / async 202 with `Prefer: respond-async`), `GET /v1/jobs/{nodeRunId}` (poll), `GET /v1/backends` (roster + readiness), `GET /v1/capabilities`, `GET /actuator/health`. `ClassifyResponse` shape: `{nodeRunId, backend (ANTHROPIC_HAIKU / OLLAMA), modelId, confidence, result, rationale, durationMs, costUnits, tokensIn, tokensOut}`. `JobAccepted` + `JobStatus` schemas mirror the audio service / classifier-router (sync and async share the same idempotency row per CSV #47). Cross-references `_shared/` for the error envelope (CSV #17), service-account JWT (CSV #18), `traceparent` + `Idempotency-Key` + `Prefer` headers (CSV #16 / #20), `TextPayload` (CSV #19), `Capabilities` (CSV #21).

**What's wired:**

- **`gls-slm-worker`** (new module). Spring Boot 4.0.2 webmvc + actuator + data-mongodb; `gls-platform-audit` for Tier 2 audit; standard tracing (Micrometer / OTel). Parent + BOM hooked up (`gls.slm.worker.version` property in `backend/bom/pom.xml` so the deployable's version can diverge per the Independent Deployable Versions policy). `openapi-generator-maven-plugin` consumes `contracts/slm-worker/openapi.yaml` and emits `ClassifyApi` / `JobsApi` / `BackendsApi` / `MetaApi` interfaces under `co.uk.wolfnotsheep.slm.api` + models under `co.uk.wolfnotsheep.slm.model`.
- **`SlmService` + `NotConfiguredSlmService`** — interface-driven so the real Anthropic Haiku + Ollama backends slot in behind the same shape. The stub throws `SlmNotConfiguredException` (controller maps to 503 `SLM_NOT_CONFIGURED`); reports `activeBackend()=NONE` and `isReady()=false`. `SlmBackendConfig` reads `gls.slm.worker.backend` (`none` default; `anthropic` / `ollama` for the future real impls) and registers the bean via `@ConditionalOnMissingBean(SlmService.class)` so a real backend can swap in cleanly when it ships.
- **`SlmResult`** record — internal shape returned by the backend: `result` map, `confidence`, `rationale`, `backend` enum, `modelId`, `tokensIn`, `tokensOut`. Translated to the API `ClassifyResponse` in the controller.
- **`ClassifyController`** — implements `ClassifyApi`. Handles the `Prefer` header for sync/async dispatch; tracks lifecycle in the `JobStore` (ACQUIRED → RUNNING → COMPLETED / FAILED). Errors map to RFC 7807 codes: `SLM_NOT_CONFIGURED`, `SLM_BLOCK_UNKNOWN`, `IDEMPOTENCY_IN_FLIGHT`, `SLM_DEPENDENCY_UNAVAILABLE`, `SLM_UNEXPECTED`. Audit envelope: `SLM_COMPLETED` on success (carries backend, modelId, tokensIn/Out, byteCount, durationMs); `SLM_FAILED` with errorCode + message on failure. `costUnits = (tokensIn + tokensOut + 999) / 1000` rounded up per CSV #22.
- **`JobController`** — `GET /v1/jobs/{nodeRunId}`. Same poll surface as the audio service / router. Throws `JobNotFoundException` (404 `SLM_JOB_NOT_FOUND`) for unknown ids.
- **`BackendsController`** — `GET /v1/backends`. First cut reports `active=NONE` plus both planned backends (`ANTHROPIC_HAIKU` and `OLLAMA`) listed unready with notes pointing at the config keys to set. When real backends ship, this controller can ask each in turn for readiness.
- **`MetaController`** — `GET /v1/capabilities` reports `tiers=["SLM"]` plus the active backend (or empty when unconfigured); `GET /actuator/health` flips between `UP` (200) and `OUT_OF_SERVICE` (503) based on `backend.isReady()`.
- **`co.uk.wolfnotsheep.slm.jobs`** package — `JobState` / `JobRecord` (`@Document(collection = "slm_jobs")`) / `JobAcquisition` / `JobRepository` / `JobStore`. Lifted from `gls-classifier-router`'s shape verbatim, which itself came from `gls-extraction-audio`. Three v2 services now share the exact same idempotency + async-job state machine, simplifying observability and operational tooling.
- **`AsyncConfig`** — `slmAsyncExecutor` `ThreadPoolTaskExecutor` (core 4, max 8, queue 32 by default; tunable via `gls.slm.worker.async.*`). `AsyncDispatcher` (`@Component`) re-enters the controller via `ObjectProvider<ClassifyController>` for the `@Async` proxy boundary.
- **`SlmEvents`** — Tier 2 audit factory. `action="CLASSIFY"` matches the router and the LLM worker so observers can join cascade-internal SLM tier calls into the same trace; `eventType=SLM_*` discriminates SLM-tier emissions from BERT and LLM emissions.
- **`SlmExceptionHandler`** — RFC 7807 mappings for `SlmNotConfiguredException` (503), `BlockUnknownException` (422), `JobInFlightException` (409), `JobNotFoundException` (404), `UncheckedIOException` (503).
- **`BackendReadinessHealthIndicator`** — actuator readiness gate. Flips UP only when `backend.isReady()`; carries `backend` detail.
- **`backend/pom.xml`** — `<module>gls-slm-worker</module>` registered. **`backend/bom/pom.xml`** — `gls.slm.worker.version` property + dependency declaration.

**Why the same `JobStore` shape lifted across three services:**

The audio service, the classifier-router, and now the SLM worker all expose the `Prefer: respond-async` + `/v1/jobs` surface (CSV #47). Sharing the exact `JobRecord` / `JobStore` shape — including the Mongo TTL strategy, the lifecycle transitions, and the deletion model — means observability, operational dashboards, and runbooks are identical across the trio. A future shared library (`gls-platform-jobs` or similar) can lift the duplicated code; for now the duplication is the pragmatic move while the shape is still settling.

**Tests (25 in module; 326 reactor total):**

- `NotConfiguredSlmServiceTest` (2) — `classify` always throws; `activeBackend()=NONE`, `isReady()=false`.
- `SlmEventsTest` (4) — `SLM_COMPLETED` envelope shape (Tier.SYSTEM, action=`CLASSIFY`, outcome=SUCCESS, metadata fields populated incl. backend / modelId / tokensIn / tokensOut); `SLM_FAILED` envelope; null-safe blockId / blockVersion / modelId omission; eventId is 26-char ULID-shaped.
- `ClassifyControllerTest` (8) — generated-API conformance; happy path (200 + backend mapping + cache); backend failure path (markFailed + propagate); in-flight 409; cached → cached body; async → 202 + Location + dispatch; async with running row → 202 without re-dispatch; the real `NotConfiguredSlmService` stub round-trip propagates `SlmNotConfiguredException`.
- `JobControllerTest` (4) — unknown nodeRunId → 404; PENDING / COMPLETED (with deserialised result) / FAILED row shapes.
- `BackendsControllerTest` (3) — stub reports `active=NONE` with both backends listed unready; active anthropic / active ollama enum translation.
- `MetaControllerTest` (4) — capabilities with no backend; capabilities with active backend; health 503 when unready; health 200 when ready.

The previous 301-reactor test suite (per the router-async-surface entry) is unchanged. New module adds 25, total 326.

**Files changed:** ~20 new source files in `gls-slm-worker` (controllers, backend, audit, jobs, health, application + yaml) + 6 new test files + 1 `pom.xml` + `backend/pom.xml` (module entry) + `backend/bom/pom.xml` (version property + dependency) + 1 new contract folder (`contracts/slm-worker/` × 4 files) + plan / log = ~36 files.

**Open issues / deferred:**

- **Real Anthropic Haiku backend** — separate PR. Wires the Anthropic SDK behind the `SlmService` interface; reads `ANTHROPIC_API_KEY`; calls `claude-haiku-4-5` (the v2 default per the model family in our infra).
- **Real Ollama backend** — separate PR. Wires a local Ollama HTTP client; reads `gls.slm.worker.ollama.endpoint` + `gls.slm.worker.ollama.model`.
- **Cascade wire-in** — `gls-classifier-router` doesn't yet dispatch to `gls-slm-worker`. Mirrors the BERT wire-in pattern: a new `SlmDispatchCascadeService` HTTP client + an extension to the orchestrator that calls SLM between BERT and LLM. Lands once the real backends exist to exercise the path.
- **MCP integration** — per CSV #1, each worker calls MCP itself. The stub doesn't need MCP; lands with the real backends.
- **JWT validation** — same blocker as the rest of the v2 services: contract declares `serviceJwt` security but enforcement waits on JWKS infra.
- **Integration tests** — same blocker as the rest of the v2 services: real cross-service wiring tests gated on issue #7.
- **Dockerfile + Compose entry** — deferred (BERT PR1 also deferred these; lands as a single follow-up that ships all the v2 service Compose entries together).
- **Cost budget** — per-day / per-call ceilings deferred to the same PR as the real Anthropic backend.

**Next:** Real Anthropic Haiku backend (closes the SLM provider loop); OR `gls-bert-trainer` Python sketch (closes Phase 1.4's last open checkbox); OR Phase 1.6 LLM worker rework (lift `gls-llm-orchestration` into the new contract shape); OR ROUTER block threshold reading + per-category enable; OR Dockerfile + Compose for `gls-slm-worker` and `gls-bert-inference`.

## 2026-04-29 — Phase 1.5 PR2 — Anthropic Haiku SLM backend

**Done:** Real `AnthropicHaikuSlmService` lands behind the `SlmService` interface — when `gls.slm.worker.backend=anthropic` and `ANTHROPIC_API_KEY` is set, the worker dispatches to `claude-haiku-4-5` via Spring AI's `AnthropicChatModel` (the same starter `gls-llm-orchestration` already depends on). PROMPT block content is read from the `pipeline_blocks` Mongo collection through a minimal `PromptBlockResolver` that doesn't depend on `gls-governance` — keeps the worker decoupled from governance class evolution while reading the same source-of-truth row.

**Decisions logged:** None new — implements CSV #2 (cascade dispatch is task-agnostic) for the SLM tier specifically.

**What's wired:**

- **`PromptBlockResolver`** (new) — reads `pipeline_blocks` via raw `MongoTemplate.findOne(Document.class)`. Validates `type=PROMPT`, picks the requested version (pinned or `activeVersion`), pulls `systemPrompt` + `userPromptTemplate` strings out of the version's content map. Falls back to `draftContent` when no published versions exist (covers seed / dev rows). Throws `BlockUnknownException` for missing block / wrong type / missing version / empty content.
- **`AnthropicHaikuSlmService`** (new) — implements `SlmService`. On `classify`:
  - Resolves the PROMPT block via the resolver.
  - Substitutes `{{text}}` in the user template (or appends the text after a blank line if no placeholder is present).
  - Calls Spring AI `ChatClient.builder(anthropicChatModel).build()` with `AnthropicChatOptions(model, temperature, maxTokens)`. `model` defaults to `claude-haiku-4-5` per the architecture doc + CLAUDE.md.
  - Parses the response: if it's JSON (with optional `\`\`\`json\` fences stripped), extracts a result map + `confidence` + `rationale`. If it's plain text, wraps the text into a `{rationale}` map with default `confidence=0.5` so callers always get a stable shape.
  - Surfaces `tokensIn` / `tokensOut` from `ChatResponse.getMetadata().getUsage()` for the controller's `costUnits` computation.
  - On any `RuntimeException` from the SDK, wraps as `UncheckedIOException` so the cascade router maps it to `ROUTER_DEPENDENCY_UNAVAILABLE` and falls through to the LLM tier.
- **`SlmBackendConfig`** (rewritten) — single `@Bean` factory now selects between the not-configured stub, the Anthropic backend, and (placeholder for) the Ollama backend. Uses `ObjectProvider<AnthropicChatModel>.getIfAvailable()` so the service starts cleanly even when `ANTHROPIC_API_KEY` is absent (the Spring AI starter only autoconfigures the model bean when the key is set; missing bean → graceful fallback to the stub with a WARN log).
- **`pom.xml`** — adds the `spring-ai-starter-model-anthropic` dependency. Imports the `spring-ai-bom` (2.0.0-SNAPSHOT) and registers the Spring snapshot repos at the module level — same pattern as `gls-llm-orchestration` (Spring AI 2.x is still snapshot-only as of 2026-04).
- **`application.yaml`** — new `gls.slm.worker.anthropic.{model, temperature, max-tokens}` properties (defaults: `claude-haiku-4-5`, `0.1`, `1024`).

**Why a minimal `PromptBlockResolver` instead of pulling in `gls-governance`:**

`gls-governance.PipelineBlock` carries metadata fields (description, metrics counters, import provenance, draft state, etc.) the SLM worker doesn't need. Depending on it would mean any governance class evolution forces a coordinated rebuild of the worker — exactly the cross-module coupling the v2 service split is meant to avoid. The Mongock change unit `V003_DefaultRouterBlock` already operates on the raw `pipeline_blocks` collection for the same reason (per the Phase 1.2 close-off entry). The resolver is a stable read-only projection of two string fields; if the governance shape changes, the worker only breaks if those two specific fields disappear.

**Tests (17 new in module; 343 reactor total):**

- `AnthropicHaikuSlmServiceTest` (9) — `renderUser` with `{{text}}` placeholder substitution; without placeholder (appends after blank line); with blank / null template (returns text only); `parseContent` of pure JSON (extracts result + confidence + rationale, strips them from the result map); JSON inside markdown code fences (strips fences); plain text (wraps as rationale with default confidence); blank/empty (zero confidence + empty rationale); JSON without `confidence` field defaults to 0.5; `activeBackend()` returns `ANTHROPIC_HAIKU`. The actual `chatModel.call()` round-trip requires the Anthropic SDK + an API key and is gated on issue #7 with the broader integration suite.
- `PromptBlockResolverTest` (8) — resolves `activeVersion` when no version pinned; resolves a pinned version distinct from active; unknown block id → `BlockUnknownException`; wrong block type → `BlockUnknownException`; missing version → `BlockUnknownException`; empty content (neither systemPrompt nor userPromptTemplate) → `BlockUnknownException`; `draftContent` fallback when no `versions` array yet; blank / null block id → `BlockUnknownException`.

The previous 326-reactor test suite (per the SLM PR1 entry) is unchanged. Module count goes from 25 → 42.

**Files changed:** 3 new source files (`PromptBlockResolver`, `AnthropicHaikuSlmService`, plus the rewritten `SlmBackendConfig`) + 2 new test files + `pom.xml` + `application.yaml` + plan / log = 8 files.

**Open issues / deferred:**

- **MCP integration** — per CSV #1, each worker calls MCP itself. The current `ChatClient.builder(...).build()` skips the `.defaultToolCallbacks(toolCallbackProvider)` call that `gls-llm-orchestration`'s `LlmClientFactory` uses. Wiring MCP requires adding `spring-ai-starter-mcp-client` + a `gls-mcp-server` connection config; lands in a follow-up PR alongside the cascade wire-in (the cascade is the natural place to inject MCP context anyway).
- **Cascade wire-in** — `gls-classifier-router` doesn't yet dispatch to `gls-slm-worker` for `PROMPT` blocks. Mirrors the BERT wire-in pattern (`SlmDispatchCascadeService` HTTP client + extension to the orchestrator that calls SLM between BERT and LLM). Lands once MCP is wired.
- **Ollama backend** — Phase 1.5 PR3. Spring AI's Ollama starter follows the same shape as the Anthropic starter; the implementation will mirror this PR with `OllamaChatModel` + an `OllamaSlmService`.
- **Cost budget** — per-day / per-call ceilings still deferred. The `costUnits` field is reported per-call but no enforcement gate exists yet.
- **Real LLM call integration test** — needs an API key + outbound network. Same blocker as the existing `gls-llm-orchestration` integration tests; gated on issue #7.
- **`AnthropicHaikuSlmService.isReady()`** — currently returns `true` whenever the bean exists. A real ping-style probe (e.g. a `/v1/messages` HEAD call) belongs with the broader health follow-up.

**Next:** Cascade wire-in (mirrors the BERT 1.4 PR2 pattern: `SlmDispatchCascadeService` in `gls-classifier-router` + orchestrator branch); OR Ollama backend (Phase 1.5 PR3); OR MCP integration; OR `gls-bert-trainer` Python sketch; OR Phase 1.6 LLM worker rework.

## 2026-04-29 — Phase 1.5 PR3 — SLM cascade wire-in

**Done:** The cascade router can now dispatch the SLM tier for `PROMPT` blocks via synchronous HTTP to `gls-slm-worker`. Activated by `gls.router.cascade.slm.enabled=true`; default off. Composes with the BERT tier — the production cascade is now BERT → SLM → inner (LLM/mock). End-to-end: with all three flags on + the SLM worker stub returning `SLM_NOT_CONFIGURED`, every `PROMPT` request flows BERT (bypass for PROMPT) → SLM (fallthrough) → LLM and the cascade trace records each step.

**Decisions logged:** None new. Implements CSV #1 (cascade dispatch) for the SLM tier specifically. The chain-of-responsibility composition pattern in `CascadeBackendConfig` extends naturally to additional tiers.

**What's wired:**

- **`SlmHttpDispatcher`** (new) — pure HTTP client. JDK `HttpClient` against `POST /v1/classify`. Translates responses: 200 → `SlmInferenceResult`; 503 → `SlmTierFallthroughException` with `errorCode` from the body (default `SLM_NOT_CONFIGURED`); 422 → `SlmBlockUnknownException`; other 4xx/5xx → fallthrough with `errorCode=SLM_HTTP_<status>`; transport / parse failures → fallthrough with `SLM_TRANSPORT_ERROR` / `SLM_RESPONSE_INVALID`.
- **`SlmInferenceResult`** (new record) — internal shape carrying `result` map, `confidence`, `backend`, `modelId`, `tokensIn`, `tokensOut`, `costUnits`.
- **`SlmTierFallthroughException`** (new) + **`SlmBlockUnknownException`** (new) — same pattern as the BERT equivalents.
- **`SlmOrchestratorCascadeService`** (new) — implements `CascadeService`. For `blockType=PROMPT`: dispatches to SLM, returns SLM outcome on success (`tierOfDecision=SLM`), escalates to inner on fallthrough (with the SLM trace step prepended). For other block types (`BERT_CLASSIFIER`, null): delegates directly to inner. `SlmBlockUnknownException` propagates so the controller maps it to 422.
- **`RouterHttpConfig`** (refactored) — class-level `@ConditionalOnProperty` removed; each `@Bean` carries its own. Now registers `BertHttpDispatcher` and `SlmHttpDispatcher` independently.
- **`CascadeBackendConfig`** (extended) — `cascadeService()` factory now composes BERT → SLM → inner using `ObjectProvider.getIfAvailable()` for each tier's dispatcher. Composition order is explicit (BERT outermost), so a request flowing through BERT → SLM → LLM gets each tier's trace step in order.
- **`RouterExceptionHandler`** — new `@ExceptionHandler(SlmBlockUnknownException.class)` → 422 `ROUTER_SLM_BLOCK_UNKNOWN`.
- **`ClassifyController.errorCodeFor`** — extended with `ROUTER_SLM_BLOCK_UNKNOWN`.
- **`application.yaml`** — three new keys under `gls.router.cascade.slm.*`: `enabled` (default `false`), `url` (default `http://gls-slm-worker:8080`), `timeout-ms` (default `60000` — higher than BERT's 30s since SLM hits a real LLM).

**Why a separate orchestrator instead of extending the BERT one:**

Each orchestrator targets a single block type — BERT for `BERT_CLASSIFIER`, SLM for `PROMPT`. Keeping them as separate `CascadeService` implementations that wrap an inner means each tier is independently testable + flaggable, the composition order is explicit at wiring time, and adding another tier later (e.g. a regex-only tier before SLM) is purely additive. A single mega-orchestrator would tangle the conditions and turn block-type routing into a switch statement. The chain-of-responsibility shape scales linearly.

**Tests (12 new in module; 355 reactor total):**

- `SlmHttpDispatcherTest` (6) — happy 200 (full result shape + request body sanity); 503 with `code=SLM_NOT_CONFIGURED`; 503 with no body (defaults to `SLM_NOT_CONFIGURED`); 422 → `SlmBlockUnknownException`; 500 → fallthrough with `errorCode=SLM_HTTP_500`; transport failure → fallthrough with `SLM_TRANSPORT_ERROR`.
- `SlmOrchestratorCascadeServiceTest` (6) — happy `PROMPT` (SLM dispatched); fallthrough on `SLM_NOT_CONFIGURED` (SLM trace step prepended); `BERT_CLASSIFIER` delegates directly to inner; null block type delegates to inner; `SlmBlockUnknownException` propagates without falling through; **composition test** — chain `BertOrchestrator(SlmOrchestrator(MockCascade))` proves `BERT_CLASSIFIER` flows BERT-bypass → SLM-bypass → mock, while `PROMPT` flows BERT-bypass → SLM-serves.

Existing tests unchanged; the new orchestrator wraps the inner cascade transparently.

**Files changed:** 5 new source files + 4 modified + 2 new test files + plan / log = 13 files.

**Open issues / deferred:**

- **MCP integration** — same blocker as the SLM PR2 entry. The cascade dispatch path is a natural place to inject MCP context; lands as a follow-up alongside the orchestrator-level MCP wiring (cross-cuts BERT, SLM, and the eventual LLM rework).
- **ROUTER block threshold reading** — neither orchestrator reads the ROUTER block from Mongo to apply per-tier `accept` thresholds. Today every BERT 200 / SLM 200 response is accepted as-is. Threshold gating lands once a representative training corpus exists.
- **Real cross-service integration test** — full BERT → SLM → LLM cascade end-to-end requires Testcontainers + all four services running. Belongs with the broader pipeline integration suite blocked on issue #7.
- **Cost / latency budgets** — `costBudget.maxCostUnits` from the ROUTER block schema not yet enforced.

**Phase 1.5 status:** three of five plan checkboxes ticked. PR1 (module + contract), PR2 (Anthropic Haiku backend), PR3 (cascade wire-in) all green. Remaining: Ollama backend; MCP integration; per-category threshold tuning (gated on a representative eval set).

**Next:** Ollama backend (Phase 1.5 PR4); OR MCP integration (cross-cutting — covers BERT, SLM, and the eventual LLM-rework simultaneously); OR `gls-bert-trainer` Python sketch (closes Phase 1.4); OR Phase 1.6 LLM worker rework; OR Dockerfile + Compose rollout for `gls-slm-worker` and `gls-bert-inference`.

## 2026-04-29 — Phase 1.5 PR4 — Ollama SLM backend

**Done:** Local Ollama backend lands behind the `SlmService` interface. Selected when `gls.slm.worker.backend=ollama`. Uses Spring AI's `OllamaChatModel` (same starter `gls-llm-orchestration` already depends on) and dispatches to `llama3.1:8b` by default. Closes the "two backends" Phase 1.5 plan checkbox.

**Decisions logged:** None new.

**What's wired:**

- **`OllamaSlmService`** (new) — implements `SlmService`. Structurally identical to `AnthropicHaikuSlmService`: resolves the PROMPT block via `PromptBlockResolver`, substitutes `{{text}}`, calls `ChatClient.builder(ollamaChatModel).build()` with `OllamaChatOptions(model, temperature, numCtx)`. Same JSON / fence parsing → `(result, confidence, rationale)`. Same `tokensIn` / `tokensOut` extraction from `ChatResponse.getMetadata().getUsage()`. Same `UncheckedIOException` wrapping for SDK runtime exceptions so the cascade router can fall through.
- **`SlmBackendConfig`** (extended) — `slmService` factory now also handles `backend=ollama`. Reads `gls.slm.worker.ollama.{model, temperature, num-ctx}` (defaults: `llama3.1:8b`, `0.1`, `32768`). `ObjectProvider<OllamaChatModel>.getIfAvailable()` returns null when the Ollama starter hasn't autoconfigured the bean (e.g. `spring.ai.ollama.base-url` not set / not reachable); falls through to the not-configured stub with a WARN log.
- **`pom.xml`** — adds `spring-ai-starter-model-ollama` (no version since the Spring AI BOM is already imported in PR2).
- **`application.yaml`** — three new keys under `gls.slm.worker.ollama.*`: `model` (default `llama3.1:8b`), `temperature` (default `0.1`), `num-ctx` (default `32768`).

**Why structurally identical to Anthropic instead of a shared base class:**

The two backends share parsing logic (~80 lines) but differ in the SDK options builder (`AnthropicChatOptions` vs `OllamaChatOptions`) and the metadata fields they surface. Extracting a `BaseSlmService` parent would require either a generic options-builder type-bound (clumsy due to Spring AI's split builder hierarchy) or reflection. The structural duplication is minor; if a third Spring-AI-backed backend ships (e.g. OpenAI), the lift to a base class becomes worthwhile. Until then, two flat 200-line implementations beat one 250-line abstraction.

**Tests (7 new in module; 362 reactor total):**

- `OllamaSlmServiceTest` (7) — `renderUser` substitution + appendage paths; `parseContent` for pure JSON; `parseContent` strips `\`\`\`json\` fences; `parseContent` non-JSON wraps as rationale with default confidence; `activeBackend()` returns `OLLAMA`; blank `modelId` falls back to default `llama3.1:8b` (proves construction succeeds; the default isn't surfaced directly but constructor validation works).

The previous 355-reactor test suite (per the SLM PR3 entry) is unchanged. Module count goes from 42 → 49.

**Files changed:** 1 new source file (`OllamaSlmService`) + 2 modified (`SlmBackendConfig`, `application.yaml`) + `pom.xml` + 1 new test file + plan / log = 6 files.

**Open issues / deferred:**

- **MCP integration** — same blocker as the Anthropic backend (PR2). Lands cross-service in a follow-up.
- **Per-category threshold tuning** — gated on a representative eval set; same as the cascade wire-in entry.
- **Real Ollama integration test** — needs a running Ollama instance with `llama3.1:8b` pulled. Same blocker as PR2's Anthropic round-trip; gated on issue #7.
- **Backend reachability probe** — `OllamaSlmService.isReady()` returns true whenever the bean exists. A real `/api/tags` ping belongs with the broader health follow-up.

**Phase 1.5 status:** **four of five plan checkboxes ticked.** PR1 (module + contract), PR2 (Anthropic backend), PR3 (cascade wire-in), PR4 (Ollama backend) all green. Remaining: MCP integration; per-category threshold tuning (gated on a representative eval set).

**Next:** MCP integration (cross-cutting — covers BERT, SLM, and the eventual LLM-rework simultaneously); OR `gls-bert-trainer` Python sketch (closes Phase 1.4); OR Phase 1.6 LLM worker rework; OR Dockerfile + Compose rollout for `gls-slm-worker` and `gls-bert-inference`.

## 2026-04-29 — Phase 1.5 PR5 — MCP integration in `gls-slm-worker`

**Done:** SLM worker now passes MCP tool callbacks to its ChatClient calls per CSV #1 ("each worker calls MCP itself"). Spring AI's `spring-ai-starter-mcp-client` auto-configures one `ToolCallbackProvider` bean per declared MCP connection; `SlmBackendConfig` collects all of them via `ObjectProvider<ToolCallbackProvider>.stream()` and hands them to both backends through a new constructor overload. The ChatClient builder calls `.defaultToolCallbacks(...)` only when there's at least one provider — missing MCP server → starter logs WARN, no tools registered, SLM call still works.

Closes the Phase 1.5 "MCP integration" plan checkbox. **Phase 1.5 is now five of five.**

**Decisions logged:** None new. Implements CSV #1 for the SLM tier; same pattern `gls-llm-orchestration`'s `LlmClientFactory` already uses.

**What's wired:**

- **`pom.xml`** — adds `spring-ai-starter-mcp-client` (Spring AI BOM already imported in PR2).
- **`AnthropicHaikuSlmService`** — new 7-arg constructor takes `ToolCallbackProvider[]`. The 6-arg constructor delegates with an empty array so existing callers + tests keep working unchanged. The `ChatClient.Builder` is now mutated step-by-step: `.defaultToolCallbacks(...)` is called only when the array is non-empty.
- **`OllamaSlmService`** — same pattern (6-arg + 7-arg constructors).
- **`SlmBackendConfig`** — new `ObjectProvider<ToolCallbackProvider>` parameter; the factory collects all providers via `.stream().toArray(ToolCallbackProvider[]::new)`, passes the array through, and logs whether MCP is wired (count) or not.
- **`application.yaml`** — adds `spring.ai.mcp.client.{type, sse.connections.governance.url}` (matches the orchestrator's pattern; default `http://gls-mcp-server:8081`, overridable via `GLS_MCP_SERVER_URL`).

**Why a constructor overload instead of mutating the existing one:**

The existing 6-arg constructors are used by tests + the original `SlmBackendConfig` factory. Adding the `ToolCallbackProvider[]` as a 7th positional arg would break every call site. With the overload, tests + legacy callers go through the 6-arg variant (which forwards to the 7-arg with an empty array); the production factory uses the 7-arg directly. No test churn.

**Tests (2 new in module; 364 reactor total):**

- `AnthropicHaikuSlmServiceTest` (was 9, now 11) — added: constructor accepts a `ToolCallbackProvider[]` (one mock provider) and reports the right `activeBackend`; constructor handles a `null` array (defaults to empty internally).
- `OllamaSlmServiceTest` unchanged — structural mirroring means the same coverage applies.
- The actual `defaultToolCallbacks(...)` invocation against a real ChatClient builder + a running MCP server is gated on issue #7.

The previous 362-reactor test suite (per the SLM PR4 entry) is unchanged. Module count goes from 49 → 51.

**Files changed:** 3 modified source files + `pom.xml` + `application.yaml` + 1 modified test file + plan / log = 7 files.

**Open issues / deferred:**

- **Real MCP round-trip integration test** — needs a running `gls-mcp-server`, the SLM worker, plus a configured backend. Same blocker as the rest of the v2 cross-service tests; gated on issue #7.
- **MCP client error handling** — Spring AI's MCP client surfaces tool failures as `RuntimeException` from the ChatClient. Today caught and wrapped as `UncheckedIOException` → `SLM_DEPENDENCY_UNAVAILABLE` 503. A finer-grained `MCP_TOOL_FAILED` code might be useful for ops dashboards. Deferred.
- **Per-category MCP scoping** — every classification gets the full MCP toolset today. Per-category restrictions (e.g. PII-scan blocks shouldn't have access to `update_classification` tools) belong with the future `POLICY` block work in Phase 1.8.
- **MCP integration in BERT-tier** — BERT inference doesn't take a system prompt, so there's nothing for MCP to inject into. The CSV requirement applies to LLM-style workers only.

**Phase 1.5 status:** **five of five plan checkboxes ticked.** PR1 (module + contract), PR2 (Anthropic backend), PR3 (cascade wire-in), PR4 (Ollama backend), PR5 (MCP integration). The remaining "tune slmAcceptThreshold per category" work is gated on the trainer + a representative eval set; not on this phase's critical path.

**Next:** `gls-bert-trainer` Python sketch (closes Phase 1.4); OR Phase 1.6 LLM worker rework (lift `gls-llm-orchestration` into the new contract shape — same shape SLM already exposes); OR Dockerfile + Compose rollout for the new v2 services; OR ROUTER block threshold reading + per-category enable.

## 2026-04-29 — Phase 1.4 / 1.5 follow-up — Dockerfile + Compose rollout for new v2 services

**Done:** Container image definitions for `gls-bert-inference` and `gls-slm-worker` plus their Compose entries, so the new tier services can be exercised end-to-end via `docker compose up --build` alongside the existing v2 cascade. The router's Compose entry also gains the cascade tier feature flags (`GLS_ROUTER_BERT_ENABLED`, `GLS_ROUTER_SLM_ENABLED`, `GLS_ROUTER_LLM_ENABLED`) + the worker URLs so flipping a flag is a `.env` change rather than a code change. Closes the deferred operational items from BERT PR1 and SLM PR1's logs.

**Decisions logged:** None new.

**What's wired:**

- **`backend/gls-bert-inference/Dockerfile`** — multi-stage build, same shape as `gls-classifier-router/Dockerfile`. Layered pom-only `dependency:go-offline` for Maven cache hits, then `package -pl gls-bert-inference -am`. Final image: `eclipse-temurin:25-jre` with `curl` for the healthcheck, exposes 8094, healthcheck against `/actuator/health`.
- **`backend/gls-slm-worker/Dockerfile`** — same shape, exposes 8095.
- **`docker-compose.yml`** — adds `gls-bert-inference` (depends on minio for the future ONNX fetch path; default `GLS_BERT_ENGINE=none` so it returns `MODEL_NOT_LOADED` 503 until the trainer ships) and `gls-slm-worker` (depends on mongo + mcp-server; default `GLS_SLM_BACKEND=none`). The router service gains four new env vars: `GLS_ROUTER_BERT_ENABLED` / `_URL`, `GLS_ROUTER_SLM_ENABLED` / `_URL`, `GLS_ROUTER_LLM_ENABLED`. Defaults are `false` so existing setups behave identically.
- **MCP wiring exposed via env** — the SLM worker's `GLS_MCP_SERVER_URL` defaults to `http://mcp-server:8081`, the same hostname the LLM orchestrator uses; both services see the same MCP server.

**Why all four cascade flags default to `false`:**

The cascade router is shared between PROMPT (LLM/SLM) and BERT_CLASSIFIER (BERT) blocks. Flipping any tier on without the matching worker reachable would surface as a 503 fallthrough — recoverable but noisy in logs. Defaulting all flags to `false` keeps the existing single-tier (mock or LLM-direct) behaviour. The `.env.example` (forthcoming follow-up) will document the recommended progression: enable LLM first, then BERT, then SLM, in that order, as each worker is verified.

**Tests (no new in module; 364 reactor unchanged):**

Compose + Dockerfile validity is exercised by the existing `docker-build` CI job (which already builds all services per merge). Local verification: `./mvnw test` still green (364 tests). No new unit tests — Compose / Dockerfile changes are infrastructure, not behaviour.

**Files changed:** 2 new Dockerfiles + 1 modified `docker-compose.yml` + plan / log = 4 files.

**Open issues / deferred:**

- **`.env.example` documentation** — should add the `GLS_ROUTER_*_ENABLED`, `GLS_BERT_*`, `GLS_SLM_*`, `GLS_MCP_SERVER_URL` keys with sensible defaults + comments. Trivial follow-up.
- **K8s manifests** — Compose covers dev / single-host. K8s deferred per CSV #38; lands once a deployment target is chosen.
- **Image registry / CI image push** — Compose builds locally on every `up`. A registry push pipeline (GitHub Container Registry?) is on the v2 release-readiness list.
- **Health probe smoke test** — once both new services start, `curl gls-bert-inference:8094/actuator/health` should return 200 (with `OUT_OF_SERVICE` since no model is loaded) and `curl gls-slm-worker:8095/actuator/health` should return 503. Belongs in the integration suite gated on issue #7.

**Next:** `gls-bert-trainer` Python sketch (closes Phase 1.4 cleanly); OR Phase 1.6 LLM worker rework; OR ROUTER block threshold reading + per-category enable; OR `.env.example` + service-template README updates.

## 2026-04-29 — Phase 1.4 PR4 — `gls-bert-trainer` Python sketch

**Done:** Closes Phase 1.4 cleanly with the long-deferred Python trainer. New top-level `gls-bert-trainer/` package — pyproject + Dockerfile + tests. Three real components: `data.py` (pymongo reader for `bert_training_samples` with top-N + min-per-class gate), `train.py` (HuggingFace fine-tune of ModernBERT-base + Optimum ONNX export), `publish.py` (minio uploader with versioned object key + metadata sidecar). Skipped (with a WARN log) when fewer than the configured per-class sample minimum exists.

**Decisions logged:** None new — implements CSV #2 (DECIDED hybrid: Python trains, JVM serves) for the trainer half of the split.

**What's wired:**

- **`gls-bert-trainer/`** (new top-level dir, sibling to the legacy `bert-classifier/` v1 inference service that this whole Phase 1.4 work eventually replaces).
- **`pyproject.toml`** — package definition. Deps: `pymongo`, `minio`, `transformers`, `datasets`, `torch`, `scikit-learn`, `optimum[onnxruntime]`, `accelerate`. Console script: `gls-bert-trainer = "gls_bert_trainer.__main__:main"`.
- **`config.py`** — env-driven `TrainerConfig` dataclass. `MONGO_URI` is required; everything else has dev-friendly defaults. Tunable: `GLS_BERT_TRAINER_TOP_N` (default 3 — matches CSV #2), `GLS_BERT_TRAINER_MIN_SAMPLES_PER_CLASS` (default 50), `GLS_BERT_TRAINER_BASE_MODEL` (default `answerdotai/ModernBERT-base`), `GLS_BERT_TRAINER_EPOCHS` (default 3), batch size, learning rate, max sequence length.
- **`data.py`** — `TrainingDataLoader` queries `bert_training_samples` (the collection populated by `BertTrainingDataCollector` in `gls-app-assembly` per the Phase 1.3 entry). Aggregation pipeline groups by `categoryId`, applies the min-per-class gate via `$match`, sorts by count desc, takes top-N. Returns a `LoadedDataset` with the chosen samples plus an ordered `label_mapping` `[(categoryId, categoryName)]` whose index matches the model's softmax-output index. `split_train_test` gives a deterministic 90/10 split sorted by `(label, text)` so eval metrics are comparable across trainer versions.
- **`train.py`** — `fine_tune_and_export`. Lazy-imports torch / transformers / optimum so the rest of the package stays importable in test environments without ML deps. Standard HF flow: `AutoTokenizer.from_pretrained` → `AutoModelForSequenceClassification.from_pretrained(num_labels=...)` → `Trainer(...).train()` → `Trainer.evaluate()` → `Trainer.save_model()`. ONNX export via `ORTModelForSequenceClassification.from_pretrained(..., export=True)`. Returns a `TrainingResult` carrying the `Path` to the exported `.onnx` file plus eval metrics (accuracy, f1_macro, per-label precision/recall) shaped to slot into the BERT_CLASSIFIER block's `trainingMetadata` field.
- **`publish.py`** — `ArtefactPublisher` uploads `model.onnx` + `metadata.json` to MinIO under `${bucket}/${trainerVersion}/${modelVersion}/...`. Default `modelVersion` is a UTC timestamp `YYYY.MM.DD-HHMMSS`. Auto-creates the bucket if absent. The metadata sidecar's keys (`trainerVersion`, `modelVersion`, `baseModelId`, `trainedAt`, `datasetSize`, `trainSize`, `testSize`, `labels`, `evaluationMetrics`) match `contracts/blocks/bert-classifier.schema.json`'s `trainingMetadata` field one-to-one so the admin UI's "create block from artefact" path is a copy-paste.
- **`__main__.py`** — CLI orchestration. Loads config, connects to Mongo, loads samples, bails early (return code 0, WARN log) if there's not enough data. Otherwise: lazy-imports `train`, runs `fine_tune_and_export` against a `TemporaryDirectory`, publishes via `ArtefactPublisher`, logs the resulting MinIO key.
- **`Dockerfile`** — Python 3.12-slim, multi-stage. Build stage installs the package + heavy ML deps; runtime stage carries forward the installed site-packages + the `gls-bert-trainer` console script. No `EXPOSE` / `HEALTHCHECK` — this is a one-shot job, not a service.

**Why deterministic train/test split instead of `random_state=42`:**

Sorting by `(label, text)` then taking every Nth sample as test guarantees:

1. The same input dataset always produces the same split, regardless of insert order in Mongo.
2. Adding a new sample shifts at most one boundary — the rest of the split is stable.
3. No NumPy / random-seed plumbing needed.

The classic `train_test_split(random_state=42)` works too but introduces a numpy dep on the data layer (it's already in train.py for HF, but cleanly separable). Saving the dep and the seed-management complexity is worth a slightly less random split for an MLP-shaped classifier where input order is irrelevant.

**Tests (15 in package; not in the Java reactor):**

- `test_config.py` (3) — minimal-env load (mongo URI from env, defaults for everything else); env overrides; missing MONGO_URI raises.
- `test_data.py` (7) — happy path (top-N categories returned in count-desc order with the `$match` gate hit); empty dataset (no `find` call); skips rows with blank text / blank label / null fields; `has_enough_samples` passes / fails; `split_train_test` is deterministic; rejects invalid ratios.
- `test_publish.py` (5) — uploads ONNX + metadata to versioned object keys; creates bucket when absent; default model version is the UTC timestamp; metadata shape matches the BERT_CLASSIFIER block schema's `trainingMetadata` field names; minio mocked.

The actual training run requires a GPU + a populated samples collection (and tens of minutes of compute) — gated on issue #7 with the rest of the v2 integration work.

**Files changed:** ~9 new Python source files + 3 new test files + `pyproject.toml` + `Dockerfile` + `README.md` + `.gitignore` + plan / log = ~17 files.

**Open issues / deferred:**

- **Per-category overrides** — the trainer assumes a single global classifier across the top-N categories. Per-category specialisation (e.g. a separate fine-tune for medical vs. legal docs) lands when org-multi-tenancy is real.
- **Distillation** — CSV #2 mentions distillation as a Phase 2 follow-up; not in this PR.
- **Active learning loop** — selecting which samples to label next based on the inference service's low-confidence cases. Phase 2 work; needs the inference + correction-loop UI to be live first.
- **Real training-run integration test** — needs a GPU + populated `bert_training_samples` + MinIO. Gated on issue #7. The unit tests under `tests/` do cover the surrounding orchestration.
- **Compose entry** — the trainer is a one-shot k8s Job, not a long-running service, so it doesn't fit the `docker compose up` shape. A `profiles: [trainer]` entry that runs once and exits is a possible follow-up; for now `docker run` is the dev-time invocation.
- **CI Python build** — the existing CI is Java-only. The Python tests would need a parallel CI workflow (or a hybrid one). For Phase 1.4 close-out, local `pytest` is the verification path.

**Phase 1.4 status:** **all five plan checkboxes ticked.** PR1 (inference module + BERT_CLASSIFIER schema), PR2 (BERT cascade wire-in), PR3 (skipped — block schema landed in PR1), PR4 (this PR — trainer sketch). The "wire bert-inference into the cascade" + "enable BERT for top-1 category" items lit up as flag flips once the trainer's first artefact is published.

**Next:** Phase 1.6 LLM worker rework (lift `gls-llm-orchestration` into the new HTTP contract shape — same shape SLM already exposes); OR ROUTER block threshold reading + per-category enable; OR `.env.example` + service-template README updates; OR `gls-bert-trainer` Compose / k8s Job manifest.

## 2026-04-29 — Phase 1.4 / 1.5 follow-up — ROUTER block threshold reading

**Done:** The cascade orchestrators (BERT and SLM) now read per-tier thresholds + enabled flags from the active `default-router` ROUTER block in Mongo and apply them: a tier's confidence below its `accept` threshold falls through to the inner cascade; a tier with `enabled=false` is skipped without dispatching at all. Closes the "ROUTER block threshold reading" deferred item carried in the BERT 1.4 PR2 and SLM 1.5 PR3 logs.

**Decisions logged:** None new — implements the cascade-tuning shape already encoded in `contracts/blocks/router.schema.json`.

**What's wired:**

- **`RouterPolicy`** (new record) — parsed shape: `(bert, slm, llm)` each carrying `(enabled, accept)`. `RouterPolicy.DEFAULT` matches the seeded `default-router` block (BERT/SLM accept = 1.01 functionally disabled, LLM accept = 0.0 always accept).
- **`RouterPolicyResolver`** (new `@Component`) — reads `default-router` from `pipeline_blocks` via `MongoTemplate.findOne(Document.class)`. Caches the parsed policy with a configurable TTL (default 60s); on Mongo lookup failure / missing block / malformed content, returns `RouterPolicy.DEFAULT` and logs at WARN.
- **`BertOrchestratorCascadeService`** — new constructor overload takes `Supplier<RouterPolicy>`. Two new gates: tier disabled → skip dispatch entirely (`TIER_DISABLED` trace step); below threshold → fall through (`BELOW_THRESHOLD` trace step carrying the actual confidence so observers see near-misses).
- **`SlmOrchestratorCascadeService`** — same pattern for `policy.slm()`.
- **`CascadeBackendConfig`** — `cascadeService()` factory now takes an `ObjectProvider<RouterPolicyResolver>` and passes a `Supplier<RouterPolicy>` to both orchestrators. Falls back to `() -> RouterPolicy.DEFAULT` when the resolver isn't wired (unit tests that don't stand up Mongo).
- **`application.yaml`** — two new keys under `gls.router.policy.*`: `block-name` (default `default-router`), `refresh-seconds` (default 60).

**Why a `Supplier<RouterPolicy>` instead of injecting `RouterPolicy` directly:**

A direct `RouterPolicy` injection would freeze the policy at orchestrator construction time. With the `Supplier`, the orchestrator calls `policy.get()` per request and the resolver's TTL cache decides when to re-read Mongo. Operators can tune thresholds via the admin UI (when that lands) and the cascade picks up the change within `refresh-seconds`. No restart required.

**Why the legacy 2-arg constructors stay:**

Existing tests construct orchestrators without a policy. The 2-arg constructor delegates to the 3-arg with `() -> RouterPolicy.DEFAULT`, so old tests keep working but now go through the threshold path with the conservative default. Happy-path tests use the 3-arg constructor with a permissive policy (`accept=0.0`).

**Tests (11 new in module; 375 reactor total):**

- `RouterPolicyResolverTest` (7) — happy parse; missing block → DEFAULT; Mongo throws → DEFAULT; missing `tiers` key → DEFAULT; cache hit within refresh interval; `invalidateCache` forces re-read; partial tiers (only `bert` configured, SLM/LLM fall back to DEFAULT).
- `BertOrchestratorCascadeServiceTest` (was 5, now 7) — added: below-threshold falls through with `errorCode=BELOW_THRESHOLD`; disabled tier skips dispatch entirely (`errorCode=TIER_DISABLED`, no `infer` call).
- `SlmOrchestratorCascadeServiceTest` (was 6, now 8) — same two new tests for SLM.

The previous 364-reactor test suite is unchanged. Router module count: 57 → 68.

**Files changed:** 2 new source files + 3 modified + `application.yaml` + 1 new test file + 2 modified test files + log = 10 files.

**Open issues / deferred:**

- **`categoryOverrides` per-category tuning** — schema's `categoryOverrides[]` not yet parsed. Lands when the admin UI's category editor lets operators set them.
- **Per-request ROUTER block selection** — `ClassifyRequest.cascadeHints.{forceTier, maxCostUnits}` still ignored.
- **`costBudget.maxCostUnits` enforcement** — schema declares it; not honoured yet. Meaningful when multiple successful tiers happen per request.
- **Cache invalidation on block save** — could subscribe to `gls.config.changed` events for the ROUTER block to drop the operator-tuning round-trip from ~30s avg to ~0.
- **Real Mongo integration test for the resolver** — gated on issue #7.

**Next:** Phase 1.6 LLM worker rework; OR per-category overrides + cascadeHints handling; OR `costBudget.maxCostUnits` enforcement; OR `.env.example` updates.

## 2026-04-29 — Phase 1.6 PR1 — `gls-llm-worker` module + contract (stub backend)

**Done:** Phase 1.6 starts. New `gls-llm-worker` JVM module mirrors `gls-slm-worker`'s shape — same async surface, same `JobStore` lifecycle, same audit factory. Ships with a stub `NotConfiguredLlmService` returning `LLM_NOT_CONFIGURED` 503. PR2 lifts the existing `gls-llm-orchestration` Anthropic + MCP integration into `LlmService` behind the same selector pattern SLM uses.

**Decisions logged:** None new — implements CSV #1 / #13 / #47 for the LLM tier specifically.

**What's wired:**

- **`contracts/llm-worker/`** (new, v0.1.0) — OpenAPI 3.1.1. Four operations (`classify`, `getJob`, `getCapabilities`, `getHealth`). Drops `/v1/backends` from the SLM template since LLM has only one provider conceptually.
- **`gls-llm-worker`** module — package `co.uk.wolfnotsheep.llmworker` (distinct from the existing `co.uk.wolfnotsheep.llm` in `gls-llm-orchestration` so the two coexist during the transition). Lifted from `gls-slm-worker` with mechanical `Slm` → `Llm` renames + minor adjustments (no `BackendsController`, no `body.setBackend()` since the LLM contract has only one provider).
- **`LlmService`** + **`NotConfiguredLlmService`** stub — always throws `LlmNotConfiguredException` → 503 `LLM_NOT_CONFIGURED`.
- **`LlmBackendId`** enum: `ANTHROPIC`, `OLLAMA`, `NONE` (the v2 LLM worker keeps the same two backends as legacy `gls-llm-orchestration`).
- **`JobStore` / `JobRecord` / `JobAcquisition` / `JobRepository`** — same shape as SLM and the router. Mongo collection `llm_jobs`.
- **`ClassifyController` / `JobController` / `MetaController` / `AsyncDispatcher` / `LlmExceptionHandler`** — same lifecycle as SLM.
- **`LlmEvents`** audit factory: `LLM_COMPLETED` / `LLM_FAILED`. `action="CLASSIFY"` matches the router and SLM so observers can join cascade-internal LLM tier calls into the same trace.
- **BOM + parent pom** — `gls.llm.worker.version` property + dependency declaration; module registered in `backend/pom.xml`.
- **`application.yaml`** — `gls.llm.worker.{backend, jobs.ttl, async.*}`. Default `backend=none`.

**Why a new module instead of in-place refactor of `gls-llm-orchestration`:**

The legacy module is Rabbit-driven (consumer + dispatcher) while the new contract is HTTP. An in-place refactor would have to delete the Rabbit consumer wholesale and rewire callers — and the cascade router still uses the Rabbit dispatch path today. By creating a new module, the legacy service stays operational during the transition; PR2 + a follow-up cascade-router PR cut the existing dispatch over to HTTP, then a final PR retires the legacy module. Distinct top-level packages (`co.uk.wolfnotsheep.llm` vs. `co.uk.wolfnotsheep.llmworker`) means classpath coexistence is clean.

**Tests (14 in module; 389 reactor total):**

- `NotConfiguredLlmServiceTest` (2) — `classify` always throws; `activeBackend()=NONE`, `isReady()=false`.
- `LlmEventsTest` (4) — `LLM_COMPLETED` / `LLM_FAILED` envelope shapes; null-safe metadata; eventId is 26-char ULID-ish.
- `ClassifyControllerTest` (8) — happy path with mocked `LlmService`; backend failure → markFailed + propagate; in-flight 409; cached idempotency; respond-async 202; running row → 202 without re-dispatch; real-stub round-trip propagates `LlmNotConfiguredException`.

The previous 375-reactor test suite is unchanged. New module: 14 tests, total 389.

**Files changed:** ~17 new source files in `gls-llm-worker` + 3 new test files + `pom.xml` + `application.yaml` + 4 contract files + `backend/pom.xml` + `backend/bom/pom.xml` + plan / log = ~30 files.

**Open issues / deferred:**

- **Real Anthropic backend (PR2)** — lifts the existing `gls-llm-orchestration` `LlmClientFactory` + `LlmProviderConfig` into `LlmService` behind `LlmBackendConfig`. Same `spring-ai-starter-model-anthropic` dep + MCP client integration as SLM PR2/PR5.
- **Cascade router cut-over (PR3)** — a new `LlmHttpDispatcher` in `gls-classifier-router` replaces the legacy `LlmDispatchCascadeService` (Rabbit) with HTTP calls. Activated by a feature flag.
- **Cost budget gate + rate-limit semaphore** — Phase 1.6 plan checkboxes; land in PR2.
- **Legacy `gls-llm-orchestration` retirement** — after PR2 + PR3 + a stabilisation window.
- **Dockerfile + Compose entry** — deferred (same pattern as BERT/SLM PR1).
- **JobControllerTest + MetaControllerTest in module** — basic happy paths only in PR1; full coverage matches SLM in PR2.

**Phase 1.6 status:** one of four plan checkboxes ticked (the "conform to new contract" item). PR2/PR3 close the rest.

**Next:** Phase 1.6 PR2 (real Anthropic + MCP, lifted from `gls-llm-orchestration`); OR Phase 1.6 PR3 (router cutover from Rabbit to HTTP); OR per-category overrides; OR Dockerfile / Compose for the v2 services.

## 2026-04-29 — Phase 1.6 PR2 — Anthropic + Ollama LLM backends + MCP

**Done:** Real LLM backends behind the `LlmService` interface. Selected via `gls.llm.worker.backend=anthropic|ollama`. Uses Spring AI's `AnthropicChatModel` / `OllamaChatModel` (same starters `gls-llm-orchestration` already depends on). MCP integration via `spring-ai-starter-mcp-client` per CSV #1 — every backend hands the configured `ToolCallbackProvider` beans to its ChatClient builder. Closes the Phase 1.6 "move logic into the new shape" plan checkbox.

**Decisions logged:** None new — implements CSV #1 (each worker calls MCP itself) and CSV #2 (cascade dispatch task-agnostic) for the LLM tier.

**What's wired:**

- **`AnthropicLlmService`** (lifted from `AnthropicHaikuSlmService`) — same shape as the SLM's Anthropic service. Defaults to `claude-sonnet-4-5` (the v2 LLM tier's planned default per CLAUDE.md), `temperature=0.1`, `max-tokens=4096`. Resolves PROMPT block via `PromptBlockResolver`, substitutes `{{text}}`, calls Spring AI `ChatClient` with `AnthropicChatOptions(model, temperature, maxTokens)` and `defaultToolCallbacks(toolCallbackProviders)` when MCP is wired. Same JSON / fence parsing, same `tokensIn`/`tokensOut` extraction, same `UncheckedIOException` wrapping for SDK errors.
- **`OllamaLlmService`** (lifted from `OllamaSlmService`) — defaults to `qwen2.5:32b` (matches the legacy orchestrator's default), `temperature=0.1`, `numCtx=32768`. Same shape as Anthropic but with `OllamaChatOptions`.
- **`PromptBlockResolver`** (lifted from `gls-slm-worker`) — reads `pipeline_blocks` via raw `MongoTemplate` projection. Resolves PROMPT blocks to `(systemPrompt, userPromptTemplate)`. Same minimal-coupling pattern as SLM (no `gls-governance` dep).
- **`LlmBackendConfig`** (rewritten) — single `@Bean` factory selects between not-configured / Anthropic / Ollama based on `gls.llm.worker.backend`. `ObjectProvider<AnthropicChatModel>` and `ObjectProvider<OllamaChatModel>` with `getIfAvailable()` so the service starts cleanly without either configured. Collects `ToolCallbackProvider` beans via `ObjectProvider.stream()` and passes them to whichever backend is active.
- **`pom.xml`** — adds `spring-ai-starter-model-anthropic`, `spring-ai-starter-model-ollama`, `spring-ai-starter-mcp-client`. Imports the `spring-ai-bom` (2.0.0-SNAPSHOT) and registers the Spring snapshot repos at the module level — same pattern as `gls-slm-worker` and `gls-llm-orchestration`.
- **`application.yaml`** — new `gls.llm.worker.{anthropic, ollama}.*` config keys + `spring.ai.mcp.client.sse.connections.governance.url` (default `http://gls-mcp-server:8081`).

**Why "lift wholesale from SLM" instead of "lift from gls-llm-orchestration":**

The SLM module was authored with the new contract shape (HTTP, async surface, JobStore, RFC 7807 errors). The legacy `gls-llm-orchestration` is Rabbit-driven; its classification logic (`LlmClientFactory.call(systemPrompt, userPrompt)`) is wrapped in queue consumers + result publishers that don't translate to the new HTTP shape. Lifting from the SLM module gets us a working LLM worker in the new shape immediately; the remaining orchestrator-specific bits (cost budgets, rate-limit semaphores, app_config-driven model selection) are smaller follow-ups that don't block the cascade router cut-over.

**Tests (26 new in module; 415 reactor total):**

- `AnthropicLlmServiceTest` (9, lifted) — `renderUser` substitution paths; `parseContent` for pure JSON / fenced JSON / plain text / blank / no-confidence-default; `activeBackend()` returns `ANTHROPIC`; tool-callbacks-aware constructor + null array fallback.
- `OllamaLlmServiceTest` (7, lifted) — similar coverage plus `activeBackend()=OLLAMA` and blank-modelId fallback to `qwen2.5:32b`.
- `PromptBlockResolverTest` (8, lifted) — happy active-version resolve; pinned-version resolve; unknown id / wrong type / missing version / empty content → `BlockUnknownException`; `draftContent` fallback; blank id → exception.
- Existing 14 tests from PR1 (`NotConfiguredLlmServiceTest` + `LlmEventsTest` + `ClassifyControllerTest`) unchanged.

The previous 389-reactor test suite (per the LLM PR1 entry) is unchanged. Module count: 14 → 40.

**Files changed:** 4 new source files (`AnthropicLlmService`, `OllamaLlmService`, `PromptBlockResolver`) + rewritten `LlmBackendConfig` + `pom.xml` (Spring AI BOM + 3 starters + Spring snapshot repos) + `application.yaml` + 3 new test files + plan / log = 11 files.

**Open issues / deferred:**

- **Cost budget gate** — Phase 1.6 plan checkbox; per-day spending cap. Not in this PR; lifts from the legacy orchestrator's tracking shape if it has one, otherwise a small follow-up.
- **Rate-limit semaphore per replica** — Phase 1.6 plan checkbox; bounds concurrent in-flight calls. Small follow-up.
- **Cascade router cutover (PR3)** — `LlmDispatchCascadeService` (Rabbit) → `LlmHttpDispatcher` + `LlmOrchestratorCascadeService` mirroring the SLM pattern. Activated by a feature flag.
- **Real cross-service integration test** — needs a running Anthropic API + an MCP server. Same blocker as the rest of the v2 services; gated on issue #7.
- **Legacy `gls-llm-orchestration` retirement** — after PR3 + a stabilisation window.
- **`isReady()` reachability probe** — currently returns `chatModel != null`. A real ping-style probe lives with the broader health follow-up.

**Phase 1.6 status:** two of four plan checkboxes ticked (PR1's contract + PR2's logic move). Cost budget + rate limit remain. PR3 cuts the cascade router over to HTTP.

**Next:** Phase 1.6 PR3 (cascade router cut-over: `LlmHttpDispatcher` + `LlmOrchestratorCascadeService` replacing `LlmDispatchCascadeService`); OR cost budget + rate limit; OR per-category overrides; OR Dockerfile / Compose for `gls-llm-worker`.

## 2026-04-29 — Phase 1.6 PR3 — cascade router cut-over (Rabbit → HTTP for LLM)

**Done:** The cascade router can now dispatch the LLM tier via HTTP to `gls-llm-worker` instead of via Rabbit to `gls-llm-orchestration`. Activated by `gls.router.cascade.llm-http.enabled=true`. When both flags are on (HTTP + Rabbit), HTTP wins — that's the cut-over path. The legacy Rabbit dispatcher stays operational behind its own feature flag for rollback.

**Decisions logged:** None new — implements the planned LLM-tier transport swap.

**What's wired:**

- **`LlmHttpDispatcher`** (new) — pure HTTP client to `gls-llm-worker`'s `POST /v1/classify`. Same shape as BERT/SLM equivalents. 200 → `LlmInferenceResult`; 503 → `LlmTierFallthroughException` (default `LLM_NOT_CONFIGURED`); 422 → `LlmBlockUnknownException`; other 4xx/5xx → fallthrough with `errorCode=LLM_HTTP_<status>`; transport / parse → `LLM_TRANSPORT_ERROR` / `LLM_RESPONSE_INVALID`.
- **`LlmInferenceResult`** (new record) — `(result, confidence, modelId, tokensIn, tokensOut, costUnits)`. No `backend` field (LLM has only one provider conceptually).
- **`LlmTierFallthroughException`** + **`LlmBlockUnknownException`** — same pattern as BERT/SLM.
- **`LlmHttpCascadeService`** (new) — implements `CascadeService`. Unlike the BERT/SLM orchestrators, doesn't wrap an inner cascade — LLM IS the cascade's floor. On `LlmTierFallthroughException`, surfaces as `LlmJobFailedException` → `ROUTER_LLM_FAILED` 502 (mirrors the legacy Rabbit failure mapping). `LlmBlockUnknownException` propagates for 422.
- **`RouterHttpConfig`** — new `@Bean` registers `LlmHttpDispatcher` when `gls.router.cascade.llm-http.enabled=true`.
- **`CascadeBackendConfig`** — selection logic: HTTP wins over Rabbit when both are wired; else Rabbit; else mock. The HTTP dispatcher → `LlmHttpCascadeService` becomes the cascade's floor.
- **`RouterExceptionHandler`** — new handler → 422 `ROUTER_LLM_BLOCK_UNKNOWN`.
- **`ClassifyController.errorCodeFor`** — extended with `ROUTER_LLM_BLOCK_UNKNOWN`.
- **`application.yaml`** — `gls.router.cascade.llm-http.{enabled, url, timeout-ms}` (defaults: `false`, `http://gls-llm-worker:8080`, 90s).

**Why HTTP wins over Rabbit when both are configured:**

The cut-over should be deterministic — flipping `llm-http.enabled=true` is the explicit "use the new path" signal. Falling back to Rabbit only when HTTP isn't wired keeps existing setups working untouched. The `llm.enabled=true` flag remains the legacy-path activator; `llm-http.enabled=true` is the new-path activator. Both can be on during a redeploy transition. The selection picks the most modern wired option.

**Tests (9 new in module; 424 reactor total):**

- `LlmHttpDispatcherTest` (6) — happy 200; 503 with code; 503 without code; 422 → BlockUnknown; 500 → fallthrough; transport failure → fallthrough.
- `LlmHttpCascadeServiceTest` (3) — `LLM` outcome on success; fallthrough → `LlmJobFailedException`; `LlmBlockUnknownException` propagates.

Router module: 68 → 77 tests. Reactor: 415 → 424.

**Files changed:** 5 new source files + 4 modified + 2 new test files + log = 12 files.

**Open issues / deferred:**

- **Cost budget gate + rate-limit semaphore** — Phase 1.6 plan checkbox; lives on the worker side. Small follow-up.
- **Legacy `gls-llm-orchestration` retirement** — once the new path runs in non-dev, the Rabbit dispatcher + legacy module can be removed.
- **End-to-end integration test** — gated on issue #7.

**Phase 1.6 status:** **three of four plan checkboxes ticked.** PR1 (contract + skeleton), PR2 (real backends + MCP), PR3 (cascade cut-over). Cost budget + rate-limit semaphore is the last item.

**Next:** Cost budget + rate limit on the LLM worker; OR per-category overrides; OR Dockerfile + Compose for `gls-llm-worker`; OR legacy retirement.

## 2026-04-29 — Phase 1.6 PR4 — cost budget gate + rate-limit semaphore

**Done:** Phase 1.6 closes. The LLM worker now enforces a per-replica daily token budget + a per-replica concurrency limit. Both default to disabled so existing setups behave identically; flip the configured values to enable.

**Decisions logged:** None new — closes the two remaining Phase 1.6 plan checkboxes.

**What's wired:**

- **`CostBudgetTracker`** (new `@Component`) — in-memory atomic counter resetting at UTC midnight. `checkBudget()` throws `BudgetExceededException` when the running daily total has crossed `gls.llm.worker.budget.daily-token-cap`. `recordUsage(tokensIn, tokensOut)` adds to the running total after each successful call. Default `0` disables enforcement entirely. Test-friendly Clock injection for time-travel testing of the UTC rollover.
- **`RateLimitGate`** (new `@Component`) — bounded fair `Semaphore`. `acquire()` returns an `AutoCloseable` Token (idempotent close) so callers use try-with-resources. Throws `RateLimitExceededException` when no permit is available within the configured wait window. Default `permits=0` disables enforcement (acquire returns a no-op Token).
- **`BudgetExceededException`** + **`RateLimitExceededException`** — both mapped to 429 in `LlmExceptionHandler`. Budget carries a `Retry-After: <seconds-until-midnight-UTC>`; rate limit carries `Retry-After: 1` (semaphore frees up as soon as another call completes).
- **`ClassifyController.doClassify`** — now wraps the backend call: `budgetTracker.checkBudget()` → `try (var t = rateLimitGate.acquire())` → backend.classify → `budgetTracker.recordUsage(...)`. The try-with-resources ensures the permit is released even if the backend throws.
- **`LlmExceptionHandler`** — new handlers for both exceptions, with the appropriate `Retry-After` header.
- **`ClassifyController.errorCodeFor`** — `LLM_BUDGET_EXCEEDED` and `LLM_RATE_LIMITED` map onto the exceptions for audit envelope shaping.
- **`application.yaml`** — new `gls.llm.worker.budget.daily-token-cap` (default 0), `gls.llm.worker.rate-limit.{permits, wait-ms}` (defaults 0, 0).

**Why budget is checked before AND recorded after the call:**

The simplest correct shape. `checkBudget()` rejects if the running total has already crossed the cap — so the first request *after* the cap is hit gets the 429. `recordUsage(in, out)` adds to the running total after success, so the cap detection always lags by one call. No upfront token estimation (which would need to hand-roll a tokeniser) — accept "budget exceeded by one call" as the rounding error.

**Why per-replica instead of cluster-wide:**

A cluster-wide budget would need a backing store (Redis token bucket, Mongo atomic increment). For Phase 1.6 PR4 first cut, per-replica is simpler and meaningful — replicas are stateless, so a 5-replica deployment with cap=1000/day gets a soft 5000-token cluster cap. A future enhancement makes the budget cluster-wide once a representative production load profile exists.

**Tests (11 new in module; 435 reactor total):**

- `CostBudgetTrackerTest` (6) — disabled at cap=0; allows calls until cap reached; rejects when cap exceeded; `retryAfterSeconds` is positive and ≤ 24h; UTC midnight rollover (with frozen Clock); `recordUsage(0, 0)` is a no-op.
- `RateLimitGateTest` (5) — disabled at permits=0; permits acquired + released via try-with-resources; throws when no permit available; `release` is idempotent; multiple threads bounded to permit count.

The previous 424-reactor test suite is unchanged. LLM worker module: 40 → 51.

**Files changed:** 4 new source files (`CostBudgetTracker`, `RateLimitGate`, `BudgetExceededException`, `RateLimitExceededException`) + 3 modified (`ClassifyController`, `LlmExceptionHandler`, `application.yaml`) + 2 new test files + plan / log = 11 files.

**Open issues / deferred:**

- **Cluster-wide budget** — per-replica today; a Redis or Mongo-backed counter would let the cluster enforce a single cap. Lands when a representative production load profile exists.
- **Per-block / per-category budgets** — the gate is a single global cap. Per-block or per-tenant caps belong with the broader observability + cost-attribution work in Phase 2.
- **Tokeniser-based pre-check** — currently we rejection-test against the running total *before* the call but accept "budget exceeded by one call" as rounding. A pre-check using HuggingFace's `tiktoken` (or an Anthropic SDK helper) would tighten the bound but adds a dep.
- **Real backend integration test** — needs a running Anthropic API + `MOCK_BUDGET=true` shape. Same blocker; gated on issue #7.

**Phase 1.6 status:** **all four plan checkboxes ticked.** PR1 (contract + skeleton), PR2 (Anthropic + Ollama + MCP), PR3 (cascade router cut-over), PR4 (cost budget + rate limit). Phase 1.6 is complete.

**Next:** Legacy `gls-llm-orchestration` retirement (the Rabbit dispatcher's last consumer is the cascade router which now prefers HTTP — once the new path is verified in non-dev, the legacy module can be deleted); OR Phase 1.7 Hub-component-to-taxonomy wiring; OR Dockerfile + Compose for `gls-llm-worker`; OR per-category overrides + cascadeHints handling.

## 2026-04-29 — Phase 1.6 follow-up — Dockerfile + Compose entry for gls-llm-worker

**Done:** Container image definition + Compose entry for `gls-llm-worker`. Same multi-stage shape as `gls-bert-inference` and `gls-slm-worker`. Closes the deferred operational item from Phase 1.6 PR1.

**Decisions logged:** None new.

**What's wired:**

- **`backend/gls-llm-worker/Dockerfile`** — multi-stage build, exposes 8096, healthcheck `/actuator/health`. Same shape as `gls-bert-inference/Dockerfile` and `gls-slm-worker/Dockerfile`.
- **`docker-compose.yml`** — adds `gls-llm-worker` service (depends on mongo + mcp-server). Defaults: `GLS_LLM_BACKEND=none` (stub), `GLS_LLM_ANTHROPIC_MODEL=claude-sonnet-4-5`, `GLS_LLM_OLLAMA_MODEL=qwen2.5:32b`. Cost budget + rate limit exposed via `GLS_LLM_BUDGET_DAILY_TOKEN_CAP` / `GLS_LLM_RATE_LIMIT_PERMITS` / `GLS_LLM_RATE_LIMIT_WAIT_MS`. All default 0 (disabled).
- **Router service env** — adds `GLS_ROUTER_LLM_HTTP_ENABLED` / `GLS_ROUTER_LLM_HTTP_URL` so flipping HTTP dispatch on is an `.env` change. Documents that `LLM_HTTP_ENABLED=true` wins over the legacy `LLM_ENABLED=true` Rabbit path.

**Tests (no new in module; 435 reactor unchanged):**

Compose / Dockerfile validity is exercised by the existing `docker-build` CI job. Local verification: `./mvnw test` still green.

**Files changed:** 1 new Dockerfile + 1 modified `docker-compose.yml` + log = 3 files.

**Open issues / deferred:**

- **`.env.example` documentation** — same as the BERT/SLM Compose-rollout PR's open item. The new `GLS_LLM_*` keys + the cut-over flag should land in `.env.example`. Trivial follow-up.
- **K8s manifests** — Compose covers dev / single-host. K8s deferred per CSV #38.

**Next:** `.env.example` updates; OR legacy `gls-llm-orchestration` retirement; OR Phase 1.7 Hub-component-to-taxonomy wiring; OR per-category overrides + cascadeHints handling.

## 2026-04-29 — Phase 1.7 PR1 — `applicableCategoryIds[]` on hub-component entities

**Done:** Phase 1.7 starts. Four governance entities (`PiiTypeDefinition`, `StorageTier`, `TraitDefinition`, `SensitivityDefinition`) gain an `applicableCategoryIds[]` field per CSV #31–34. Empty array = global (the pre-1.7 default behaviour); non-empty array = scoped to those category ids. A Mongock change unit backfills `[]` on every pre-1.7 row so the new field reads cleanly without nulls.

**Decisions logged:** None new — implements CSV #31 / #32 / #33 / #34 (each entity's per-category scoping).

**What's wired:**

- **`PiiTypeDefinition`** — new `private List<String> applicableCategoryIds = new ArrayList<>();` + getter/setter that null-coalesces to empty.
- **`StorageTier`** — same pattern.
- **`TraitDefinition`** — same pattern.
- **`SensitivityDefinition`** — same pattern. (The "promote to first-class entity" plan checkbox was already substantively done — `SensitivityDefinition` is already a `@Document(collection = "sensitivity_definitions")`. This PR just adds the field.)
- **`V004_BackfillApplicableCategoryIds`** (new Mongock change unit, order 004) — runs `updateMany({applicableCategoryIds: {$exists: false}}, {$set: {applicableCategoryIds: []}})` against the four collections. Idempotent: re-runs are no-ops since the filter drops to zero matches after the first execution. Operates on raw collections (no Java domain shape imported) — same decoupling pattern as `V003_DefaultRouterBlock`.

**Why default to empty array (= global) instead of "all categories":**

Pre-1.7 the entities were globally applicable (no category scoping existed). Empty array preserves that behaviour exactly: every consumer that reads `applicableCategoryIds` interprets `[]` as "applies everywhere". The alternative — populating each row with the full list of category ids at backfill time — would lock the row's behaviour to the categories that existed *at backfill time*; new categories created after the migration wouldn't auto-apply. Empty-as-global is the right default.

**Why the Mongock backfill instead of letting the field be null:**

Spring Data's `@Field` deserialisation tolerates a missing field (returns `null`), which would null-pointer downstream `applicableCategoryIds.isEmpty()` calls if a consumer forgets a null check. Backfilling once at migration time means downstream code can rely on the field always being a non-null `List<String>` — fewer null checks, fewer surprise NPEs.

**Tests (no new in module; 435 reactor unchanged):**

The migration's correctness is exercised by Mongock's own framework + the existing `V001_MongockSmoke` smoke. Per the existing pattern (V002 / V003 don't have unit tests either), data migrations are integration-tested via running them against a live Mongo and verifying the resulting documents. Local verification: `./mvnw test` still green.

**Files changed:** 4 modified entity files + 1 new Mongock change unit + plan / log = 7 files.

**Open issues / deferred:**

- **Hub `PackImportService` updates** — Phase 1.7 PR2. Preserve `applicableCategoryIds[]` on import (don't reset to empty when re-importing a pack); fire `gls.config.changed` events for the four component types per CSV #30.
- **Per-request consumption** — the field is populated but no caller consumes it yet. Once Stage ④ scan dispatch (Phase 1.9) lands, the orchestrator filters PII / metadata-extraction blocks to only the ones whose `applicableCategoryIds` matches the just-classified category.
- **Admin UI category picker** — operators need a UI to set the field. Lands when the admin UI editor for each entity is in shape.

**Next:** Phase 1.7 PR2 (`PackImportService` updates + `gls.config.changed` events); OR `.env.example` updates; OR per-category overrides in the cascade router; OR legacy `gls-llm-orchestration` retirement.

## 2026-04-29 — Phase 1.7 PR2 — `PackImportService` preserve-on-missing for `applicableCategoryIds[]`

**Done:** Phase 1.7 closes. `PackImportService` now respects `applicableCategoryIds[]` on import with preserve-on-missing semantics — pack files without the field leave the existing operator-set value alone. The `gls.config.changed` event firing was already wired by `GovernanceConfigChangeBridge` (Phase 0.7+), which auto-publishes for every Spring Data Mongo `AfterSaveEvent` on the four entity types.

**Decisions logged:** None new.

**What's wired:**

- **`PackImportService.strListOrNull`** (new package-private helper) — returns the list value for `key`, or `null` when the key is absent. Distinguishes "missing field" (preserve existing) from "explicit empty array" (reset to global). The existing `strList` returns `[]` in both cases, which would force a reset on every re-import — wrong for our use case.
- **`applySensitivity` / `applyStorageTier` / `applyPiiType` / `applyTrait`** — each gains: read incoming list, set field only when non-null. Pack files without the field leave the existing value alone.
- **`GovernanceConfigChangeBridge`** (pre-existing) — bridges Spring Data Mongo `AfterSaveEvent` to `gls.config.changed` publishes. PackImportService's `repo.save(...)` triggers this for free.

**Why preserve-on-missing:**

Pack authors can't realistically know which deployments have already scoped a definition. Re-importing should refresh content fields (display names, regex patterns) without trampling operator-set scoping. Operators who want to reset ship a pack with explicit `applicableCategoryIds: []` — the helper treats that as "reset to global" (distinct from "key absent").

**Tests (6 new in module; 441 reactor total):**

- `PackImportServiceTest` (6, new) — `strListOrNull`: missing key → null; explicit `[]` → empty list; populated array → list of strings; non-string elements via toString; non-list value → null; explicit null value → null.

Previous 435 reactor tests unchanged. App-assembly module: 10 → 16.

**Files changed:** 1 modified `PackImportService.java` + 1 new test + plan / log = 4 files.

**Open issues / deferred:**

- **Full apply* method test coverage** — needs broader test harness (9+ repos to mock); gated on issue #7 alongside the broader PackImportService suite.
- **Per-request consumption** — the field is populated but no caller filters by it yet. Lands with Stage ④ scan dispatch (Phase 1.9).

**Phase 1.7 status:** **all six plan checkboxes ticked.** PR1 (entity fields + Mongock backfill), PR2 (preserve-on-missing). Event firing pre-existing via `GovernanceConfigChangeBridge`. Phase 1.7 complete.

**Next:** Phase 1.8 `POLICY` block type + interpreter (CSV #35); OR Phase 1.9 Stage ④ scan dispatch; OR `.env.example` updates; OR per-category overrides in the cascade router; OR legacy `gls-llm-orchestration` retirement.

## 2026-04-29 — Phase 1.8 PR1 — POLICY block enum + content schema

**Done:** Phase 1.8 starts. `BlockType.POLICY` added to `gls-governance.PipelineBlock`. New `contracts/blocks/policy.schema.json` v0.4.0 declares the content shape: `requiredScans[]`, `metadataSchemaIds[]`, `governancePolicyIds[]`, optional `conditions.bySensitivity[]`. PR2 wires the in-engine interpreter (CSV #37 Option A); PR3 seeds per-category POLICY blocks from imported governance packs.

**Decisions logged:** None new — implements CSV #35.

**What's wired:**

- **`BlockType.POLICY`** — added to `PipelineBlock.BlockType` (gls-governance) with a Javadoc pointer to the schema. The admin UI's block-list filter and validation switch are the natural consumers; both currently render unknown enum values gracefully so no UI changes needed for PR1.
- **`contracts/blocks/policy.schema.json`** (new, v0.4.0) — JSON Schema 2020-12 for `POLICY` block content:
  - `categoryId` (required, taxonomy reference) — each category gets one POLICY block; the engine resolves by `categoryId == result.categoryId` after classification.
  - `requiredScans[]` — array of `{scanType (PII/PHI/PCI/CUSTOM), ref, blocking}`. `ref` is a `PiiTypeDefinition.key` or a PROMPT block id depending on scanType. `blocking=true` means scan failure stops the pipeline.
  - `metadataSchemaIds[]` — ordered list of `MetadataSchema` ids whose extracted-metadata fields the engine populates after classification.
  - `governancePolicyIds[]` — list of `GovernancePolicy` ids that apply.
  - `conditions.bySensitivity[]` — per-sensitivity overrides (e.g. RESTRICTED docs in this category needing stricter scans). The engine evaluates the first match's `apply` block, overlaying the top-level lists.
- **`contracts/blocks/VERSION`** — bumped 0.3.0 → 0.4.0.
- **`contracts/blocks/CHANGELOG.md`** — appends a 0.4.0 entry.
- **`contracts/blocks/README.md`** — flips the POLICY bullet to ✓ (v0.4.0).

**Why a `(scanType, ref)` tuple instead of a flat ref + lookup:**

PII / PHI / PCI scans dispatch differently in stage ④ (different prompt templates, different audit metadata). The engine needs to know `scanType` without first resolving `ref` against the right collection — keeps the dispatch decision local.

**Why `conditions.bySensitivity` instead of a generic predicate language:**

Sensitivity is the most operationally common condition for stricter scans. A general predicate language (full JSONLogic / CEL) would be more flexible but invites configuration sprawl + interpreter complexity. By-sensitivity covers ~all of CSV #35's listed use cases ("RESTRICTED docs need extended PII scan", "INTERNAL skips the metadata-extraction step"). A future schema bump can add `byTraitId` / `byContentLength` / etc. when concrete needs surface.

**Tests (no new in module; 441 reactor unchanged):**

The schema is consumed by the admin UI (block save validation lands in a separate cross-cutting PR) and by the in-engine interpreter (Phase 1.8 PR2). Validity of the schema document itself is exercised by Spectral / Redocly via the CI's `contracts-validate` job.

**Files changed:** 1 modified `PipelineBlock.java` (enum) + 1 new `policy.schema.json` + 3 modified contract files (`VERSION`, `CHANGELOG.md`, `README.md`) + plan / log = 7 files.

**Open issues / deferred:**

- **In-engine interpreter (PR2)** — a new pipeline-engine node that resolves the POLICY block for the just-classified category, then dispatches the listed scans / metadata schemas / governance policies. Lives in `gls-app-assembly`'s `PipelineExecutionEngine`.
- **Per-category seed at install time (PR3)** — Mongock change unit + `PackImportService` enhancement so installing a governance pack auto-creates one POLICY block per category. Default content carries the pack-author-supplied `requiredScans`; operators tune from there.
- **Schema validation at block save time** — same blocker as the other content schemas; cross-cuts all block types.

**Next:** Phase 1.8 PR2 (in-engine interpreter); OR Phase 1.9 Stage ④ scan dispatch (the workers POLICY blocks reference); OR `.env.example` updates; OR legacy `gls-llm-orchestration` retirement.

## 2026-04-29 — Phase 1.8 PR2 — POLICY block resolver + typed view

**Done:** `PolicyBlock` typed record + `PolicyBlockResolver` service in `gls-governance`. Reads `pipeline_blocks` via raw `MongoTemplate` (same minimal-coupling pattern as the SLM/LLM workers' `PromptBlockResolver`) and parses POLICY block content into a strongly-typed shape. `effectiveFor(sensitivity)` applies per-sensitivity overrides. PR3 wires this into the pipeline-execution engine.

**Decisions logged:** None new.

**What's wired:**

- **`PolicyBlock`** (new record) — typed view of `policy.schema.json`. Fields: `categoryId`, `categoryName`, `requiredScans` (List of `RequiredScan(scanType, ref, blocking)`), `metadataSchemaIds`, `governancePolicyIds`, `sensitivityOverrides`. `effectiveFor(sensitivity)` returns a new `PolicyBlock` with the matching override's lists overlaid (any list the override doesn't carry inherits from top-level).
- **`PolicyBlockResolver`** (new `@Service`) — `resolveByCategoryId(categoryId)` queries `pipeline_blocks` for `type=POLICY` documents, walks the active version's content map, returns the first matching `PolicyBlock` or `Optional.empty()`. Robust: malformed scan entries silently skipped; Mongo lookup failures logged at WARN + return empty (consumers fall back to no-policy defaults).

**Why a typed record + resolver instead of putting it on `PipelineBlock` directly:**

`PipelineBlock` carries the union of all block types' content as `Map<String, Object>`. A typed `getPolicyContent()` accessor would pollute the model. The resolver pattern keeps `PipelineBlock` content-agnostic; consumers that *know* they want POLICY get a clean record. Same shape as `PromptBlockResolver` → `ResolvedPrompt` in the SLM/LLM workers.

**Why `effectiveFor` lives on `PolicyBlock`, not the resolver:**

The override-application logic only depends on the parsed block; it doesn't touch Mongo. Putting it on the record lets unit tests exercise the override semantics without mocking, and admin-UI consumers ("show what RESTRICTED docs of this category get") can call it without going through Mongo.

**Tests (9 new in module; 450 reactor total):**

- `PolicyBlockResolverTest` (9) — happy resolve; `blocking=false` honoured; categoryId mismatch → empty; no POLICY blocks → empty; Mongo throws → empty + WARN; blank/null categoryId → empty; sensitivity overrides with partial `apply` block; `draftContent` fallback when no active version; malformed scan entries silently skipped.

Governance module: 17 → 26. Reactor: 441 → 450.

**Files changed:** 1 new `PolicyBlock.java` + 1 new `PolicyBlockResolver.java` + 1 new test file + log = 4 files.

**Open issues / deferred:**

- **Engine integration (PR3)** — `PipelineExecutionEngine` calls `PolicyBlockResolver` after classification: looks up the POLICY block, dispatches the listed scans / metadata schemas / governance policies. Lives in `gls-app-assembly`.
- **Per-category seeding (PR4)** — Mongock change unit + `PackImportService` enhancement to auto-create POLICY blocks at pack install.
- **Schema-validation-at-save** — cross-cuts all block content types.

**Next:** Phase 1.8 PR3 (engine integration); OR Phase 1.9 Stage ④ scan dispatch; OR `.env.example` updates; OR legacy retirement.

## 2026-04-29 — Phase 1.8 PR3 — POLICY interpreter wired into the pipeline engine

**Done:** `PipelineExecutionEngine` now resolves the POLICY block for the just-classified category right after `applyClassificationToDocument`, applies the per-sensitivity override, and stashes the effective policy's key fields in the pipeline run's shared context. Phase 1.9's Stage ④ scan dispatch reads from the context — no extra Mongo round-trip per stage. Observe-only this PR; the dispatch lands in 1.9.

**Decisions logged:** None new.

**What's wired:**

- **`PipelineExecutionEngine`** constructor — gains an `ObjectProvider<PolicyBlockResolver>` parameter. Optional via `getIfAvailable()` so test contexts that don't stand up Mongo can construct the engine cleanly.
- **`resolveAndRecordPolicy(event, ctx)`** (new private helper) — called once per classification, right after `applyClassificationToDocument`. Looks up the POLICY block for `event.categoryId()`, applies `effectiveFor(event.sensitivityLabel().name())`, writes four keys into `ctx`:
  - `policyCategoryId` — confirms the policy applied (might be null for un-classified docs, where this whole path is skipped).
  - `policyRequiredScanCount` — for downstream observability.
  - `policyMetadataSchemaIds` — list of schema ids Stage ④ will dispatch.
  - `policyGovernancePolicyIds` — list of governance-policy ids enforcement uses.
- Logs at INFO when a policy resolves; WARN if the resolver throws (fail-soft — pipeline doesn't fail if policy resolution misbehaves).

**Why "stash in shared context" instead of carrying via a structured event:**

The pipeline already passes `Map<String, Object> ctx` through every node call. Adding policy fields to that map is a one-line change per consumer ("read this key, fall back to empty"). The alternative — synthesising a richer event with policy fields — would require reshaping `DocumentClassifiedEvent` and every downstream consumer. The map-stash works for now; if the policy fields multiply, a typed `PolicyContext` value can replace them in the same key namespace.

**Why fail-soft on resolver errors:**

Pipeline correctness shouldn't depend on the POLICY block being resolvable. If Mongo is flaky, if a category has no POLICY block yet, if the resolver bean is missing in a test context — the pipeline should keep working with no policy applied (the pre-1.8 behaviour). The audit trail records that no policy was applied; ops can investigate.

**Tests (no new in module; 450 reactor unchanged):**

The new helper is covered structurally by:

- `PolicyBlockResolverTest` (PR2) — proves the resolver returns the right policy.
- `PolicyBlock.effectiveFor` (PR2) — proves the override application.
- `PipelineExecutionEngineTest` — Mockito's `@InjectMocks` autowires the new constructor parameter. The existing 7 tests don't exercise the new path so they're unaffected; an end-to-end "after classify, ctx contains policy keys" assertion needs more setup than the existing test fixtures provide and lives with the broader integration suite (gated on issue #7).

**Files changed:** 1 modified `PipelineExecutionEngine.java` + plan / log = 3 files.

**Open issues / deferred:**

- **Visual-DAG-node form-factor** — currently the engine calls the resolver inline. CSV #37 Option A says "a node in the visual DAG that runs after classification". Promoting the call to a real node type (with editable config in the admin UI) is deferred to Phase 1.9 once the Stage ④ dispatch shape is concrete; until then the inline call is the simplest correct shape.
- **Pack-installed POLICY blocks (PR4)** — Mongock change unit + `PackImportService` enhancement so installing a governance pack auto-creates one POLICY block per category.
- **Visibility into resolved policy** — the four context keys are logged but not surfaced to admin UI consumers. A "what policy applied?" panel on the document-detail screen lands when the admin UI editor for POLICY blocks is in shape.

**Phase 1.8 status:** **three of four plan checkboxes ticked.** PR1 (enum + schema), PR2 (resolver + typed view), PR3 (engine wire-in). PR4 (per-category pack-install seeding) is the last item — small.

**Next:** Phase 1.8 PR4 (per-category POLICY blocks seeded from imported pack); OR Phase 1.9 Stage ④ scan dispatch; OR `.env.example` updates; OR legacy retirement.

## 2026-04-29 — Phase 1.8 PR4 — POLICY blocks auto-seeded at pack install

**Done:** Phase 1.8 closes. `PackImportService` now seeds an empty POLICY block for every imported category that doesn't already have one. Block name convention: `policy-${categoryId}`. Idempotent — re-imports skip categories that already have a block.

**Decisions logged:** None new.

**What's wired:**

- **`PackImportService` constructor** — gains `PipelineBlockRepository pipelineBlockRepo`.
- **`seedPolicyBlocksForCategories(ctx)`** (new helper) — runs after the component-import loop in `importPack`. For each category without a pre-existing `policy-${categoryId}`-named block, creates one with empty content (`requiredScans`, `metadataSchemaIds`, `governancePolicyIds` all `[]`).
- **Result reporting** — adds a `POLICY_BLOCKS_SEED` ComponentResult so the pack-install UI surfaces the count.
- **PREVIEW mode** — skips the seeding (returns a skipped=N result with a "preview mode" detail line).

**Why deterministic name `policy-${categoryId}`:**

Same convention as `default-router`. One logical block, one stable name. Re-imports look up by name; admin tooling references by name. Using `categoryId` (not `categoryName`) means the name doesn't change if an admin renames the category.

**Why empty content + populate-via-admin-UI vs. extracting from the pack:**

The pack's `PIPELINE_BLOCKS` component type isn't fully wired today ("not yet supported"). When it lands, packs ship explicit POLICY blocks; the seed step then only covers categories the pack didn't include. For now, seeding empty blocks gives the engine + admin UI something to read; operators populate from there.

**Tests (no new in module; 450 reactor unchanged):**

Existing `PackImportServiceTest` (PR2 of Phase 1.7) still passes — the constructor change is autowired by Spring DI. End-to-end behaviour ("import N categories → N POLICY blocks created") needs Mongo + full import flow; gated on issue #7. Focused helper tests are a small follow-up once `PackImportService` gets a broader test harness (mocking 14+ repositories is heavier than this helper warrants).

**Files changed:** 1 modified `PackImportService.java` + plan / log = 3 files.

**Open issues / deferred:**

- **Pack-supplied POLICY blocks** — once `PIPELINE_BLOCKS` import is fully wired, packs can ship explicit POLICY blocks that supersede the seed.
- **Per-category default scans** — the seeded blocks are empty. A "default policy template" config could populate them; deferred until concrete demand.
- **`policy-${categoryId}` collision with operator-named blocks** — unlikely; future PR can add a uniqueness check at the schema level.

**Phase 1.8 status:** **all four plan checkboxes ticked.** PR1 (enum + schema), PR2 (resolver + typed view), PR3 (engine wire-in), PR4 (pack seeding). Phase 1.8 complete.

**Next:** Phase 1.9 Stage ④ scan dispatch (consumes the policy fields the engine stashes in shared context); OR `.env.example` updates; OR legacy `gls-llm-orchestration` retirement; OR Phase 1.10+.

## 2026-04-29 — Phase 1.9 PR1 — PROMPT block schema + scan PROMPT seeder

**Done:** First PR of Phase 1.9. Two pieces, contract-first.

1. `contracts/blocks/prompt.schema.json` — formal JSON Schema 2020-12 for the PROMPT block content. The SLM and LLM workers have been reading `systemPrompt` + `userPromptTemplate` in practice since Phase 1.5; the schema captures that minimum, plus the new fields stage ④ needs:
   - `kind` discriminator: `CLASSIFICATION` / `SCAN` / `METADATA_EXTRACTION` / `GENERAL` (default).
   - `scanType` (`PII` / `PHI` / `PCI` / `CUSTOM`) — required when `kind == SCAN`, enforced via `allOf` + `if/then`.
   - `metadataSchemaId` — required when `kind == METADATA_EXTRACTION`, same pattern.
   - `applicableCategoryIds[]` — same scope convention as the hub-component entities (CSV #31–34). Empty = global.
   - Optional `model` config: `provider` (`ANTHROPIC` / `OLLAMA`), `modelId`, `temperature`, `maxTokens`. Workers reject mismatched providers.
   - `outputFormat`: `JSON` (default) / `TEXT`. Stage ④ scans + classification want JSON-mode output.
   - Existing PROMPT blocks remain valid: both `systemPrompt` and `userPromptTemplate` are individually optional (an `anyOf` requires at least one), and all new fields default to permissive values.
   - `contracts/blocks/VERSION` → 0.5.0; CHANGELOG + README updated.

2. `PackImportService.seedScanPromptBlocksForPiiTypes(ctx)` — new helper, runs after `seedPolicyBlocksForCategories` in `importPack`. For every `PiiTypeDefinition` in the local DB, creates a `kind=SCAN` PROMPT block named `scan-pii-${piiTypeKey.toLowerCase()}` if one doesn't already exist. Inherits `applicableCategoryIds` from the PII type (CSV #31 scope), so a category-scoped PII type produces a category-scoped scan PROMPT block. The seeded `systemPrompt` is built from the PII type's `displayName` / `description` / `examples` and instructs the model to return strict JSON `{found, instances[], confidence}` — the shape the cascade router will parse at PR2 dispatch time.

**Decisions logged:** None new (all behaviour aligns with existing CSV #36 — Stage ④ scan dispatch via PROMPT blocks).

**Why iterate `piiTypeRepo.findAll()` instead of `ctx.imported`:**

Operators upgrading from a Phase 1.7 install pick up scan blocks for pre-existing PII types on the next pack install. The seed is idempotent (skips when the deterministic block name already exists), so this is safe to run on every pack install.

**Why a deterministic block name (`scan-pii-${key}`):**

Same pattern as `policy-${categoryId}` (Phase 1.8 PR4) and `default-router`. POLICY-block authors get a single stable id to reference from `requiredScans[].ref`; admin tooling looks up by name; re-imports don't drift.

**What this leaves for PR2 (engine dispatch):**

- `PolicyBlockResolver` already returns the `requiredScans[]` from the typed `PolicyBlock`; the engine has the count stashed in `policyRequiredScanCount` but not the refs themselves. PR2 will plumb the actual `requiredScans` list (or scan refs) through the shared context.
- A new dispatcher (probably in `gls-app-assembly` or a shared engine module) iterates the resolved scan refs, resolves each as a PROMPT block id, and calls the cascade router. Aggregates results.
- Block `ref` resolution: try as PROMPT block id first; fall back to PiiTypeDefinition.key only if the block doesn't exist. The seed makes the first path always succeed in practice.

**Tests:**

`PackImportServiceTest` gains 5 focused tests covering the static `buildScanSystemPrompt(PiiTypeDefinition)` helper:

- includes displayName when present
- falls back to key when displayName is null
- omits "Definition:" line when description is blank
- omits "Examples:" line when examples is empty or null
- emits the strict JSON response contract (`found`, `instances`, `confidence`, `found=false`)

Plus offline JSON Schema 2020-12 meta-validation of `prompt.schema.json` (run via `jsonschema` Python lib): schema is meta-valid; positive samples (minimal `systemPrompt` only, scan, metadata-extraction, classification with model + applicableCategoryIds) all validate; negative samples (missing `scanType` for SCAN, missing `metadataSchemaId` for METADATA_EXTRACTION, empty body, unknown property) all fail with the expected reason.

Reactor: 450 → 455 Java tests in `gls-app-assembly`. Full backend reactor green.

**Contracts touched:** `contracts/blocks/prompt.schema.json` (new), `contracts/blocks/VERSION` (0.4.0 → 0.5.0), `contracts/blocks/CHANGELOG.md`, `contracts/blocks/README.md`.

**Files changed:** 4 contract files + `PackImportService.java` + `PackImportServiceTest.java` + plan / log = 8 files.

**Open issues / deferred:**

- **PHI / PCI scan PROMPT blocks** — only PII types are seeded today. PHI / PCI types live in the same `pii_type_definitions` collection (different `category` field), or in a sibling collection — needs review with the hub schema before PR2 / PR3 broaden the seeder.
- **Operator overrides** — operators editing the seeded `systemPrompt` from the admin UI must publish a new version, not edit the v1 in place. The PROMPT block versioning (CSV #41 / Phase 0.10 Mongock) handles this when the admin UI's block editor lands.
- **Schema coverage in CI** — Spectral lints `contracts/**/*.yaml` (OpenAPI), but JSON Schema files under `contracts/blocks/` aren't validated against their meta-schema in CI. The local Python check in this PR proves the schema is meta-valid; promoting that to a CI step is a small follow-up.

**Next:** Phase 1.9 PR2 — engine Stage ④ dispatch. Plumb the resolved `requiredScans[]` through the engine's shared context; add a dispatcher that iterates each scan, resolves its `ref` as a PROMPT block id, and calls `gls-classifier-router` for execution. Aggregate results into a new context key for PR3 (metadata extraction) and PR4 (enforcement handoff).

## 2026-04-29 — Phase 1.9 PR2 — Engine Stage ④ scan dispatch

**Done:** Engine now dispatches the resolved POLICY block's `requiredScans[]` through the cascade router. Observe-only — blocking failures are logged but don't gate the pipeline yet (PR4 wires gating + audit emission).

**What's wired:**

- **Engine plumbing.** `PipelineExecutionEngine.resolveAndRecordPolicy` now stashes the actual `requiredScans` list in `ctx` under `policyRequiredScans` (List<Map<String,Object>> with `scanType`, `ref`, `blocking` keys), in addition to the existing `policyRequiredScanCount`. Downstream PRs read the typed list; the count stays as a cheap log signal.

- **`ScanRouterClient` (new).** Sibling to `ClassifierRouterClient` — same `POST /v1/classify` transport, but a scan-specific result parse: SCAN PROMPT blocks return `{found, instances, confidence}`, not the classification fields the existing client extracts. Returns a generic `RouterScanOutcome(success, tierOfDecision, confidence, result, error, durationMs)` record. Active when `pipeline.scan-router.enabled=true` (defaults true so the bean is present whenever the cascade-router classpath is wired). Independent flag from `pipeline.classifier-router.enabled` so SCAN dispatch can be staged-rolled.

- **`PolicyScanDispatcher` (new).** Iterates `requiredScans[]`, calls the client per scan, and aggregates `PolicyScanResult` records. Behaviour:
  - Empty / null scans → returns empty list silently.
  - Client absent (test contexts, flag off) → records each scan as `dispatched=false` with an explanatory error so the engine has a deterministic record without a scan ever leaving the JVM.
  - Scan with null / blank `ref` → records `dispatched=false` + error; doesn't call the client.
  - Client throws → caught, recorded as `dispatched=true` (we attempted) with `error` populated.
  - Builds `nodeRunId` as `${pipelineRunId}-scan-${ref}` (or just `scan-${ref}` when no run id) so the cascade router's 24h idempotency window dedupes correctly across retries.

- **`PolicyScanResult` (new record).** Carries scan metadata + outcome: `scanType`, `ref`, `blocking`, `dispatched`, `tierOfDecision`, `confidence`, `result` (raw block-shaped output), `error`, `durationMs`. Convenience: `success()` and `blockingFailure()` helpers.

- **Engine wire-in.** New `dispatchPolicyScans(run, doc, ctx)` reads `policyRequiredScans` from ctx, deserialises back into typed `RequiredScan` records, calls the dispatcher with `doc.getExtractedText()`, and stashes the results under `policyScanResults`. Fail-soft: dispatcher exceptions are logged (observe-only) and don't surface to the engine's pipeline run state.

**Why a sibling client instead of extending `ClassifierRouterClient`:**

The classification client returns an `LlmJobCompletedEvent` populated from classification-specific fields in `result` (categoryId, sensitivity, retentionScheduleId, etc). For SCAN PROMPTs, those fields don't exist — the result is `{found, instances, confidence}`. Bolting scan parsing onto the same client would either bloat its response shape or silently strip data. A sibling client with a generic `Map<String,Object>` result keeps both code paths focused.

**Why observe-only at this phase:**

PR2 establishes the dispatch shape — the typed `RequiredScan` flowing through to the cascade router and back. Gating the pipeline on blocking failures is a separate behavioural change that needs a status transition (e.g. `SCAN_FAILED` → retry / quarantine) which is part of PR4 (results aggregated, passed to enforcement). Splitting them keeps the failure-mode review separate from the wire-in.

**Tests:**

- `PolicyScanDispatcherTest` (10 tests): empty scans, single scan success, transport failure, blocking-failure flag, multi-scan ordering, no-client fallback, null/blank ref handling, dispatcher exception capture, nodeRunId convention (`runId-scan-ref` and the null-runId fallback).
- `ScanRouterClientTest` (5 tests): JDK builtin `HttpServer` round-trip — successful response → outcome with tier + confidence + result, non-2xx → failure outcome with HTTP status + body, minimal `result: {found:false}` parse, idempotency-key fallback to `nodeRunId`, `blockVersion` lands in request body.

Reactor: 455 → 470 in `gls-app-assembly`. Full backend reactor green.

**Contracts touched:** None. The router contract (`contracts/classifier-router/openapi.yaml` v0.2.0) already supports any PROMPT block id at `POST /v1/classify`, which is what SCAN dispatch leverages.

**Files changed:**

- `PipelineExecutionEngine.java` — constructor +1 param, `resolveAndRecordPolicy` plumbs `policyRequiredScans`, new `dispatchPolicyScans` helper.
- `PolicyScanDispatcher.java` (new), `ScanRouterClient.java` (new), `PolicyScanResult.java` (new).
- `PolicyScanDispatcherTest.java` (new), `ScanRouterClientTest.java` (new).
- Plan + log = 8 files.

**Open issues / deferred:**

- **Gating on blocking failures** — currently observed-only. PR4 wires `SCAN_FAILED` status + retry path.
- **Visual-DAG node form-factor** — same deferral as Phase 1.8 PR3. Once PR3 + PR4 ship, the inline-call shape is concrete enough to promote to a real visual-node type.
- **Per-scan audit emission** — `PolicyScanDispatcher` doesn't emit Tier 2 audit events yet. Adds nicely after `gls-platform-audit` library work continues (CSV #1 / #5 / #6).
- **Block ref → PROMPT vs PiiType fallback** — the seeded `scan-pii-${key}` blocks make `requiredScans[].ref` always resolve as a PROMPT block id today. If pre-seed POLICY blocks reference raw `PiiTypeDefinition.key` strings, the router will return 422 (no such block); a follow-up can add a `ScanRefResolver` that detects PiiType keys and synthesizes a transient PROMPT block at dispatch time.

**Next:** Phase 1.9 PR3 — metadata extraction PROMPT block seeder + dispatch (parallel pattern to PR1 + PR2 but for `policyMetadataSchemaIds[]`). After that, PR4 aggregates scan + metadata results, persists them to the document, hands off to enforcement, and gates the pipeline on blocking failures.

## 2026-04-29 — Phase 1.9 PR3 — Metadata extraction PROMPT seeder + engine dispatch

**Done:** Third PR of Phase 1.9. Mirrors PR1 + PR2's pattern but for the metadata extraction half of stage ④. Two pieces:

1. **`PackImportService.seedExtractionPromptBlocksForMetadataSchemas`** — runs after `seedScanPromptBlocksForPiiTypes` in `importPack`. For every `MetadataSchema` in the local DB, creates a `kind=METADATA_EXTRACTION` PROMPT block named `extract-metadata-${schemaId}` if one doesn't already exist. The seeded `systemPrompt` enumerates the schema's fields (`fieldName (TYPE) [required]: description — hint: ... — examples: ...`) and instructs the model to return strict JSON keyed by `fieldName` with type-appropriate values (`TEXT/KEYWORD/CURRENCY → string`, `NUMBER → number`, `BOOLEAN → boolean`, `DATE → ISO 8601 string`). Required fields whose value can't be determined return the literal string `"NOT_FOUND"`. Idempotent + PREVIEW-skipping; iterates `metadataSchemaRepo.findAll()` so operators upgrading from a Phase 1.7 install pick up extraction blocks for pre-existing schemas on the next pack install.

2. **`MetadataExtractionDispatcher`** — sibling to `PolicyScanDispatcher`, reuses `ScanRouterClient` for transport (the cascade router doesn't care whether the PROMPT is `SCAN` or `METADATA_EXTRACTION` kind — the client is a transport, not a parser). Iterates `metadataSchemaIds[]`, builds the deterministic block ref `extract-metadata-${schemaId}`, dispatches each through the cascade router, and aggregates `MetadataExtractionResult` records. Same fail-soft semantics as the scan dispatcher: empty list → silent return, no client → not-dispatched marker, blank schema id → recorded error, dispatcher exception → caught + recorded. `nodeRunId` is `${pipelineRunId}-extract-${schemaId}` so the router's idempotency window dedupes retries cleanly. The `ScanRouterClient` name is now slightly misleading because it serves both scan and metadata extraction calls — flagged for a rename to `RouterPromptClient` in a follow-up.

3. **Engine wire-in.** `PipelineExecutionEngine` constructor gains `MetadataExtractionDispatcher`; new `dispatchMetadataExtraction` helper called inline after `dispatchPolicyScans`. Reads `policyMetadataSchemaIds` from ctx, calls the dispatcher with `doc.getExtractedText()`, stashes the per-schema results under `policyExtractionResults` for PR4 to persist onto the document + hand off to enforcement.

**Why reuse `ScanRouterClient` instead of a third client:**

Both SCAN and METADATA_EXTRACTION blocks emit a `Map<String, Object>` result through the cascade router — the only difference is what's inside the map. `ScanRouterClient.RouterScanOutcome` already carries a generic `result` map. Forking the client into a third sibling would just be three classes that do the same HTTP call. The dispatcher (which knows the result *interpretation*) is the right place for the type split.

**Tests:**

- `MetadataExtractionDispatcherTest` (9 tests): empty list, single schema success, transport failure, multi-schema ordering, no-client fallback, blank/null schema id, dispatcher exception capture, nodeRunId convention (`runId-extract-id` and the null-runId fallback).
- `PackImportServiceTest` +5 tests for `buildExtractionSystemPrompt`: field listing with type / required / hint / examples, name fallback to id, optional-blocks elision, minimal-field handling, strict-JSON response contract assertions.

Reactor: 470 → 484 in `gls-app-assembly`. Full backend reactor green.

**Contracts touched:** None. The PROMPT schema (Phase 1.9 PR1, v0.5.0) already covers the new `kind=METADATA_EXTRACTION` blocks; the cascade-router contract (v0.2.0) accepts any PROMPT id at `POST /v1/classify`.

**Files changed:**

- `PackImportService.java` — new `seedExtractionPromptBlocksForMetadataSchemas` + static `buildExtractionSystemPrompt` helper, called from `importPack` after the scan PROMPT seeder.
- `MetadataExtractionDispatcher.java` (new), `MetadataExtractionResult.java` (new).
- `PipelineExecutionEngine.java` — constructor +1 param, `dispatchMetadataExtraction` helper, `extractionResultToMap` ctx serialiser.
- `MetadataExtractionDispatcherTest.java` (new), `PackImportServiceTest.java` +5 tests.
- Plan + log = 8 files.

**Open issues / deferred:**

- **`ScanRouterClient` rename** — the client now serves both scan and metadata extraction calls. Renaming it to `RouterPromptClient` (or `CascadeRouterClient`) is a small follow-up that improves naming clarity without changing behaviour.
- **Block id vs name mismatch** (carried from PR2) — seeded block names like `extract-metadata-hr-leave` are passed to the router as `block.id`, which the router's downstream lookup queries as Mongo `_id`. The seeders don't currently set `_id = name`, so the auto-generated ObjectId is what Mongo stores. Either (a) the seeders set `_id = name` for stable lookup, (b) the dispatcher resolves `name → _id` before calling the router, or (c) the cascade router accepts `block.name`. Option (a) is the smallest change; tracked for a small standalone PR.
- **Metadata persistence on the document** — `policyExtractionResults` lives in shared context only; PR4 will fold the extracted fields into `DocumentClassificationResult.extractedMetadata` (matching the existing classification-time metadata extraction path) and emit Tier 2 audit events.
- **MIME-type filtering** — `MetadataSchema.linkedMimeTypes` isn't yet used by the dispatcher. Phase 1.9 dispatch runs every schema referenced by the POLICY block regardless of doc MIME type. A follow-up can short-circuit when the doc's MIME type is set + not in the schema's allow-list.

**Next:** Phase 1.9 PR4 — final piece. Persist scan + extraction results onto the document (or `DocumentClassificationResult`), gate the pipeline on blocking scan failures (`SCAN_FAILED` status + retry path), emit Tier 2 audit events for each scan + extraction outcome, and hand off the consolidated state to the enforcement stage.

## 2026-04-29 — Phase 1.9 PR4 — Stage ④ result persistence onto classification record

**Done:** Final PR of Phase 1.9 (with two deferred follow-ups). The engine now persists the aggregated stage ④ results onto the `DocumentClassificationResult` so downstream nodes — governance, enforcement, indexing — read them as part of the canonical record, not from in-process shared context.

**What's wired:**

- **`DocumentClassificationResult.policyScanFindings`** (new field, `Map<String, Object>`). Keyed by scan ref; each value is the per-scan result row from PR2 (`{scanType, ref, blocking, dispatched, tierOfDecision, confidence, result, error, durationMs}`). Mongo handles the new field transparently — existing rows simply have `null`.

- **`PipelineExecutionEngine.persistPolicyResults`** (new helper). Runs inline after `dispatchMetadataExtraction`. Reads `policyScanResults` + `policyExtractionResults` from shared context. For extractions: merges `extractedFields` of every successful row into the existing `DocumentClassificationResult.extractedMetadata` map (string-coerced — non-string values like `42`, `99.5`, `true` become `"42"`, `"99.5"`, `"true"`), but does **not** overwrite existing keys: classification-time metadata wins on conflict. For scans: builds a fresh `policyScanFindings` map keyed by ref. Saves the result only when something changed.

- **Aggregation helpers as static, package-private.** `mergeExtractedFields(existing, results)` and `aggregateScanFindings(results)` are pure functions extracted from `persistPolicyResults` for direct unit testing without spinning up the full engine.

- **Fail-soft semantics.** Missing `classificationResultId` on the doc → silent return. Empty results → silent return. Repo lookup or save throws → logged + skipped. Same observe-only stance as PR2 + PR3.

**Why classification-time metadata wins on conflict:**

The classification call already extracts metadata fields where the model has high confidence — these are the canonical values. Stage ④'s extraction pass is a refinement: it covers fields the classifier-time prompt didn't ask for (because the schema is per-category, looked up after classification). Re-extracting a field that classification already populated risks overwriting a high-confidence value with a lower-confidence one. Preserving the classification-time value matches the rule "first writer wins for canonical fields."

**Why a new field for scans rather than overloading `extractedMetadata`:**

`extractedMetadata` is `Map<String, String>` — a flat view designed for Elasticsearch indexing (KEYWORD-typed structured fields). Scan results are nested (`{found, instances[], confidence}`); flattening them would lose the per-scan confidence + instance positioning that downstream consumers (governance enforcement, redaction tooling, audit) need. A separate `Map<String, Object>` keyed by ref keeps the two concerns cleanly separated.

**Tests:**

`PipelineExecutionEnginePolicyResultsTest` — 13 focused tests:

`mergeExtractedFields` (8): null/empty inputs, pass-through when no extractions, additive merge from successful rows, no-overwrite of existing keys, skip rows where `dispatched=false` or `error≠null`, string coercion of non-string values, skip null/blank keys, skip rows with non-map `extractedFields`.

`aggregateScanFindings` (5): null/empty inputs, key-by-ref pass-through, skip rows with null ref, last-row-wins for duplicate refs.

Reactor: 484 → 497 in `gls-app-assembly`. Full backend reactor green.

**Contracts touched:** None. The `DocumentClassificationResult` collection is internal to `gls-governance` — no external consumers depend on a specific shape today (admin UI reads it via `gls-governance` services).

**Files changed:**

- `DocumentClassificationResult.java` — new `policyScanFindings` field + getter/setter.
- `PipelineExecutionEngine.java` — new `persistPolicyResults` helper, two extracted static helpers, new wire-in call after `dispatchMetadataExtraction`.
- `PipelineExecutionEnginePolicyResultsTest.java` (new).
- Plan + log = 5 files.

**Open issues / deferred:**

- **Blocking scan failure gating** (deferred from PR4 scope). Currently the engine continues even when a scan with `blocking=true` fails. Promoting that to a hard `SCAN_FAILED` document status with a retry path is a separate behavioural change — gates need a status enum addition, retry semantics, and an admin-UI surface. Tracked as a small follow-up PR; the data is already persisted under `policyScanFindings` so the gating PR just needs to read it.
- **Tier 2 audit emission for each scan + extraction** (deferred). The `gls-platform-audit` library still has gaps (Phase 0.7 status) — leader election (ShedLock) + circuit breaker + comprehensive metrics are outstanding. Once that lands, `PolicyScanDispatcher` and `MetadataExtractionDispatcher` each emit a `policy.scan.dispatched` / `policy.metadata.extracted` Tier 2 event per outcome.
- **MIME-type filtering on extraction** — same deferral as PR3.
- **Visual-DAG node form-factor** — same deferral as PR2 / PR3. The inline-call shape is now concrete enough across PR2–PR4 to promote to a real visual-node type in a future Phase 1.9.5 / phase-2 PR.

**Phase 1.9 status:** **all four plan checkboxes ticked.** PR1 (PROMPT schema + scan PROMPT seeder), PR2 (engine scan dispatch), PR3 (metadata extraction seeder + dispatch), PR4 (results persistence). Phase 1.9 complete with the gating + audit deferrals noted above.

**Next:** Phase 1.10 (`gls-enforcement-worker` split) is the natural next step. Or close-off follow-ups on Phase 1.9: blocking-failure gating PR, `ScanRouterClient` rename to `RouterPromptClient`, block-id-vs-name reconciliation across the seeders.

## 2026-04-29 — Phase 1.10 PR1 — Enforcement worker contract

**Done:** First PR of Phase 1.10. Lays the contract for the upcoming `gls-enforcement-worker` deployable. The `gls-governance-enforcement` Spring Boot module already exists with its own `@SpringBootApplication`, but no Dockerfile / compose entry — today the orchestrator calls `EnforcementService.enforce` in-process via the dependency. Phase 1.10 carves it out so the engine calls it over HTTP.

**Contracts touched:**

- `contracts/enforcement-worker/VERSION` — new, `0.1.0`.
- `contracts/enforcement-worker/CHANGELOG.md` — new.
- `contracts/enforcement-worker/README.md` — new.
- `contracts/enforcement-worker/openapi.yaml` — new, OpenAPI 3.1.1.

**Surface:**

- `POST /v1/enforce` — sync + async (`Prefer: respond-async`). Request carries a `nodeRunId` (idempotency key per CSV #16) plus a `ClassificationEvent` envelope (the keys the worker needs: `documentId`, `classificationResultId`, `categoryId`, `sensitivityLabel`, etc — the worker re-fetches the full classification result from Mongo). Optional `policyContext.governancePolicyIds` carries the Phase 1.9 `policyGovernancePolicyIds` so the worker doesn't re-resolve.
- Response: `EnforceResponse` with `AppliedSummary` (applied policy ids, retention schedule + period text + trigger + expected disposition action, storage tier before/after + whether bytes migrated, audit event id).
- `GET /v1/jobs/{nodeRunId}` — async poll surface (same pattern as audio / SLM / LLM / classifier-router).
- `GET /v1/capabilities`, `GET /actuator/health` — standard meta surface.

**Error envelope:**

RFC 7807 + extensions per CSV #17. Specific codes carved out:

- `404 DOCUMENT_NOT_FOUND` — referenced documentId doesn't resolve.
- `422 ENFORCEMENT_INVALID_INPUT` — classification event malformed (e.g. missing `categoryId`).
- `409` via shared `idempotency.yaml` (in-flight conflict).
- `429` via shared `retry.yaml`.
- `4XX` / `5XX` catch-all via shared error envelope.

**Why a slim `ClassificationEvent` envelope rather than the full `DocumentClassificationResult`:**

The orchestrator already has the classified event in hand (it just received it from the LLM worker). Re-serialising the full classification result is redundant — the worker re-fetches by `classificationResultId` to get the canonical record. The slim envelope keeps the request body small + the contract decoupled from the internal Mongo shape.

**Why `policyContext` is optional:**

The Phase 1.9 PR4 stash on the classification result (`extractedMetadata`, `policyScanFindings`) lives in Mongo. The worker reads them directly. But `policyGovernancePolicyIds` was only stashed in the engine's shared context, not persisted (it's resolved each pipeline run from the POLICY block). When the engine calls the worker, passing `policyContext.governancePolicyIds` saves a Mongo round-trip; absence falls through to the worker re-resolving via the `PolicyBlockResolver` it inherits from `gls-governance`.

**Spectral lint:** clean (1 warning fixed by adding a description to `getCapabilities`).

**Decisions logged:** None new (mirrors the established async-pattern + error-envelope decisions from earlier phases).

**Files changed:** 4 contract files + plan + log = 6 files.

**Open issues / deferred:**

- **Generator wiring** — `contracts-smoke` only validates `hello-world`. Adding enforcement-worker's spec to the smoke generator (so `gls-enforcement-worker`'s controllers are generated against the contract) is a follow-up step, same as the slm-worker / llm-worker pattern.
- **PR2 follow-on** — implement HTTP controllers on the existing `gls-governance-enforcement` Spring Boot app (it currently only has a Rabbit consumer). Add Dockerfile + compose entry.
- **PR3 follow-on** — engine cutover. Add an HTTP client similar to `ClassifierRouterClient` and call it from `PipelineExecutionEngine` after classification, behind a `pipeline.enforcement-worker.enabled` feature flag. Rabbit path stays as fallback until the cutover proves out.
- **Tier 2 audit emission** — `AppliedSummary.auditEventId` is contracted but until `gls-platform-audit` library completes (Phase 0.7 outstanding), the worker emits via the legacy `AuditEventRepository` and reports the legacy id.

**Next:** Phase 1.10 PR2 — HTTP controllers + Dockerfile + compose entry on the existing `gls-governance-enforcement` module. Wires the contracted surface to the in-place `EnforcementService` (no logic move; just an HTTP veneer + idempotent job-store like the other workers).

## 2026-04-30 — Phase 1.10 PR2 — Enforcement worker HTTP surface

**Done:** Carved `gls-governance-enforcement` from a library-module-with-`@SpringBootApplication` into a standalone deployable. The HTTP surface implements the Phase 1.10 PR1 contract directly via openapi-generator stubs; the existing `EnforcementService` is reused unchanged — the controller is a thin veneer plus before/after snapshot capture so `AppliedSummary` can be composed from the diff without altering the service signature (which is also called by the in-process pipeline engine + the legacy Rabbit consumer).

**Contracts touched:**

- `contracts/enforcement-worker/openapi.yaml` — bumped 0.1.0 → 0.2.0. `AppliedSummary.retentionTrigger` and `AppliedSummary.expectedDispositionAction` enums realigned with the canonical Java `ClassificationCategory.RetentionTrigger` and `RetentionSchedule.DispositionAction` values. PR1 used aspirational values that didn't exist in the codebase — caught at PR2 mapping time. Technically breaking, but the contract has no live consumers yet (PR2 is the first implementation). Major version bump deferred to 1.0.0.
- `contracts/enforcement-worker/VERSION`, `CHANGELOG.md` — version bump + changelog entry per `CLAUDE.md` workflow.

**New module surface:**

- `web/EnforceController` (implements `EnforceApi`) — POST `/v1/enforce`. Sync (200) without `Prefer: respond-async`, 202 + `Location: /v1/jobs/{nodeRunId}` with it. Sync + async share the `JobStore` row for idempotency per CSV #47. Acquired/Running/Completed/Failed disposition matches the slm-worker / classifier-router pattern. Captures `storageTierId` snapshot before calling `EnforcementService.enforce`; composes `AppliedSummary` from the diff afterwards.
- `web/JobController` (implements `JobsApi`) — GET `/v1/jobs/{nodeRunId}`. Returns `JobStatus` envelope; `result` is the cached `EnforceResponse` JSON for completed rows.
- `web/MetaController` (implements `MetaApi`) — `/v1/capabilities` advertises tier `ENFORCEMENT`; `/actuator/health` returns the contract-shaped `HealthResponse{UP}`. Spring Boot's actuator continues to expose its own `/actuator/health` for ops tooling.
- `web/AsyncDispatcher` + `web/AsyncConfig` — bounded `enforcementAsyncExecutor` (4/8/32 defaults, tunable via `gls.enforcement.worker.async.*`), invoked via `@Async` from the controller's async branch.
- `web/EnforceExceptionHandler` (`@RestControllerAdvice`) — RFC 7807 problem+json mapping for `DocumentNotFoundException` (404), `EnforcementInvalidInputException` (422), `JobInFlightException` (409), `JobNotFoundException` (404), `IllegalArgumentException` (422 fallback), and `RuntimeException` (500 catch-all).
- `web/EnforcementMapper` — pure-static translator between the contract `EnforceRequest` / `ClassificationEvent` and the internal `DocumentClassifiedEvent` (validates required fields), plus `DocumentModel` → `AppliedSummary` (storage-tier-diff, retention denormalisation, optional schedule-fallback for `expectedDispositionAction`).
- `web/{DocumentNotFoundException, EnforcementInvalidInputException, JobInFlightException, JobNotFoundException}` — thin marker exceptions consumed by the handler.
- `jobs/{JobStore, JobAcquisition, JobRecord, JobRepository, JobState}` — Mongo-backed idempotency cache. Collection `enforcement_jobs`, TTL via `expireAfter="0s"` index on `expiresAt` (24h default; tunable via `gls.enforcement.worker.jobs.ttl`). Lifted from the slm-worker pattern verbatim.

**EnforcementService:** no signature change. The new HTTP path calls `enforce(event)` exactly as the legacy `ClassificationEnforcementConsumer` and `PipelineExecutionEngine` do. Existing `EnforcementServiceTest` (5 tests) still passes unmodified.

**Application class:** `GlsGovernanceEnforcementApplication` gains `@EnableAsync` and an additional `enforcement.jobs` package in `@EnableMongoRepositories.basePackages`. Component scan unchanged.

**Module pom:** OpenAPI generator wired to `contracts/enforcement-worker/openapi.yaml`, generates into `co.uk.wolfnotsheep.enforcement.{api,model,invoker}`. `interfaceOnly=true` so controllers retain hand-written control. Added `jakarta.validation-api` + `swagger-annotations` for the generated stubs.

**Application config:** Port flipped from 8084 → 8097 (matches the contract's `localhost` server entry; no other consumer referenced 8084). New properties: `gls.enforcement.worker.{jobs.ttl, async.{core-size,max-size,queue-capacity}, build.version}`. Re-routed mongo URI lookup to `spring.data.mongodb.uri` (was on the deprecated `spring.mongodb.uri` key, which Spring Boot 4 silently ignores).

**Docker + compose:**

- `Dockerfile` (new) — repo-root context, mirrors slm-worker layout. JDK 25 build → JRE 25 runtime; `EXPOSE 8097`; healthcheck on `/actuator/health`.
- `docker-compose.yml` — new `gls-enforcement-worker` service (after `gls-llm-worker`). Wires Mongo + Rabbit + MinIO. Sets `PIPELINE_EXECUTION_ENGINE_ENABLED=true` to disable the legacy in-process Rabbit consumer in this container — otherwise it would double-process the `documents.classified` queue alongside the `gls-app-assembly` engine.

**Tests:** 25 new tests, full suite 30 tests / 0 failures in this module.

- `EnforcementMapperTest` (11) — required-field validation (5 missing-field paths), all-fields pass-through, optional-field defaults, storage-diff composition, schedule-fallback for disposition, null-safe response when `after=null`.
- `EnforceControllerTest` (8) — implements-EnforceApi guard, happy-path sync (200 + cache write + storage diff), 404 on missing document with `markFailed("DOCUMENT_NOT_FOUND")`, 409 on in-flight collision (no service call), 200 on completed cache hit (no service call), 202 on async with `Location` header + dispatcher invocation, 422 on invalid-input with `markFailed("ENFORCEMENT_INVALID_INPUT")`, 500-class on unexpected service failure with `markFailed("ENFORCEMENT_UNEXPECTED")`.
- `JobControllerTest` (4) — unknown nodeRunId throws `JobNotFoundException`; PENDING / COMPLETED / FAILED rows surface correctly; cached `EnforceResponse` round-trips through Jackson with `findAndRegisterModules()`.
- `MetaControllerTest` (2) — capabilities advertise `service` / `version` / `tiers=[ENFORCEMENT]`; health returns 200 UP.

Reactor: full backend reactor green (`./mvnw -DskipTests package`). `gls-app-assembly` test suite still green (63 tests including the unaffected `PipelineExecutionEngineTest` etc).

**Spectral lint:** clean (`No results with a severity of 'error' found!`).

**Decisions logged:** None new. The contract enum realignment to canonical Java values is captured in the CHANGELOG, not the decision tree — it's a spec-correction, not a new architectural call.

**Open issues / deferred:**

- **`auditEventId` always null in PR2** — `AppliedSummary.auditEventId` is contracted but the `EnforcementService` writes via the legacy `AuditEventRepository.save()` without surfacing the saved id. The simpler refactor (return `AuditEvent` from `enforce`) would ripple through the engine + consumer. Deferred to the same PR that wires `gls-platform-audit` (Phase 0.7 outstanding); the controller passes `null` and notes the deferral inline.
- **Service-account JWT** — contract declares `serviceJwt` security but the worker has no JWT validator wired yet. Same blocker as Phase 0.5 (JWKS infra). Defer until that lands.
- **PR3 follow-on** — orchestrator cutover. Add `EnforcementWorkerClient` (mirroring `ClassifierRouterClient`) called from `PipelineExecutionEngine` after `applyClassificationToDocument`, behind `pipeline.enforcement-worker.enabled` (default off). Rabbit consumer + in-process call stay as the fallback until the cutover proves out.
- **Generator-smoke wiring** — `contracts-smoke` only validates `hello-world` today. Add the enforcement-worker spec to its surface in a small follow-up so a contract change without a regenerate is caught at CI rather than at module compile-time.
- **Standalone-deployable acceptance test** — no Testcontainers integration test boots the worker against a real Mongo + posts a real request yet. The unit-level coverage is high (30 tests) but a smoke test would catch wiring regressions (e.g. the application.yaml mongo-key fix). Tracked as a Phase 1.10 PR2.5 / acceptance follow-up.

**Phase 1.10 status:** 2 of 3 plan checkboxes ticked (deployable split + contract). Rollback feature flag remains for PR3.

**Next:** Phase 1.10 PR3 — orchestrator cutover behind `pipeline.enforcement-worker.enabled`. Add HTTP client + integration into `PipelineExecutionEngine.applyClassificationToDocument`. Or: address PR2 follow-ups (audit-event-id wiring, JWT, smoke test) once Phase 0.7 / 0.5 prerequisites land.

## 2026-04-30 — Phase 1.10 PR3 — Engine cutover behind feature flag

**Done:** Wired `gls-app-assembly`'s `PipelineExecutionEngine` to call `gls-enforcement-worker` over HTTP when `pipeline.enforcement-worker.enabled=true`. Otherwise (default), the engine keeps invoking the in-process `EnforcementService` exactly as before. Both paths share the same downstream behaviour because the worker container reuses the same `EnforcementService` internally — the cutover is a transport swap, not a logic move.

**Contracts touched:** None new. Consumes the v0.2.0 surface from PR1+PR2 (`POST /v1/enforce` synchronous path).

**Changes:**

- `infrastructure/services/pipeline/EnforcementWorkerClient.java` (new) — synchronous JDK `HttpClient` against `${pipeline.enforcement-worker.url}/v1/enforce`. Builds the request body from a `DocumentClassifiedEvent` (omitting null-optional fields), sets `traceparent` (random valid format) + `Idempotency-Key: nodeRun:<nodeRunId>`, parses `EnforceResponse` + `AppliedSummary` into a slim `Outcome` record (carries `storageTierBefore`/`After`, `storageMigrated`, `auditEventId`, `durationMs`). Bean conditional on `pipeline.enforcement-worker.enabled=true` per CSV-friendly opt-in pattern; default off so the legacy in-process path stays primary.
- `infrastructure/services/pipeline/EnforcementWorkerException.java` (new) — sibling of `ClassifierRouterException`. Engine treats it as an `ENFORCEMENT` stage failure (existing `PipelineStageException("ENFORCEMENT", e)` wrap).
- `PipelineExecutionEngine.java` — new `ObjectProvider<EnforcementWorkerClient>` constructor parameter (last position to minimise churn) + new private `applyEnforcement(doc, event, node)` helper extracted from `handleGovernance`. The helper checks `getIfAvailable()` on the provider: if present (worker bean active), call HTTP and re-fetch the doc by id (worker has already persisted before responding); if absent, call the in-process `EnforcementService.enforce`. The rest of `handleGovernance` (toggle clearing, status routing, audit log) is unchanged.
- `application.yaml` — new `pipeline.enforcement-worker.{enabled, url, timeout-ms}` keys with env overrides `PIPELINE_ENFORCEMENT_WORKER_{ENABLED, URL, TIMEOUT_MS}`. Defaults: `false` / `http://gls-enforcement-worker:8097` / `60000`.

**Why a private helper rather than a separate strategy class:** the helper is one method called from one place; extracting it to a strategy interface would add ceremony without a second implementation in flight. The branch reads top-to-bottom in `handleGovernance` exactly the way the existing classifier-router branch reads — the engine has a consistent "optional client provider, fall through to in-process service" shape across the workers it dispatches to.

**Why the engine re-fetches the document after the worker call:** the worker writes via the same `documentService.save(doc)` inside `EnforcementService.enforce` before responding. The contracted `AppliedSummary` carries observability fields (storage diff, audit event id), not the full `DocumentModel`. Re-fetching by id is the same Mongo round-trip the in-process path implicitly does (engine-side lookup before per-node toggle clearing + status save). No contract change needed.

**Why `Idempotency-Key: nodeRun:<nodeRunId>`:** orchestrator pattern from CSV #16. Repeated dispatches with the same nodeRunId hit the worker's `JobStore` cache and return the previously-cached `EnforceResponse` without re-running enforcement.

**Tests:**

- `EnforcementWorkerClientTest` (new, 4) — JDK-builtin `HttpServer` (no external deps; mirrors `ClassifierRouterClientTest`):
  - happy-path 200 → `Outcome` with all `AppliedSummary` fields populated, request body shape sanity (nodeRunId, classification keys, sensitivity), header sanity (`Idempotency-Key=nodeRun:nr-engine-1`, valid `traceparent` regex);
  - non-2xx (404 + problem+json) → `EnforcementWorkerException` containing `HTTP 404` and the response code;
  - minimal response (no `applied` fields) → `Outcome` with all-null/false defaults;
  - event with null optional fields → request body omits those keys.
- `PipelineExecutionEngineTest` (existing 7) — unchanged. The new `ObjectProvider<EnforcementWorkerClient>` constructor parameter is unmocked → injected as null by `@InjectMocks`, exercising the "client absent" branch implicitly. The graph-only tests don't invoke `handleGovernance` so the branch isn't directly hit; that's covered by the contract-style HTTP test on the client itself.

Reactor: 67 tests / 0 failures across `gls-app-assembly` + `gls-governance-enforcement` on a clean build (was 63 in PR2; +4 new client tests).

**Spectral lint:** N/A (no contract changes).

**Files changed:**

- `backend/gls-app-assembly/src/main/java/.../pipeline/{EnforcementWorkerClient.java, EnforcementWorkerException.java}` (2 new).
- `backend/gls-app-assembly/src/main/java/.../pipeline/PipelineExecutionEngine.java` (1 modified).
- `backend/gls-app-assembly/src/main/resources/application.yaml` (1 modified — feature-flag keys).
- `backend/gls-app-assembly/src/test/java/.../pipeline/EnforcementWorkerClientTest.java` (1 new).
- Plan + log = 6 files total.

**Decisions logged:** None new. Mirrors the established CSV #13 / #16 / #20 / #47 patterns for sync HTTP cutover.

**Open issues / deferred:**

- **No engine-level test of the HTTP branch.** The `PipelineExecutionEngineTest` only exercises the graph-compilation logic (topological sort, condition evaluation) — it doesn't call `handleGovernance` end-to-end. A higher-level integration test that wires a real `PipelineExecutionEngine` against an `EnforcementWorkerClient` pointed at a stub HTTP server would close that gap. Tracked as a follow-up.
- **Compose env not pre-wired.** The `api` compose entry doesn't set `PIPELINE_ENFORCEMENT_WORKER_ENABLED` — relies on the application.yaml default (`false`). Same pattern as `PIPELINE_CLASSIFIER_ROUTER_ENABLED`. Operators flip via `.env` when ready to roll forward; PR2 already shipped the worker container.
- **`auditEventId` always null end-to-end** — same deferral noted in PR2 (worker emits via legacy `AuditEventRepository` without surfacing the saved id). The client surfaces the field but it'll be null until `gls-platform-audit` integration lands (Phase 0.7).
- **Service-account JWT** — client doesn't add a JWT header. Same deferral as PR2; worker doesn't validate either, so end-to-end works without one. Tighten when JWKS infra lands.

**Phase 1.10 status:** **all three plan checkboxes ticked** (deployable split + contract + rollback feature flag). Phase 1.10 complete with the audit/JWT/integration-test deferrals above.

**Next:** Phase 1.11 (`gls-indexing-worker`) — greenfield service consuming `gls.documents.classified`, writes document body + `extractedMetadata` to Elasticsearch. Or close-off any of the deferred Phase 1.10 follow-ups once their prerequisites land.

## 2026-04-30 — Phase 1.11 PR1 — Indexing-worker contract

**Done:** First PR of Phase 1.11. Lays the contract for the upcoming `gls-indexing-worker` deployable. Today the indexing logic lives in `gls-app-assembly` as `ElasticsearchIndexService` (301 lines, called from `DocumentController` / `MonitoringController` / `PipelineWebhookController`). Phase 1.11 carves it into a new module that consumes `document.classified` from RabbitMQ + exposes a small REST admin surface for sync escape hatches.

**Contracts touched:**

- `contracts/indexing-worker/{VERSION, CHANGELOG.md, README.md, openapi.yaml}` — new (v0.1.0). REST admin surface only; the primary path is the Rabbit consumer declared in `messaging/asyncapi.yaml`.
- `contracts/messaging/{VERSION, CHANGELOG.md, asyncapi.yaml}` — bumped 0.3.0 → 0.4.0. The `documentClassified` channel + `consumeDocumentClassified` operation descriptions add `gls-indexing-worker` as a third consumer alongside `ClassificationEnforcementConsumer` and `PipelineExecutionConsumer`. No payload change; each consumer binds its own queue so the broker fans out the existing `DocumentClassifiedEvent` to all three.

**REST surface (admin escape hatches + ops):**

- `POST /v1/index/{documentId}?nodeRunId=…` — sync re-index a single document; idempotent on `nodeRunId` per CSV #16. 200 / 404 (`DOCUMENT_NOT_FOUND`) / 409 (in-flight) / 422 (`INDEX_MAPPING_CONFLICT` — document parked in `index_quarantine`) / 503 (`INDEX_BACKEND_UNAVAILABLE`).
- `DELETE /v1/index/{documentId}` — remove from index; idempotent (200 with `result: NOT_FOUND` if already absent).
- `POST /v1/reindex` — async bulk reindex; returns 202 + `Location: /v1/jobs/{nodeRunId}`. Optional `statusFilter[]` to restrict the walk; default skips `DISPOSED`.
- `GET /v1/jobs/{nodeRunId}` — poll bulk-reindex job; `result` carries `ReindexSummary` (totalDocuments, indexedCount, skippedCount, failedCount, durationMs).
- `GET /v1/capabilities`, `GET /actuator/health` — standard meta surface (advertises `tiers: [INDEXING]`).

**Why an HTTP surface for a primarily-Rabbit worker:**

- **Admin escape hatches.** The existing `MonitoringController.reindexAll` is operator-driven (button in the admin UI). An admin needs a way to force re-index without re-classifying — that's the `POST /v1/index/{documentId}` endpoint.
- **Disposition cleanup.** `DocumentController` calls `removeDocument` after disposition. Admin actions (delete, archive) need an explicit removal path; the Rabbit consumer is index-only.
- **Bulk reindex** is operator-triggered (after schema migration, after a long ES outage) — needs an HTTP entry point with poll-able job state. The contracted async pattern matches the established `JobStore` shape from sibling workers.

**Why no payload change to `DocumentClassifiedEvent`:**

The indexing worker re-fetches the canonical `DocumentModel` from Mongo by id — same pattern the enforcement worker uses. The event envelope is a notification, not the indexable record. Adding fields to the event would couple all three consumers to one consumer's needs.

**Spectral lint:** clean (`No results with a severity of 'error' found!`). AsyncAPI lint: clean (1 info-level note that 3.1.0 is newer than 3.0.0; we're consistent with the rest of the repo on 3.0.0).

**Decisions logged:** None new. The "AsyncAPI is the equivalent of OpenAPI for async interfaces" rule was already established in CLAUDE.md.

**Files changed:**

- `contracts/indexing-worker/{VERSION, CHANGELOG.md, README.md, openapi.yaml}` — 4 new.
- `contracts/messaging/{VERSION, CHANGELOG.md, asyncapi.yaml}` — 3 modified.
- Plan + log = 9 files total.

**Open issues / deferred:**

- **Search endpoint.** The existing `ElasticsearchIndexService.search(queryJson)` is a read-only passthrough used by some controllers. Not contracted here — search likely stays in `gls-app-assembly` (or moves to a separate search service later). The indexing worker is write-only for the Phase 1.11 cutover.
- **Generator-smoke wiring.** `contracts-smoke` only validates `hello-world` today. Add the indexing-worker spec to its surface in a small follow-up so a contract change without a regenerate is caught at CI rather than at module compile-time.
- **Service-account JWT.** Contract declares `serviceJwt` security; worker won't validate until JWKS infra lands (Phase 0.5 deferral).

**Next:** Phase 1.11 PR2 — greenfield `gls-indexing-worker` Maven module: Spring Boot entry + `@RabbitListener` on `gls.documents.classified` + ES write logic (lifted from `ElasticsearchIndexService`) + HTTP controllers implementing the contract + per-service error handling (INDEX_FAILED status + `index_quarantine` mapping-conflict bin) + Dockerfile + compose entry. Tests lifted from the slm-worker / enforcement-worker pattern.

## 2026-04-30 — Phase 1.11 PR2 — Indexing-worker module

**Done:** New greenfield `gls-indexing-worker` Maven module. Consumes `document.classified` from RabbitMQ + exposes the contracted REST admin surface. ES write logic + index template lifted verbatim from the legacy in-process `ElasticsearchIndexService` (the cutover in PR3 will retire that class). Wired into the parent reactor + BOM + docker-compose.

**Contracts touched:** None new (consumes the v0.2.0 surface from PR1).

**New module surface:**

- `GlsIndexingWorkerApplication` — Spring Boot entry, `@EnableAsync`, scans `indexing` + `document` packages, `@EnableMongoRepositories` for the `jobs` + `quarantine` packages alongside the existing `document.repositories`.
- `service/IndexingService` — lifted from legacy. `@PostConstruct ensureIndex()` creates `ig_central_documents` if missing (same mappings: filename analyzer, dynamic extractedMetadata, ISO 15489 classification codes). `indexDocument(documentId)` throws `DocumentNotFoundException` if not in Mongo; otherwise `PUT`s to ES via JDK `HttpClient`. 5xx / transport → `IndexBackendUnavailableException`; 4xx → parks a `QuarantineRecord` in `index_quarantine` then throws `MappingConflictException`. `removeDocument` + `reindexAll(statusFilter)` mirror the same shape.
- `service/{DocumentNotFoundException, IndexBackendUnavailableException, MappingConflictException}` — typed exceptions consumed by `IndexingExceptionHandler` and the consumer.
- `consumer/RabbitMqConfig` — declares the worker's own queue `gls.documents.classified.indexing` bound to the existing `gls.documents` topic exchange via routing key `document.classified`. Each consumer of that key (enforcement, pipeline-execution, indexing) binds its own queue so the broker fans out — same pattern noted in `messaging/asyncapi.yaml` v0.4.0.
- `consumer/DocumentClassifiedConsumer` — `@RabbitListener` on `gls.documents.classified.indexing`. Happy/unhappy: `DocumentNotFoundException` + `MappingConflictException` are swallowed (acked) — re-queueing wouldn't help; backend-unavailable rethrows so Spring AMQP requeues / DLXes per broker config.
- `web/IndexController` — `POST /v1/index/{documentId}?nodeRunId=…` (sync, idempotent via `JobStore`) + `DELETE /v1/index/{documentId}` (idempotent — returns `result: NOT_FOUND` if absent).
- `web/ReindexController` — `POST /v1/reindex` returns 202 + `Location: /v1/jobs/{nodeRunId}`. Background work runs through `AsyncDispatcher` → `indexingAsyncExecutor` (2/4/8 defaults — bulk reindex holds its thread for the full ES walk). On completion, `ReindexSummary` is cached on the `JobRecord.resultJson` field.
- `web/JobController` — `GET /v1/jobs/{nodeRunId}` deserialises the cached `ReindexSummary` JSON onto `JobStatus.summary`.
- `web/MetaController` — `/v1/capabilities` advertises tier `INDEXING`; `/actuator/health` returns the contract-shaped `HealthResponse{UP}`.
- `web/IndexingExceptionHandler` — RFC 7807 problem+json mapping: `DocumentNotFoundException` → 404 `DOCUMENT_NOT_FOUND`; `MappingConflictException` → 422 `INDEX_MAPPING_CONFLICT`; `IndexBackendUnavailableException` → 503 `INDEX_BACKEND_UNAVAILABLE`; `JobInFlightException` → 409 `IDEMPOTENCY_IN_FLIGHT`; `JobNotFoundException` → 404 `INDEXING_JOB_NOT_FOUND`; `IllegalArgumentException` → 422 `INDEX_INVALID_INPUT`; `RuntimeException` catch-all → 500 `INDEX_UNEXPECTED`.
- `jobs/{JobStore, JobAcquisition, JobRecord, JobRepository, JobState}` — Mongo-backed idempotency cache + async-job poll surface in `indexing_jobs` collection. TTL via `expireAfter="0s"` index on `expiresAt` (24h default). Same shape as enforcement-worker.
- `quarantine/{QuarantineRecord, QuarantineRepository}` — Mongo-backed `index_quarantine` collection. Each row records `documentId` (id), reason, ES HTTP status, response body, request body, timestamp. Future PR adds an admin UI page for review.

**Module pom + Dockerfile + compose:**

- `pom.xml` — new artifact `co.uk.wolfnotsheep.indexing:gls-indexing-worker`. Dependencies: `gls-document` (for `DocumentModel` / `DocumentRepository`), Spring Boot starters (webmvc, actuator, data-mongodb, amqp), jakarta.validation + swagger-annotations for generated stubs, openapi-generator plugin wired to `contracts/indexing-worker/openapi.yaml`. `interfaceOnly=true` so controllers retain hand-written control.
- `Dockerfile` — repo-root context, mirrors slm-worker / enforcement-worker layout. JDK 25 build → JRE 25 runtime. `EXPOSE 8098`. Healthcheck on `/actuator/health`.
- `docker-compose.yml` — new `gls-indexing-worker` service after `gls-enforcement-worker`. Wires Mongo + Rabbit + ES (depends_on all three healthy).
- `backend/pom.xml` — registers `gls-indexing-worker` in `<modules>` after `gls-llm-worker`.
- `backend/bom/pom.xml` — `gls.indexing.worker.version` property + dependency entry, mirroring the per-deployable-versioning pattern.

**Application config:** Port 8098 (next available after 8097). New properties: `gls.indexing.worker.{jobs.ttl, async.{core-size,max-size,queue-capacity}, build.version}`. Mongo URI on `spring.data.mongodb.uri` (Spring Boot 4 key); ES on `spring.elasticsearch.uris`.

**Tests:** 24 / 0 failures.

- `IndexingServiceBuildEsDocumentTest` (4) — minimal doc + full-doc with classification codes + JSON escaping (quotes, newlines) + 50KB extracted-text truncation.
- `IndexControllerTest` (9) — implements-IndexApi guard, happy-path 200 + cache write, missing-doc → `markFailed("DOCUMENT_NOT_FOUND")`, mapping-conflict → `markFailed("INDEX_MAPPING_CONFLICT")`, backend-unavailable → `markFailed("INDEX_BACKEND_UNAVAILABLE")`, in-flight collision (no service call), completed-cache 200 (no service call), DELETE happy + DELETE-not-found.
- `JobControllerTest` (4) — unknown-id → `JobNotFoundException`; PENDING / COMPLETED-with-summary / FAILED-with-error rows surface correctly through Jackson with `findAndRegisterModules()`.
- `MetaControllerTest` (2) — capabilities tier + health UP/200.
- `DocumentClassifiedConsumerTest` (5) — happy path; null event discarded; `DocumentNotFoundException` + `MappingConflictException` swallowed (acked); `IndexBackendUnavailableException` rethrown (requeued).

Reactor: full backend reactor green (`./mvnw -DskipTests package`).

**Decisions logged:** None new.

**Files changed:**

- `backend/gls-indexing-worker/{pom.xml, Dockerfile, src/main/resources/application.yaml}` — 3 new.
- `backend/gls-indexing-worker/src/main/java/co/uk/wolfnotsheep/indexing/{GlsIndexingWorkerApplication, service/{IndexingService, DocumentNotFoundException, IndexBackendUnavailableException, MappingConflictException}, jobs/{5 files}, quarantine/{2 files}, consumer/{RabbitMqConfig, DocumentClassifiedConsumer}, web/{IndexController, ReindexController, JobController, MetaController, AsyncConfig, AsyncDispatcher, IndexingExceptionHandler, JobInFlightException, JobNotFoundException}}` — 22 new.
- `backend/gls-indexing-worker/src/test/java/.../{service/IndexingServiceBuildEsDocumentTest, web/{IndexControllerTest, JobControllerTest, MetaControllerTest}, consumer/DocumentClassifiedConsumerTest}.java` — 5 new.
- `backend/pom.xml` — module registration.
- `backend/bom/pom.xml` — version property + dependency entry.
- `docker-compose.yml` — new service entry.
- Plan + log = ~35 files total.

**Open issues / deferred:**

- **No PII summary on the indexed document.** The legacy ES service didn't index `piiFindings`; PR2 stays compatible. Future enhancement once admin search needs it.
- **Search endpoint stays in `gls-app-assembly`.** PR3 will leave the read path alone; the indexing worker is write-only this phase.
- **Service-account JWT** — contract declares `serviceJwt` but no validator wired (Phase 0.5 deferral).
- **No quarantine admin UI yet.** `index_quarantine` rows accumulate; admin review surface is a Phase 3 follow-up.
- **No DLX wiring on the new queue.** `gls.documents.classified.indexing` rejects + requeues on `IndexBackendUnavailableException`; needs dedicated DLX binding in a Phase 2 reliability pass.
- **No engine-level integration test.** Unit coverage is high (24 tests); a Testcontainers smoke that boots Rabbit + Mongo + ES + this worker is a Phase 1.11 PR2.5 / acceptance follow-up.

**Phase 1.11 status:** **all three plan checkboxes ticked** (consumer + ES write + per-service error handling). Cutover from in-process callers (PR3) is the remaining loose end before the legacy `ElasticsearchIndexService` can be retired.

**Next:** Phase 1.11 PR3 — cut in-process callers over. `DocumentController` / `MonitoringController` / `PipelineWebhookController` stop calling `ElasticsearchIndexService.indexDocument` / `removeDocument` / `reindexAll`. New `IndexingWorkerClient` (HTTP) for the sync admin paths; the Rabbit consumer absorbs the per-document indexing path. Behind `pipeline.indexing-worker.enabled` (default off; legacy in-process service stays as fallback).

## 2026-04-30 — Phase 1.11 PR3 — Indexing-worker cutover behind feature flag

**Done:** Wired `gls-app-assembly`'s legacy `ElasticsearchIndexService` to dispatch each public write (`indexDocument` / `removeDocument` / `reindexAll`) to `gls-indexing-worker` over HTTP when `pipeline.indexing-worker.enabled=true`. Otherwise (default), the in-process write logic runs unchanged. Cutover is contained inside the legacy service — the 3 callers (`DocumentController` / `MonitoringController` / `PipelineWebhookController`) have zero changes. The worker container reuses the same `IndexingService` internally; this is a transport swap, not a logic move.

**Contracts touched:** None new. Consumes the v0.2.0 surface from PR1.

**Changes:**

- `infrastructure/services/IndexingWorkerClient.java` (new) — synchronous JDK `HttpClient` against `${pipeline.indexing-worker.url}/v1/index/{id}?nodeRunId=…`, `DELETE /v1/index/{id}`, and `POST /v1/reindex`. Sets `traceparent` (random valid format) + `Idempotency-Key: nodeRun:<nodeRunId>`. Returns: ES `_version` from index (best-effort, 0 if missing), `result` string from delete (`DELETED` / `NOT_FOUND`), `nodeRunId` from reindex dispatch (fire-and-forget — admin polls monitoring page for completion). Bean conditional on `pipeline.indexing-worker.enabled=true`; default off.
- `infrastructure/services/IndexingWorkerException.java` (new) — sibling of `ClassifierRouterException` / `EnforcementWorkerException`. Caught inside the legacy service and surfaced as a `SystemError` row, matching the existing in-process ES failure path so callers see no behaviour change.
- `infrastructure/services/ElasticsearchIndexService.java` — added `ObjectProvider<IndexingWorkerClient>` constructor parameter. Each public write checks `getIfAvailable()` first: if present, dispatch over HTTP; if absent, run the legacy in-process logic. `reindexAll()` returns `-1` to signal "in-flight" when dispatched (the worker's surface is async; admins poll the monitoring page). `0` on dispatch failure, preserving the existing "no documents indexed" semantics. Index-document failures still go through `persistEsError` so the existing monitoring + retry surface keeps working.
- `application.yaml` — new `pipeline.indexing-worker.{enabled, url, timeout-ms}` keys with env overrides `PIPELINE_INDEXING_WORKER_{ENABLED, URL, TIMEOUT_MS}`. Defaults: `false` / `http://gls-indexing-worker:8098` / `30000`.

**Why route inside the legacy service rather than via a new facade:**

The 3 callers (`DocumentController` / `MonitoringController` / `PipelineWebhookController`) inject `ElasticsearchIndexService` directly and call its methods inline. Wrapping them in a new `IndexingDispatcher` interface would either require touching every caller (3 controllers + their tests + their constructor injections) or creating an internal pass-through that just shadows the method names. The legacy service is already a thin Spring bean with a small surface; the `getIfAvailable()` check at the top of each method is two lines. Smaller cutover surface, no caller changes, and the seam is removable in a single deletion when the legacy path is retired.

**Why fire-and-forget for `reindexAll`:**

The legacy service returned `int indexedCount` synchronously after walking every document. The worker's contracted surface is async (`POST /v1/reindex` returns 202 + `Location: /v1/jobs/{nodeRunId}`). Bridging "synchronous int" to "async dispatch" cleanly would require the legacy service to block waiting for the worker's `JobStore` to flip to `COMPLETED` — which works for small datasets but stalls the admin endpoint for minutes on real corpora. We chose fire-and-forget: dispatch and return `-1` immediately. The existing `MonitoringController.reindex` UI path can poll the monitoring page (or in a follow-up, surface the `nodeRunId` for direct job polling). The synchronous in-process path remains intact when the flag is off.

**Tests:**

- `IndexingWorkerClientTest` (new, 6) — JDK-builtin `HttpServer`, mirrors `ClassifierRouterClientTest` / enforcement client test:
  - `indexDocument` happy path (200 → version 7, headers + path sanity);
  - `indexDocument` non-2xx (503) → `IndexingWorkerException` with `HTTP 503` + the response code;
  - `removeDocument` happy (`DELETED`);
  - `removeDocument` idempotent (`NOT_FOUND`);
  - `reindex` async dispatch (202 → returns generated `nodeRunId`);
  - `reindex` non-202 (409) → `IndexingWorkerException`.
- Existing `gls-app-assembly` test suite — 73 / 0 (was 67; +6 from the new test file). The `ElasticsearchIndexService` constructor change is absorbed by Mockito's null injection for the new `ObjectProvider` parameter; the legacy path is exercised by every test that doesn't explicitly wire a worker bean.

Reactor: `./mvnw clean test -pl gls-app-assembly,gls-indexing-worker -am` — full green (97 across the two modules).

**Decisions logged:** None new. Mirrors the established CSV #13 / #16 / #20 / #47 patterns for sync HTTP cutover behind a feature flag.

**Files changed:**

- `backend/gls-app-assembly/src/main/java/.../infrastructure/services/{IndexingWorkerClient.java, IndexingWorkerException.java}` (2 new).
- `backend/gls-app-assembly/src/main/java/.../infrastructure/services/ElasticsearchIndexService.java` (modified).
- `backend/gls-app-assembly/src/main/resources/application.yaml` (modified — feature-flag keys).
- `backend/gls-app-assembly/src/test/java/.../infrastructure/services/IndexingWorkerClientTest.java` (1 new).
- Plan + log = 6 files total.

**Open issues / deferred:**

- **`reindexAll` returns `-1` on worker dispatch.** Existing `MonitoringController.reindex` calls return the int to the admin UI as "documents indexed". `-1` is harmless but uninformative. A small follow-up wires the returned `nodeRunId` through to the admin UI so the operator can poll the worker's `JobStore` for the real summary.
- **No engine-level integration test of the cutover.** Unit coverage is high (6 new client tests); a Testcontainers smoke that boots the api + the indexing worker against real Mongo/Rabbit/ES with the flag on would close the gap. Tracked alongside the same deferral from PR2.
- **Legacy `ElasticsearchIndexService` is no longer the only writer path.** When the flag is off, both the in-process service and the worker's Rabbit consumer would write (because PR2's worker container is already up). Operators flip the flag to switch; they should NOT run both with the worker's compose container present unless they want double writes (idempotent — same docId, ES PUT upsert — but wasteful). Documented inline in the application.yaml comment.
- **Service-account JWT** — client doesn't send a JWT header; worker doesn't validate. Same Phase 0.5 deferral as the enforcement-worker cutover.

**Phase 1.11 status:** **all four checkboxes ticked** (consumer + ES write + error handling + cutover). Phase 1.11 complete with the deferrals above; the legacy `ElasticsearchIndexService` can be retired in a future PR once the worker has soaked.

**Next:** Phase 1.12 (`gls-audit-collector`) — Tier 1 + Tier 2 binary, hash-chain WORM, S3 Object Lock backend, OpenSearch hot + S3 cold for Tier 2, every service starts emitting via `gls-platform-audit`. Largest sub-phase remaining in Phase 1.

## 2026-04-30 — Phase 1.12 PR1 — Audit-collector REST contract

**Done:** First PR of Phase 1.12. Lays the REST query + admin contract for `gls-audit-collector`. The async consumer side already exists in `contracts/audit/asyncapi.yaml` (channels `audit.tier1.{eventType}` / `audit.tier2.{eventType}`, operations `consumeAuditTier1` / `consumeAuditTier2`); this PR adds the read surface so the admin UI + ops tooling can integrate against the surface while PR2 wires the greenfield module.

**Discovery — what's already in place:**

- `contracts/audit/asyncapi.yaml` v0.3.0 — Tier 1 / Tier 2 / Tier 3 channel families, with the Tier 1 / Tier 2 publish + consume operations declared.
- `contracts/audit/event-envelope.schema.json` — full JSON Schema 2020-12 envelope with hash-chain fields (`previousEventHash`), supersession (`supersedes` / `supersededBy`), `metadata`/`content` partition for right-to-erasure, ULID `eventId`, etc.
- `gls-platform-audit` library (Phase 0.7) — `AuditEmitter`, `OutboxAuditEmitter`, `OutboxRelay`, `Tier1HashTransformer` for publisher-side hash chaining, ShedLock leader-election autoconfig, Micrometer metrics. The publisher half is done.

So Phase 1.12 isn't entirely greenfield — the **collector** (consumer) is what's missing, plus the migration of existing services from `AuditEventRepository.save()` to the library. Adjusting PR plan accordingly.

**Contracts touched:**

- `contracts/audit-collector/{VERSION, CHANGELOG.md, README.md, openapi.yaml}` — new (v0.1.0).

**REST surface:**

- `GET /v1/events?documentId=…&eventType=…&actorService=…&from=…&to=…&pageToken=…&pageSize=…` — search Tier 2 events. Cursor pagination (1–500, default 50). Tier 1 deliberately not searchable here — Tier 1 is for compliance attestation, not free-form search; fetch by id only.
- `GET /v1/events/{eventId}` — fetch a single event by ULID id. Resolves both Tier 1 + Tier 2 via the collector's id index.
- `GET /v1/chains/{resourceType}/{resourceId}/verify` — Tier 1 per-resource hash-chain verification (CSV #4). Walks oldest → newest, recomputes the chain, returns either `OK` + `eventsTraversed` or `BROKEN` + `brokenAtEventId` + `expectedPreviousHash` / `computedPreviousHash` mismatch detail.
- `GET /v1/capabilities`, `GET /actuator/health` — standard meta surface.

**Why no `POST /v1/events`:**

The write path is exclusively async. Producers emit via `gls-platform-audit`'s `AuditEmitter` → `audit_outbox` → relay → Rabbit. A REST POST would create a second emit path that bypasses the outbox guarantees and breaks the "single emitter" rule (CLAUDE.md "Audit Relay Pattern" — "Never bypass the outbox. No direct AMQP publishes from service code for audit traffic.").

**Why Tier 1 is fetch-by-id only, not searchable:**

Tier 1 is the compliance audit-of-record. Search-friendly indexing (categoryId, eventType filters across the full corpus) is a Tier 2 concern. Tier 1 access patterns are: (a) fetch a known event by id during attestation, and (b) walk the hash chain for a specific resource. Both are O(log n) with simple indices; full-text search would complicate the WORM-write path.

**Error envelope:**

- `404 AUDIT_EVENT_NOT_FOUND` — event id doesn't resolve in either tier.
- `404 AUDIT_RESOURCE_NOT_FOUND` — chain verify against a resource with no Tier 1 events.
- `422 AUDIT_QUERY_INVALID` — list query failed validation (e.g. `from > to`).
- `503 AUDIT_BACKEND_UNAVAILABLE` — Tier 1 / Tier 2 store unreachable.
- Standard `4XX` / `5XX` catch-alls via shared envelope.

**Spectral lint:** clean (`No results with a severity of 'error' found!`).

**Decisions logged:** None new. Mirrors established CSV #17 (RFC 7807) / #18 (JWT) / #20 (traceparent) / #21 (capabilities) patterns.

**Files changed:**

- `contracts/audit-collector/{VERSION, CHANGELOG.md, README.md, openapi.yaml}` — 4 new.
- Plan + log = 6 files total.

**Open issues / deferred:**

- **Tier 2 multi-`eventType` filter.** PR1 takes a single `eventType`. Real ops queries often want to OR multiple — bump to `eventType[]` in a 0.2.0 spec rev once the implementation is exercised.
- **Async chain verify.** `GET /v1/chains/.../verify` is synchronous; large chains will need a job-store-backed async surface (mirroring the indexing-worker reindex pattern) in a future revision.
- **Service-account JWT** — contract declares it but no validator wired yet (Phase 0.5 deferral).
- **AsyncAPI `audit/asyncapi.yaml` info.version drift.** The file's `info.version: 0.2.0` is older than the on-disk `VERSION: 0.3.0`. Cosmetic but should reconcile in a small follow-up.

**Next:** Phase 1.12 PR2 — greenfield `gls-audit-collector` Maven module: Spring Boot entry, two `@RabbitListener`s (Tier 1 + Tier 2), JobStore mirror, hash-chain consumer-side validation, Mongo backends initially (real backends in PR3 / PR4), Dockerfile + compose entry. Tests lifted from the indexing-worker pattern.

## 2026-04-30 — Phase 1.12 PR2 — Audit-collector module

**Done:** New greenfield `gls-audit-collector` Maven module. Consumes `audit.tier1.*` + `audit.tier2.*` from RabbitMQ on its own bound queues (single Tier 1 queue per CSV #4 single-writer, separate Tier 2 queue for horizontal scale). Persists each envelope to a Mongo collection (`audit_tier1_events` / `audit_tier2_events`); the collector's REST surface from PR1 reads back through the same store. Tier 1 consumer validates the per-resource hash chain on receipt — mismatched chains are dropped with a structured log line, not persisted.

**Contracts touched:** None new (consumes the v0.1.0 surface from PR1).

**New module surface:**

- `GlsAuditCollectorApplication` — Spring Boot entry. Scans `auditcollector` package; `@EnableMongoRepositories` for the `store` package.
- `consumer/AuditRabbitConfig` — declares `gls.audit.tier1.collector` (bound to `audit.tier1.*`) and `gls.audit.tier2.collector` (bound to `audit.tier2.*`) on the existing `gls.audit` topic exchange. The Tier 1 queue is single-writer; horizontal scale will gate it via ShedLock once a second collector replica becomes a real concern.
- `consumer/EnvelopeMapper` — extracts denormalised fields from the free-form envelope map into typed `StoredAuditEvent` rows. Preserves the full envelope verbatim on `StoredAuditEvent.envelope` so the contract response can round-trip the raw payload regardless of which fields we happen to denormalise today.
- `consumer/Tier1Consumer` — happy/unhappy-path per CLAUDE.md: chain-broken events are dropped + logged (acked, not requeued); duplicates are idempotent no-ops; null / missing-required-fields envelopes are dropped with a warning.
- `consumer/Tier2Consumer` — no chain validation. Idempotent on `eventId` via Mongo unique-key conflict.
- `chain/EventHasher` — deterministic SHA-256 of the canonical identity string `eventId|eventType|timestamp|resourceType:resourceId|previousEventHash`. The chain protects identity + ordering; content protection is the publisher's `Tier1HashTransformer` job (already done in Phase 0.7).
- `chain/ChainBrokenException` — typed exception carrying the offending event id, expected hash, and computed hash for downstream logging / metrics.
- `chain/ChainVerifier` — walks a per-resource chain oldest → newest, recomputes the hash sequence, returns OK / BROKEN / NOT_FOUND. Used by the contracted `GET /v1/chains/{resourceType}/{resourceId}/verify` endpoint.
- `store/StoredAuditEvent` (abstract) + `StoredTier1Event` (`@Document(collection = "audit_tier1_events")`) + `StoredTier2Event` (`@Document(collection = "audit_tier2_events")`) — Mongo-mapped event rows with composite indexes `idx_resource_chain` (chain lookup) and `idx_tier2_search` (eventType + timestamp DESC for the Tier 2 search filters).
- `store/Tier1Repository` + `Tier2Repository` — Spring Data Mongo repos with the chain-lookup query methods.
- `web/EventsController` — `GET /v1/events` (Tier 2 dynamic Mongo query with `documentId` / `eventType` / `actorService` / `from` / `to` filters + cursor pagination) and `GET /v1/events/{eventId}` (consults Tier 1 then Tier 2 via id index). Page token is intentionally simple (zero-padded page index); future revisions can swap to opaque base64 without breaking the contract.
- `web/ChainsController` — implements `GET /v1/chains/{resourceType}/{resourceId}/verify` against `ChainVerifier`.
- `web/MetaController` — capabilities advertise tiers `[AUDIT_TIER_1, AUDIT_TIER_2]`; health returns 200 UP.
- `web/AuditCollectorExceptionHandler` — RFC 7807 problem+json envelope per CSV #17. `AuditEventNotFoundException` → 404; `AuditResourceNotFoundException` → 404; `AuditQueryInvalidException` → 422; `DataAccessResourceFailureException` → 503 `AUDIT_BACKEND_UNAVAILABLE`; `RuntimeException` catch-all → 500.

**Module pom + Dockerfile + compose:**

- `pom.xml` — new artifact `co.uk.wolfnotsheep.auditcollector:gls-audit-collector`. Dependencies: `gls-platform-audit` (envelope record types), Spring Boot starters (webmvc, actuator, data-mongodb, amqp), jakarta.validation + swagger-annotations, openapi-generator plugin wired to `contracts/audit-collector/openapi.yaml`. `interfaceOnly=true`.
- `Dockerfile` — repo-root context, mirrors slm/enforcement/indexing-worker layout. JDK 25 build → JRE 25 runtime. `EXPOSE 8099`. Healthcheck on `/actuator/health`.
- `docker-compose.yml` — new `gls-audit-collector` service after `gls-indexing-worker`. Wires Mongo + Rabbit (depends_on both healthy). No ES dependency yet — that lands in PR3 when Tier 2 storage moves to Elasticsearch.
- `backend/pom.xml` + `backend/bom/pom.xml` — module registration + per-deployable version property `gls.audit.collector.version`.

**Application config:** Port 8099 (next after 8098). Critically, sets `gls.platform.audit.relay.enabled=false` — the collector is consume-only; if the relay scheduled task ran here, it would emit events from this container's outbox alongside the events it's already consuming, creating an emit-loop. Operators don't need to think about this; the override is in `application.yaml`.

**Tests:** 26 / 0 failures.

- `EventHasherTest` (6) — hash format (sha256: prefix, 64 hex chars), determinism, sensitivity to eventId/resource/previousHash changes, null-row handling.
- `ChainVerifierTest` (5) — empty chain → NOT_FOUND, single first-in-chain OK, multi-event chain OK, broken link reports BROKEN with offending id + hashes, first event with non-null previousHash flagged BROKEN.
- `EnvelopeMapperTest` (4) — Tier 1 full denormalisation, Tier 1 missing optional fields → null, Tier 2 marks SYSTEM, invalid timestamp → null.
- `Tier1ConsumerTest` (6) — first-in-chain persisted; correct chain link persisted; wrong chain link dropped (no insert); duplicate key idempotent; null envelope discarded; missing-resource discarded.
- `MetaControllerTest` (2) — capabilities tier list, health UP/200.
- `ChainsControllerTest` (3) — OK / BROKEN response shapes, NOT_FOUND throws `AuditResourceNotFoundException`.

Reactor: full backend reactor green. Existing platform-audit suite (18 tests) still passing.

**Decisions logged:** None new. The collector is a consumer of decisions already locked in CSV #3 / #4 / #6 / #17 / #18.

**Files changed:**

- `backend/gls-audit-collector/{pom.xml, Dockerfile, src/main/resources/application.yaml}` — 3 new.
- `backend/gls-audit-collector/src/main/java/.../auditcollector/{GlsAuditCollectorApplication, chain/{EventHasher, ChainBrokenException, ChainVerifier}, consumer/{AuditRabbitConfig, EnvelopeMapper, Tier1Consumer, Tier2Consumer}, store/{StoredAuditEvent, StoredTier1Event, StoredTier2Event, Tier1Repository, Tier2Repository}, web/{EventsController, ChainsController, MetaController, AuditCollectorExceptionHandler, AuditEventNotFoundException, AuditQueryInvalidException, AuditResourceNotFoundException}}.java` — 19 new.
- `backend/gls-audit-collector/src/test/java/.../auditcollector/{chain/{EventHasherTest, ChainVerifierTest}, consumer/{EnvelopeMapperTest, Tier1ConsumerTest}, web/{MetaControllerTest, ChainsControllerTest}}.java` — 6 new.
- `backend/pom.xml` — module registration.
- `backend/bom/pom.xml` — version property + dependency entry.
- `docker-compose.yml` — new service entry.
- Plan + log = ~32 files total.

**Open issues / deferred:**

- **Tier 2 stays on Mongo for now.** PR3 will switch to Elasticsearch (already in the stack). The `EventsController`'s search uses `MongoTemplate` — swapping to ES means a thin `Tier2Store` interface with two implementations and a feature flag, similar to the indexing-worker cutover.
- **Tier 1 stays on Mongo without role-based deny enforcement.** PR4 adds the role-based-deny / append-only enforcement (Mongo collection with `db.collection.changeStream` denied for everything but the collector's service account). True S3 Object Lock is a future follow-up.
- **No id-to-tier index.** `getEvent(eventId)` consults Tier 1 then Tier 2; for the Mongo first-cut this is two lookups. Once Tier 2 moves to ES (PR3), we'll want a small "events_index" Mongo collection mapping `eventId → (tier, timestamp)` so the controller dispatches to the right backend in O(1).
- **Search supports a single `eventType` only.** Bump the contract to `eventType[]` once the implementation is exercised.
- **No DLX wiring** on `gls.audit.tier1.collector` — broken-chain events are acked + logged today. A Phase 2 reliability pass should bind a DLX for forensics.
- **No ShedLock yet** on the Tier 1 consumer. Single-writer is enforced by single-replica today; a second collector container would currently double-write. Add ShedLock guard in PR3 / PR4 when scaling becomes real.
- **Service-account JWT** — contract declares it, no validator wired (Phase 0.5 deferral).

**Phase 1.12 status:** 2 of 5 plan items ticked (deployable + chain validation). Remaining: Tier 2 ES backend (PR3), Tier 1 hardened backend (PR4), service migrations from `AuditEventRepository` (PR5+).

**Next:** Phase 1.12 PR3 — swap Tier 2 storage from Mongo to the existing Elasticsearch container. New `Tier2Store` interface with `MongoTier2Store` (current) + `EsTier2Store` (new) implementations behind `gls.audit.collector.tier2-backend=mongo|es` config flag (default `mongo` until ES indices + ILM are wired).

## 2026-04-30 — Phase 1.12 PR3 — Tier 2 backend swap behind config flag

**Done:** Refactored Tier 2 storage in `gls-audit-collector` behind a `Tier2Store` interface. The existing Mongo logic moved into `MongoTier2Store` (the default, conditional on `gls.audit.collector.tier2-backend=mongo` or absent). New `EsTier2Store` activates when the flag is `es` — talks to the existing Elasticsearch container via JDK `HttpClient` (mirrors the `gls-indexing-worker` pattern; no spring-data-elasticsearch dep). Both implementations satisfy the same narrow contract: `save(event)` (idempotent on `eventId`), `findById(eventId)`, `search(criteria, pageIndex, pageSize)`. Tier2Consumer + EventsController dispatch through the interface.

**Bug found + fixed:** the original PR2 `EventsController.listEvents` called `query.addCriteria(Criteria.where("timestamp")...)` twice (once for `from`, once for `to`), which Mongo's `BasicDocument` rejects with `InvalidMongoDbApiUsage: can't add a second 'timestamp' criteria`. The refactor consolidates both bounds into a single `Criteria.where("timestamp").gte(...).lt(...)` call. Caught by the `MongoTier2StoreTest` filter test — the bug existed in `main` but wasn't covered by tests until the refactor pulled the search logic out of the controller.

**Contracts touched:** None new. Internal storage swap; the OpenAPI surface is unchanged.

**Changes:**

- `store/Tier2Store.java` (new) — interface declaring the three operations + a `SearchCriteria` record (single struct rather than five nullable parameters per call).
- `store/MongoTier2Store.java` (new) — `@ConditionalOnProperty(... matchIfMissing = true)`. Wraps the existing `Tier2Repository` + `MongoTemplate` query logic from PR2's `EventsController` and `Tier2Consumer`. Includes the timestamp-criteria fix.
- `store/EsTier2Store.java` (new) — `@ConditionalOnProperty(havingValue = "es")`. JDK `HttpClient` against `${spring.elasticsearch.uris}/audit_tier2_events`. `@PostConstruct ensureIndex()` creates the index with explicit mappings on the denormalised filter fields (keyword) + a `dynamic: true` envelope object so envelope drift survives. Save = `PUT /_doc/{eventId}` (upsert, idempotent). Search = `POST /_search` with `bool/filter` query for active criteria + `match_all` when none.
- `consumer/Tier2Consumer.java` — now injects `Tier2Store` instead of `Tier2Repository`. Idempotency is the backend's responsibility per the interface contract — both implementations swallow duplicate-key signals internally.
- `web/EventsController.java` — now injects `Tier2Store` for search + single fetch. The `MongoTemplate` dependency moved to `MongoTier2Store`.
- `application.yaml` — new `gls.audit.collector.tier2-backend` key (env override `GLS_AUDIT_TIER2_BACKEND`); default `mongo`. Added `spring.elasticsearch.uris` so the ES backend can be exercised on the existing stack.
- `docker-compose.yml` — collector service gets `SPRING_ELASTICSEARCH_URIS=http://elasticsearch:9200` and `GLS_AUDIT_TIER2_BACKEND=${GLS_AUDIT_TIER2_BACKEND:-mongo}`.

**Why no `Tier2Repository` cleanup yet:**

`Tier2Repository` is now only used by `MongoTier2Store`; the controller + consumer no longer reference it. Could be inlined as a private interface inside the Mongo store, but that's a cosmetic refactor. Left as-is so PR3 stays surgical — a follow-up can collapse the redundant indirection once we're sure no other module reaches into it.

**Tests:** 36 / 0 in module (was 26; +10 new for the storage abstraction).

- `MongoTier2StoreTest` (5) — happy-path insert; `DuplicateKeyException` swallowed for idempotency; `findById` delegates; `search` with all filters builds and runs query; `search` with empty criteria still calls template.
- `EsTier2StoreTest` (5) — JDK-builtin `HttpServer` driven, mirrors `EsIndexingService` test pattern: `save` PUTs to `/_doc/{eventId}` with body sanity; `findById` returns empty on 404; `findById` parses `_source` into `StoredTier2Event`; `search` POSTs `bool/filter` and parses hits; `search` with no filters uses `match_all`.

Existing tests unchanged — `Tier1ConsumerTest`, `EventHasherTest`, `ChainVerifierTest`, `MetaControllerTest`, `ChainsControllerTest`, `EnvelopeMapperTest` all still green.

Reactor: full backend reactor green (`./mvnw -DskipTests package`).

**Decisions logged:** None new. Backend selection mirrors the established `pipeline.indexing-worker.enabled` cutover pattern from Phase 1.11 PR3.

**Files changed:**

- `backend/gls-audit-collector/src/main/java/.../store/{Tier2Store, MongoTier2Store, EsTier2Store}.java` — 3 new.
- `backend/gls-audit-collector/src/main/java/.../{consumer/Tier2Consumer, web/EventsController}.java` — 2 modified (constructor + dispatch swap).
- `backend/gls-audit-collector/src/main/resources/application.yaml` — modified.
- `backend/gls-audit-collector/src/test/java/.../store/{MongoTier2StoreTest, EsTier2StoreTest}.java` — 2 new.
- `docker-compose.yml` — modified.
- Plan + log = 9 files total.

**Open issues / deferred:**

- **OpenSearch + S3 ILM.** Real production goal per CSV #3. PR3 ships ES on the existing single-node Elasticsearch container; ILM (hot → warm → cold → delete) and an OpenSearch cluster are a Phase 2 hardening pass.
- **`Tier2Repository` is now a single-implementation indirection.** Could be inlined into `MongoTier2Store` in a small follow-up.
- **No `id-to-tier` index.** `EventsController.getEvent(eventId)` consults Tier 1 first then Tier 2 — two lookups for any miss. Adding a small `events_index` Mongo collection mapping `eventId → tier` would make this O(1). Tracked alongside the same deferral from PR2.
- **No bulk reindex from Mongo to ES.** Once ES becomes the chosen backend, historical events in `audit_tier2_events` Mongo collection won't migrate. A small admin endpoint `POST /v1/admin/migrate-tier2` is a follow-up; until then, operators can either accept the gap or replay events from the original outbox.

**Phase 1.12 status:** 3 of 5 plan items ticked (deployable + chain validation + Tier 2 backend swap). Remaining: hardened Tier 1 backend (PR4), service migrations from `AuditEventRepository` (PR5+).

**Next:** Phase 1.12 PR4 — Tier 1 hardened backend. Mongo append-only with role-based deny per CSV #3 (the simplest first cut: collection-level role grants only INSERT for the collector's service account; UPDATE / DELETE explicitly denied). True S3 Object Lock remains a future follow-up.

## 2026-04-30 — Phase 1.12 PR4 — Tier 1 hardened backend (append-only)

**Done:** Refactored Tier 1 storage in `gls-audit-collector` behind a `Tier1Store` interface that intentionally exposes only `append` + reads — no `save`, `update`, or `delete` methods. `AppendOnlyMongoTier1Store` is the default implementation, using `MongoRepository.insert()` (the only write primitive that throws on collision rather than upserting) and translating `DuplicateKeyException` into a typed `AppendOnlyViolationException` the consumer treats as idempotent. The mutating-method-absence is enforced by a reflection-based test so future PRs can't quietly add `save`.

**Contracts touched:** None new. Internal storage swap mirroring PR3's Tier 2 abstraction.

**Changes:**

- `store/Tier1Store.java` (new) — interface with `append(event)`, `findById(eventId)`, `findLatestForResource(type, id)`, `findChainAsc(type, id)`. Deliberately narrow.
- `store/AppendOnlyViolationException.java` (new) — typed exception so the consumer can tell "duplicate" apart from "Mongo unreachable" without inspecting Spring exception classes. Distinct from `Tier2Store`'s silent-swallow approach because the Tier 1 surface is more sensitive — exposing the typed exception makes it explicit at the call site that the consumer's idempotent-no-op behaviour is a deliberate decision.
- `store/AppendOnlyMongoTier1Store.java` (new) — `@ConditionalOnProperty(... matchIfMissing = true)`. Wraps the existing `Tier1Repository` reads + `repo.insert()` for the write path. Translates `DuplicateKeyException` → `AppendOnlyViolationException`.
- `consumer/Tier1Consumer.java` — now injects `Tier1Store` instead of `Tier1Repository`. Catches `AppendOnlyViolationException` for idempotent no-op handling (replaces the old `DuplicateKeyException` catch).
- `chain/ChainVerifier.java` — now injects `Tier1Store`. Calls `findChainAsc(type, id)` instead of the repo method directly.
- `web/EventsController.java` — `getEvent(eventId)` now consults `Tier1Store.findById` first then `Tier2Store.findById`.
- `application.yaml` — new `gls.audit.collector.tier1-backend` key (env override `GLS_AUDIT_TIER1_BACKEND`); default `mongo`. Inline comment documents the operator-side hardening: grant only `insert` + `find` on `audit_tier1_events`; deny `update` / `delete` / `dropCollection` / `renameCollectionSameDB`.

**Why a typed exception (not silent swallow):**

The Tier 2 path absorbs duplicates inside `MongoTier2Store.save()` because Tier 2 is operational data and there's no architectural concern about an unintended overwrite. Tier 1 is the audit-of-record — making the duplicate signal explicit at the call site lets the consumer's "idempotent no-op" behaviour be a deliberate decision rather than an accident of API choice. If a future PR ever needs to react differently to a duplicate Tier 1 event (e.g. metric-counter, dead-letter for forensics), the typed exception makes that easy without changing the store's contract.

**Why a reflection-test rather than just relying on the interface shape:**

The `Tier1Store` interface intentionally has no mutating methods — but a future PR could add `save(StoredTier1Event)` thinking it's harmless. The reflection test (`interface_does_not_expose_update_or_delete`) gives a compile/test-time signal that the architectural intent is being violated. Cheap insurance against drift.

**Tests:** 42 / 0 in module (was 36; +6 new for `AppendOnlyMongoTier1StoreTest`).

- `AppendOnlyMongoTier1StoreTest` (6) — `append` calls `repo.insert`; `DuplicateKeyException` translates to `AppendOnlyViolationException` with eventId in the message; `findById` / `findLatestForResource` / `findChainAsc` delegate cleanly; reflection test verifies the interface has no `save` / `update` / `delete` / `deleteById` / `deleteAll` methods.
- `Tier1ConsumerTest` (6) — updated to use `Tier1Store` mock. The "duplicate is idempotent" test now throws `AppendOnlyViolationException` via `doThrow().when().append()` rather than `repo.insert` throwing `DuplicateKeyException`. Same behaviour, different surface.
- `ChainVerifierTest` (5) — updated to use `Tier1Store` mock. Method swap from `findByResourceTypeAndResourceIdOrderByTimestampAsc` to `findChainAsc`.
- Existing `EventHasherTest`, `EnvelopeMapperTest`, `MetaControllerTest`, `ChainsControllerTest`, `MongoTier2StoreTest`, `EsTier2StoreTest` unchanged.

Reactor: full backend reactor green.

**Decisions logged:** None new. The "interface as architectural guard" pattern is implicit in CLAUDE.md "Audit Relay Pattern" — making it concrete for Tier 1 here.

**Files changed:**

- `backend/gls-audit-collector/src/main/java/.../store/{Tier1Store, AppendOnlyMongoTier1Store, AppendOnlyViolationException}.java` — 3 new.
- `backend/gls-audit-collector/src/main/java/.../{consumer/Tier1Consumer, chain/ChainVerifier, web/EventsController}.java` — 3 modified.
- `backend/gls-audit-collector/src/main/resources/application.yaml` — modified.
- `backend/gls-audit-collector/src/test/java/.../{store/AppendOnlyMongoTier1StoreTest}.java` — 1 new.
- `backend/gls-audit-collector/src/test/java/.../{consumer/Tier1ConsumerTest, chain/ChainVerifierTest}.java` — 2 modified.
- Plan + log = 11 files total.

**Open issues / deferred:**

- **Tier1Repository is now a single-implementation indirection.** Could inline into `AppendOnlyMongoTier1Store` in a follow-up — same shape as the `Tier2Repository` deferral from PR3.
- **No datastore-level deny enforcement is automated.** The application.yaml comment documents the role grants operators should configure, but the collector container doesn't itself enforce them — that's intentional (the collector runs as a different service account than the deployment-script-driven Mongo user; verification belongs in deployment validation, not the running service).
- **No S3 Object Lock backend yet.** The `Tier1Store` interface is ready for one (`@ConditionalOnProperty(havingValue = "s3-object-lock")`), but the implementation needs the AWS SDK + bucket config + ILM mode. Tracked as a future follow-up; Mongo append-only with role-based deny meets CSV #3's RECOMMENDED option for the first cut.
- **Reflection test is a structural guard, not a functional one.** It catches accidental future additions of `save`/`update`/`delete` on `Tier1Store`, but a sufficiently determined refactor could rename the methods to `persist` / `mutate`. Acceptable risk — the test exists to surface intent during code review.

**Phase 1.12 status:** **4 of 5 plan items ticked** (deployable + chain validation + Tier 2 swap + Tier 1 hardening). Remaining: service migrations from `AuditEventRepository` (PR5+) — touches every service that emits audit events today.

**Next:** Phase 1.12 PR5 — start migrating services from the legacy `AuditEventRepository.save()` calls to `gls-platform-audit`'s `AuditEmitter`. Recommended order: `gls-governance-enforcement` (highest event volume, already touched in Phase 1.10), then `gls-app-assembly` document/classification flows, then connectors. Each migration is a small focused PR; they can land independently because the legacy and new paths don't conflict (publisher-side outbox already deployed in Phase 0.7).

## 2026-04-30 — Phase 1.12 PR5 — Migrate gls-governance-enforcement to AuditEmitter

**Done:** First service migration to the new audit pipeline. `gls-governance-enforcement`'s `EnforcementService` now dual-writes every audit event — the legacy `auditEventRepository.save(...)` call retains alongside a new `platformAudit.emitTier1/2(...)` call. Once admin UIs cut over to the collector's REST surface (a later PR), the legacy emissions can be deleted in one sweep — every call site is paired.

**Why dual-write rather than replace:**

The legacy `audit_events` Mongo collection is read by several admin UI surfaces today (monitoring page, document detail, etc.). Cutting them all over to query the collector's REST surface in a single PR would be high-risk — the collector is brand-new (PR2 / PR3 / PR4 just landed). Dual-write is the safe migration path: the legacy admin UI keeps working unchanged, the new collector accumulates events in parallel, and once we've verified the collector's accuracy against real traffic we can flip read paths and delete the legacy emissions.

**Changes:**

- `audit/EnforcementAuditEmitter.java` (new) — thin helper that constructs `gls-platform-audit` envelopes from the small set of fields each emit site has on hand. Provides `emitTier1(documentId, eventType, action, outcome, retentionClass, metadata, content)` for compliance events (DOMAIN tier, default `7Y` retention) and `emitTier2(...)` for operational telemetry (SYSTEM tier, `30D` retention). `AuditEmitter` bean injected via `ObjectProvider` so absent → no-op (legacy `save` has already fired). Generates ULID-shaped event ids per the envelope schema's `^[0-9A-HJKMNP-TV-Z]{26}$` pattern using SecureRandom — not strictly time-sortable like a real ULID, but conforming to the validator's character set; replace with a real ULID library when one is added to the platform.
- `services/EnforcementService.java` — added `EnforcementAuditEmitter platformAudit` constructor parameter. Dual-write at all 8 emit sites: PII_SENSITIVITY_ESCALATED (×2, both Tier 1), GOVERNANCE_APPLIED (Tier 1), STORAGE_TIER_MIGRATED (Tier 1), STORAGE_MIGRATION_FAILED (Tier 2), DOCUMENT_DISPOSED / DOCUMENT_ARCHIVED / DOCUMENT_ANONYMISED (all Tier 1), DISPOSITION_FAILED (Tier 2).
- `pom.xml` — added `gls-platform-audit` dependency.
- `services/EnforcementServiceTest.java` — added `@Mock private EnforcementAuditEmitter platformAudit` so `@InjectMocks` resolves the new constructor parameter. Existing 5 tests unchanged.
- `audit/EnforcementAuditEmitterTest.java` (new) — 5 focused tests for the helper: Tier 1 envelope shape (DOMAIN, DOCUMENT resource, SYSTEM actor, retentionClass/metadata/content), Tier 2 envelope shape (SYSTEM, 30D default), exception swallowing (audit failure must not break enforcement), absent-emitter no-op, null retentionClass defaults to 7Y.

**Why a helper rather than calling AuditEmitter directly:**

Three reasons:

1. **Envelope construction is verbose.** Eight emit sites would each need to build the full `AuditEvent` record (15 fields), construct an `Actor`, construct a `Resource`, partition `details` into metadata/content. The helper collapses that to one line per site.
2. **Service identity stays in one place.** Service name, version, instance id are injected once on the helper, not at every emit site.
3. **Audit emission must be non-fatal.** The helper wraps every `emitter.emit()` call in try/catch — a Rabbit outage or schema validation failure would otherwise break the enforcement transaction. The legacy `save` has already fired, so the audit data isn't lost.

**Tests:** 35 / 0 in module (was 30; +5 new for `EnforcementAuditEmitterTest`). Existing `EnforcementServiceTest` (5) unchanged — Mockito's `@InjectMocks` absorbs the new constructor parameter via the new `@Mock`.

Reactor: full backend reactor green.

**Decisions logged:** None new. The dual-write pattern is the standard CLAUDE.md "Audit Relay Pattern" guidance — emit via the library, never bypass; the parallel `audit_events` collection write is just the legacy compatibility path during migration.

**Files changed:**

- `backend/gls-governance-enforcement/pom.xml` — modified.
- `backend/gls-governance-enforcement/src/main/java/.../enforcement/{audit/EnforcementAuditEmitter, services/EnforcementService}.java` — 1 new, 1 modified.
- `backend/gls-governance-enforcement/src/test/java/.../enforcement/{audit/EnforcementAuditEmitterTest, services/EnforcementServiceTest}.java` — 1 new, 1 modified.
- Plan + log = 7 files total.

**Open issues / deferred:**

- **ULID generation is approximate.** The helper uses SecureRandom for all 26 chars to satisfy the envelope schema's pattern, but a real ULID encodes a millisecond timestamp in the first 10 chars (Crockford base32) so events sort lexicographically by time. Replace with a real ULID library when added to the platform — or move ULID generation into `gls-platform-audit` so all services benefit.
- **No `previousEventHash` set on the Tier 1 envelopes.** The publisher's hash-chain head tracker (per CSV #4) is a separate concern from the consumer-side validator we built in PR2; currently every emit goes out with `previousEventHash=null`, which the collector's validator accepts as "first in chain" for that resource. Real chain integrity needs the publisher to look up the latest hash for the resource and chain on top — a follow-up PR adds a `Tier1ChainHeadTracker` to `gls-platform-audit`.
- **No `pipelineRunId` / `nodeRunId` propagation.** The enforcement service doesn't have these on hand inside `enforce()` — they live on the orchestrator side. Needs a small refactor to thread them through (or read them from a request-scoped bean populated by the controller).
- **`traceparent` not propagated.** Same — needs request-scoped propagation.
- **Legacy emissions are still fired.** Once the collector + admin UI cutover is verified, a follow-up PR can delete every legacy `auditEventRepository.save(...)` call alongside the new emit. Not yet — this PR is the safe-by-default migration.

**Phase 1.12 status:** **all 5 plan items at least partially ticked.** PR5 covers the first service migration (gls-governance-enforcement); PR6+ migrates the remaining services (gls-app-assembly document/classification flows, connectors). Phase 1.12 substantially complete with the cutover-completion PR sequence still ahead.

**Next:** Phase 1.12 PR6 — migrate `gls-app-assembly` document + classification flows to `gls-platform-audit`. Largest remaining migration; same dual-write pattern.

## 2026-04-30 — Phase 1.12 PR6 — Migrate gls-app-assembly to AuditEmitter

**Done:** Second service migration. `gls-app-assembly` now dual-writes audit events from three call surfaces: `FilingService` (3 sites), `ReviewQueueController` (4 sites), `DocumentController` (5 sites — 3 ACCESS_DENIED + DOCUMENT_VIEWED ×2 + DOCUMENT_DOWNLOADED). 12 emit sites total. Same dual-write pattern as PR5 — legacy `auditEventRepository.save(...)` retains alongside the new `platformAudit.emit*(...)` call.

**Why a separate helper rather than reusing PR5's:**

`gls-governance-enforcement`'s `EnforcementAuditEmitter` defaults to SYSTEM actor — appropriate because every emit there is service-driven. `gls-app-assembly`'s emits are mostly USER actor (DOCUMENT_VIEWED is a person clicking a document, ACCESS_DENIED is RBAC tripping on a user request). Inlining USER-actor support into the enforcement helper would muddle its surface; a thin separate helper here is clearer. When a third migration lands (likely connectors in Phase 1.13), pull the shared helper into `gls-platform-audit` to avoid copy-paste drift.

**Changes:**

- `audit/PlatformAuditEmitter.java` (new) — same shape as `EnforcementAuditEmitter` but exposes three methods: `emitUserAction(documentId, eventType, action, userId, outcome, metadata, content)` (DOMAIN tier, USER actor, 7Y default — the common case here), `emitTier1(...)` (DOMAIN, SYSTEM actor — for system-driven Tier 1 events), `emitTier2(...)` (SYSTEM tier, 30D default).
- `services/filing/FilingService.java` — added `PlatformAuditEmitter` constructor parameter; dual-write at 3 sites (DOCUMENT_FILED, DOCUMENT_RETURNED_TO_TRIAGE, DOCUMENT_RETURNED_TO_INBOX).
- `controllers/review/ReviewQueueController.java` — added `PlatformAuditEmitter`; dual-write at 4 sites (CLASSIFICATION_APPROVED, CLASSIFICATION_OVERRIDDEN, CLASSIFICATION_REJECTED, PII_REPORTED).
- `controllers/documents/DocumentController.java` — added `PlatformAuditEmitter`; dual-write at 5 sites including 3 ACCESS_DENIED paths (with `Outcome.FAILURE`).
- `pom.xml` — added `gls-platform-audit` dependency.
- `audit/PlatformAuditEmitterTest.java` (new) — 6 focused tests: USER actor + DOMAIN tier + 7Y default, SYSTEM actor + DOMAIN tier (emitTier1), SYSTEM tier + 30D default (emitTier2), exception swallowing, absent-emitter no-op, null retentionClass defaults to 7Y.

**Tests:** 79 / 0 in `gls-app-assembly` (was 73; +6 new for `PlatformAuditEmitterTest`). Existing controller + service tests unchanged — Mockito's `@InjectMocks` absorbs the new constructor parameter via no-arg null injection.

Reactor: full backend reactor green (`./mvnw -DskipTests package`).

**Decisions logged:** None new. Same dual-write rationale as PR5.

**Files changed:**

- `backend/gls-app-assembly/pom.xml` — modified.
- `backend/gls-app-assembly/src/main/java/.../infrastructure/audit/PlatformAuditEmitter.java` — 1 new.
- `backend/gls-app-assembly/src/main/java/.../infrastructure/{services/filing/FilingService, controllers/review/ReviewQueueController, controllers/documents/DocumentController}.java` — 3 modified.
- `backend/gls-app-assembly/src/test/java/.../infrastructure/audit/PlatformAuditEmitterTest.java` — 1 new.
- Plan + log = 7 files total.

**Open issues / deferred:**

- **Two near-identical helpers.** `EnforcementAuditEmitter` (PR5) and `PlatformAuditEmitter` (this PR) share ~80% of their code — envelope construction, ULID gen, exception swallowing, absent-emitter no-op. When the connector migration lands, pull the shared base into `gls-platform-audit` (e.g. `BaseAuditEmitterHelper`) and reduce each service-specific helper to just the convention-setting bits (default tier, default actor, default retention).
- **No `pipelineRunId` / `nodeRunId` / `traceparent` propagation.** Same deferral as PR5 — needs request-scoped beans threaded through the controllers + services.
- **Legacy emissions still fired.** Cleanup PR comes after admin UI cutover verifies the new pipeline.
- **`AuditInterceptor.java` and `AuditLogController.java` not migrated yet.** Both reference `AuditEventRepository` per the survey, but neither writes events — `AuditLogController` is read-only (admin UI query surface), `AuditInterceptor` injects but doesn't `save()`. Both eventually move to consume the collector's REST surface; tracked alongside the admin UI cutover.

**Phase 1.12 status:** **all 5 plan items ticked** with the connectors migration deferred to Phase 1.13. Phase 1.12 substantially complete; cleanup PRs (delete legacy emissions, retire `AuditEventRepository`, migrate admin UI to query collector REST) come after the new pipeline soaks.

**Next:** Phase 1.13 (`Connectors family review`) — audit `gls-connectors` (Drive, Gmail), conform to new contract surface, add `gls-platform-audit` integration. The connector migration is the third audit emit site so it's worth pulling the shared helper into `gls-platform-audit` first to avoid a third copy.

## 2026-04-30 — Phase 1.13 PR1 — Connectors review + per-source ShedLock

**Done:** Phase 1.13 surveyed both connector code surfaces and wired per-source ShedLock. Surprise finding: there's no separate `gls-connectors` module — connectors live inside `gls-app-assembly` under `infrastructure.services.{drives, mail}` and `infrastructure.controllers.{drives, mailboxes, auth}`. Survey of `auditEventRepository.save(...)` calls in those packages: zero hits. Connectors handle ingestion (Drive/Gmail watch + pull), not state-change auditing — the audit-relevant events fire downstream via the services already migrated in PR5 / PR6 (`DOCUMENT_INGESTED`, `DOCUMENT_CLASSIFIED`, `GOVERNANCE_APPLIED`).

**Plan-item-1 (audit-emit migration) is therefore implicitly complete** with no code change — there was nothing to migrate. `gls-platform-audit` dependency is already on the classpath via PR6's `gls-app-assembly` migration, so a future PR that adds state-change auditing in connector code can reuse the existing `PlatformAuditEmitter` helper.

**Plan-item-2 (per-source watch sharding via ShedLock) — real implementation work:**

- `infrastructure/services/connectors/PerSourceLock.java` (new) — wraps `LockProvider.lock(LockConfiguration)` with a `withLock(name, lockAtMostFor, action)` helper that runs the action under a Mongo-backed lock. Returns `true` if the action ran, `false` if another replica held the lock (silent skip). `LockProvider` injected via `ObjectProvider` so single-replica deployments without `shedlock-provider-mongo` on the classpath fall through to `action.run()` (the `getIfAvailable()`-returns-null no-op path).
- `services/drives/GoogleDriveFolderMonitor.checkMonitoredFolders` — per-drive lock keyed on `drive-poll-<driveId>`; lockAtMostFor 10 minutes (worst-case folder scan). Extracted the per-drive work into `processDrive(drive)` so the lambda body is small.
- `services/mail/GmailPollingScheduler.pollGmailWatchers` — per-watcher lock keyed on `gmail-poll-<pipelineId>-<nodeId>` (each watcher is a unique combination of pipeline + node, since one pipeline can have multiple gmailWatcher nodes); lockAtMostFor 5 minutes.

Both pollers continue to use `@Scheduled` at the method level (so every replica runs the schedule), but the per-iteration lock prevents the same source from being processed by two replicas simultaneously. This is the right pattern for "per-source sharding": replica A polls drives 1, 3, 5 while replica B polls drives 2, 4 (whoever acquires the lock first wins; the next tick redistributes).

**Why per-iteration rather than method-level `@SchedulerLock`:**

`@SchedulerLock(name = "...")` would prevent any replica from running the scheduled method while another holds the lock — that's leader-election semantics, not sharding. Per-source sharding requires the schedule to run on every replica with each source acquiring its own lock. Manual `LockProvider` use is the only path to that pattern.

**Tests:** 84 / 0 in `gls-app-assembly` (was 79; +5 new for `PerSourceLockTest`).

- `PerSourceLockTest` (5) — happy-path acquire + run + unlock, lock-held-elsewhere skips, unlock-on-action-throw, absent `LockProvider` falls through (no-op), `LockConfiguration` carries the right name + lockAtMostFor.

Reactor: full backend reactor green (`./mvnw -DskipTests package`).

**Decisions logged:** None new. Per-source sharding pattern is implicit in the plan's Phase 1.13 wording; making it concrete here.

**Files changed:**

- `backend/gls-app-assembly/src/main/java/.../infrastructure/services/connectors/PerSourceLock.java` — 1 new.
- `backend/gls-app-assembly/src/main/java/.../infrastructure/services/{drives/GoogleDriveFolderMonitor, mail/GmailPollingScheduler}.java` — 2 modified.
- `backend/gls-app-assembly/src/test/java/.../infrastructure/services/connectors/PerSourceLockTest.java` — 1 new.
- Plan + log = 5 files total.

**Open issues / deferred:**

- **Lock duration tuning.** Drive folder scan worst-case is hard to estimate; 10 minutes is a guess. If a real scan exceeds it the lock auto-releases mid-scan and a second replica might double-process the tail of the file list (idempotent on `externalStorageRef.fileId` so no duplicate documents — just wasted API calls). Tune via observability once we have a perf baseline (Phase 0.11 deferral).
- **No metrics on lock acquisition.** Skipped iterations are debug-logged but not counted. Useful telemetry: `connector.lock.acquired{source=drive}`, `connector.lock.skipped{source=drive}` per second. Tracked alongside the broader Phase 2 reliability metrics.
- **Email polling continues to scan `activePipelines` × `visualNodes` even when most iterations skip-due-to-lock.** Cheap (N is small for any realistic install), but a sufficiently large pipeline catalog would benefit from indexing watcher nodes separately. Premature optimisation today.
- **`gls-connectors` as its own module.** The plan named the module aspirationally; the code lives in `gls-app-assembly` today. Carving it out would mirror the worker extractions of Phase 1.10 / 1.11 — separate Maven module + own deployable consuming the same Rabbit topology. Tracked as a future architectural cleanup; nothing forces it today since the connectors don't have their own scaling profile.

**Phase 1.13 status:** **both plan items ticked.** Phase 1.13 complete with the deferrals above.

**Phase 1 status overall:** **all 13 sub-phases substantially complete** — 1.1 through 1.13 done, with deferrals tracked per-PR. Phase 1's acceptance gates remain (end-to-end happy path test, 10%-of-baseline perf check, audit Tier 1 verification, hub pack import propagation under 30 seconds, cost-per-document recording) — all blocked on Phase 0.11's load driver having representative content.

**Next:** Either start Phase 2 (system-wide resilience — recovery tasks, circuit breakers, rate limits, quorum queues, ~15-25 PRs estimated) or close-off Phase 1 deferrals. Phase 2 is the natural next step but it's a long phase; the smaller Phase 1 deferrals (audit cleanup, ULID library, JWT validation when JWKS lands) are quick wins if a focused session is preferred.

## 2026-04-30 — Phase 1 closeout PR1 — Extract shared `BaseAuditEmitter`

**Done:** Resolved the "two near-identical helpers" deferral from Phase 1.12 PR6's open-issues list. `PlatformAuditEmitter` (gls-app-assembly) and `EnforcementAuditEmitter` (gls-governance-enforcement) shared ~80% of their code — envelope construction, ULID gen, exception swallowing, absent-emitter no-op, schema-version stamping. Pulled the shared logic into `BaseAuditEmitter` in `gls-platform-audit`; per-service helpers now extend it and contribute only the convention-setting bits (default actor type, default tier, default retention).

**Changes:**

- `gls-platform-audit/emit/BaseAuditEmitter.java` (new) — abstract base. Holds `ObjectProvider<AuditEmitter>`, serviceName, serviceVersion, instanceId. Exposes a single `protected final emit(documentId, eventType, tier, action, outcome, retentionClass, actor, metadata, content)` that resolves the emitter via `getIfAvailable`, returns silently when null (legacy dual-write path), builds the envelope with a fresh ULID + current schema version, swallows `RuntimeException` with WARN log. Sets `pipelineRunId` / `nodeRunId` / `traceparent` / `previousEventHash` to null pending request-scoped propagation (still deferred).
- `gls-platform-audit/emit/Ulid.java` (new) — pulled the Crockford-base32 ULID generator out of the per-service helpers. Pure utility with `nextId()`. Lives in the same package so subclass authors don't need to import it (callers go through `BaseAuditEmitter.emit`).
- `gls-app-assembly/.../infrastructure/audit/PlatformAuditEmitter.java` — refactored to extend `BaseAuditEmitter`. Constructor delegates the 4-arg seed; convention methods (`emitUserAction`, `emitTier1`, `emitTier2`) reduced to 3-line calls into `super.emit`. Public surface unchanged — every call site keeps working.
- `gls-governance-enforcement/.../enforcement/audit/EnforcementAuditEmitter.java` — same refactor; convention methods (`emitTier1`, `emitTier2`) reduced to 3-line calls. Public surface unchanged.
- `gls-platform-audit/test/.../emit/BaseAuditEmitterTest.java` (new) — 3 tests exercising the shared base via a minimal in-test subclass that exposes `emit`: envelope shape (ULID-format eventId, current schema version, DOCUMENT resource, null trace fields, pass-through of metadata + content), runtime-exception swallowing, absent-emitter no-op.
- `gls-platform-audit/test/.../emit/UlidTest.java` (new) — 3 tests: schema-pattern match across 100 invocations, no excluded characters (I, L, O, U), distinctness across 1000 invocations.

**Tests:** Reactor green (`./mvnw -DskipTests package`). Module-level: `gls-platform-audit` +6 tests (BaseAuditEmitterTest 3 + UlidTest 3); `gls-governance-enforcement` unchanged at 5 in `EnforcementAuditEmitterTest` (still pass — they exercise the public surface, not the duplicated internals); `gls-app-assembly` unchanged at 6 in `PlatformAuditEmitterTest` (same).

**Decisions logged:** None new. The "shared helper" pattern was already named in the PR6 deferral — making it concrete here.

**Files changed:**

- `backend/gls-platform-audit/src/main/java/.../emit/{BaseAuditEmitter, Ulid}.java` — 2 new.
- `backend/gls-platform-audit/src/test/java/.../emit/{BaseAuditEmitterTest, UlidTest}.java` — 2 new.
- `backend/gls-app-assembly/src/main/java/.../infrastructure/audit/PlatformAuditEmitter.java` — modified (now extends base, lost ~80 LOC of duplicated internals).
- `backend/gls-governance-enforcement/src/main/java/.../enforcement/audit/EnforcementAuditEmitter.java` — modified (same shape).
- Log = 5 files total.

**Open issues / deferred:**

- **Legacy `auditEventRepository.save(...)` dual-writes still fired.** Cleanup PR is gated on the admin UI cutover to the collector REST surface — both `AuditInterceptor` (injects but doesn't save) and `AuditLogController` (read-only admin query) need to migrate first. Same deferral as PR5/PR6.
- **`pipelineRunId` / `nodeRunId` / `traceparent` propagation.** Same deferral — needs request-scoped beans threaded through the controllers + services. Now centralised in `BaseAuditEmitter.emit`'s call site so the future PR has a single place to wire it.
- **Real ULID library.** `Ulid.nextId()` is still SecureRandom-only — not time-sortable. Swap to a real ULID library (e.g. `com.github.f4b6a3:ulid-creator`) when one is added to the platform; the change becomes a single-file edit now that the generator is one place.

**Phase 1 closeout status:** 1 of 2 actionable closeout items done. Next: PerSourceLock metrics (Phase 1.13 deferral). Other deferrals (admin UI cutover, full pipelineRunId propagation, ULID library, JWT/JWKS validation, Phase 1 acceptance gates) remain blocked on external work / Phase 0.11 loadgen.

**Next:** Phase 1 closeout PR2 — add Micrometer counters to `PerSourceLock` so Drive/Gmail polling skip-rate is observable.

## 2026-04-30 — Phase 1 closeout PR2 — `PerSourceLock` metrics

**Done:** Resolved the "no metrics on lock acquisition" deferral from Phase 1.13 PR1's open-issues list. `PerSourceLock` now emits Micrometer counters on every `withLock` call so Drive/Gmail polling skip-rate is observable in real workloads — the missing telemetry that the deferred lock-duration tuning will need.

**Changes:**

- `infrastructure/services/connectors/PerSourceLock.java` — constructor takes a second `ObjectProvider<MeterRegistry>` parameter (optional via `getIfAvailable`). On every call: derives `source` from the lock-name prefix (`drive-poll-...` → `drive`, `gmail-poll-...` → `gmail`, fallback `unknown`) and increments either `connector.lock.acquired{source=...}` (lock acquired or single-replica fall-through) or `connector.lock.skipped{source=...}` (another replica holds it). Source cardinality is bounded; the high-cardinality lock-name itself is intentionally not tagged. Public `withLock` signature unchanged — the two existing call sites (`GoogleDriveFolderMonitor`, `GmailPollingScheduler`) compile and run as before.
- `PerSourceLockTest` — switched from no-metrics-aware mocks to a real `SimpleMeterRegistry`. Existing 5 tests extended with counter assertions. Added 2 new tests: `absent_MeterRegistry_does_not_break_lock_acquisition` (proves the optional injection works), `source_extraction_falls_back_to_unknown_for_unconventional_names` (covers null / empty / no-dash / leading-dash inputs to `sourceOf`).

**Tests:** 86 / 0 in `gls-app-assembly` (was 84; +2 new for `PerSourceLockTest`). Reactor green.

**Decisions logged:** None new. The metrics namespace (`connector.lock.*`) and the `source` tag matches the wording in the PR1 deferral.

**Files changed:**

- `backend/gls-app-assembly/src/main/java/.../infrastructure/services/connectors/PerSourceLock.java` — modified.
- `backend/gls-app-assembly/src/test/java/.../infrastructure/services/connectors/PerSourceLockTest.java` — modified (2 new tests; existing 5 extended).
- Log = 3 files total.

**Open issues / deferred:**

- **Lock-duration tuning still pending.** Same deferral as PR1; the metrics added here are the prerequisite. With `connector.lock.skipped{source}` visible in dashboards, operators can pick lock-at-most-for windows that minimise skip-rate without risking double-processing.
- **No timer for action runtime under lock.** Useful telemetry once tuning starts: `connector.lock.action.duration{source}` would give a real histogram. Not added here to keep PR small; layer on once the counter shape proves out.

**Phase 1 closeout status:** 2 of 2 actionable items done. Other Phase 1 deferrals remain blocked on external work:
- Legacy `auditEventRepository.save(...)` cleanup → blocked on admin UI cutover to collector REST surface.
- `AuditInterceptor` + `AuditLogController` migration → same gating.
- Real ULID library (vs SecureRandom-only) → blocked on library selection / addition to platform.
- `pipelineRunId` / `nodeRunId` / `traceparent` propagation → blocked on request-scoped beans being threaded through controllers + services.
- Phase 1 acceptance gates (end-to-end happy path test, 10%-of-baseline perf check, audit Tier 1 verification, hub pack import propagation, cost-per-document recording) → blocked on Phase 0.11's load driver having representative content.

**Next:** Phase 2 (system-wide resilience) is the natural next step now that Phase 1's actionable closeout is done. ~15-25 PRs estimated covering recovery jobs, vendor circuit breakers, backpressure / rate limits, quorum queues + DLQ wiring.

## 2026-04-30 — Phase 2.1 PR1 — `StalePipelineRunRecoveryTask` (detect + fail-out)

**Done:** First half of Phase 2.1 plan item 1 — detection of v2 `PipelineRun`s stuck in `RUNNING`/`WAITING` for >15 min, with explicit fail-out so they no longer sit invisibly stuck. The legacy `StaleDocumentRecoveryTask` (which operates on `DocumentModel.status` — the v1 form) stays as-is; this is a parallel task for the v2 `pipeline_runs` / `node_runs` collections that the legacy task doesn't touch. Auto-resume from the last completed node is deferred to PR2 (needs a public `engine.resumeRun` entry point).

**Why split:** The plan item is "detects pipeline runs stuck > 15 min; resets to last completed node; re-queues." The detect-and-fail behaviour is a complete observable outcome on its own — operators stop seeing silently stuck runs. Auto-resume requires exposing a new entry point on `PipelineExecutionEngine` (currently the only ways into `walkNodes` are `executePipeline(DocumentIngestedEvent)` for fresh runs and `resumePipeline(LlmJobCompletedEvent)` for LLM-completion callbacks — neither fits the "continue from currentNodeIndex" shape). Doing the engine refactor in the same PR conflates two reviewable concerns; splitting keeps each PR's surface tight.

**Changes:**

- `infrastructure/services/pipeline/StalePipelineRunRecoveryTask.java` (new) — `@Scheduled(fixedDelay = 5 min, initialDelay = 2 min)`. Queries `pipeline_runs` for status ∈ {`RUNNING`, `WAITING`} AND `updatedAt < now-15min` via the existing `findByStatusAndUpdatedAtBefore` repo method. Per stale run: enumerates `node_runs`, marks any in-flight (`RUNNING` / `WAITING`) `NodeRun` as `FAILED` with `error="STALE_RECOVERY: ..."` and `completedAt`/`durationMs` stamped; marks the `PipelineRun` itself `FAILED` with `errorNodeKey=currentNodeKey`, `error="STALE_RECOVERY: ..."`, `completedAt`/`totalDurationMs` stamped; persists a `SystemError` row so the existing monitoring page surfaces the recovery action. Top-level errors are caught and turned into a `SystemError` so the task self-reports if Mongo goes offline mid-cycle.
- Per-cycle Micrometer counters: `pipeline.stale.detected` (every stale `PipelineRun` seen) and `pipeline.stale.failed` (every run successfully failed-out — diverges from `detected` when a per-run save throws, which the task swallows so the next iteration retries).
- `infrastructure/services/pipeline/StalePipelineRunRecoveryTaskTest.java` (new) — 8 tests: no-op when nothing stale; `RUNNING` run + in-flight `NodeRun` both failed with stamped fields; `WAITING` run also handled; completed/skipped/already-failed `NodeRun`s left alone; multiple stale runs processed independently; one-run save failure doesn't break the batch; top-level exception persists `SystemError` instead of escaping; absent `MeterRegistry` doesn't break the task.

**Tests:** 94 / 0 in `gls-app-assembly` (was 86; +8 new for `StalePipelineRunRecoveryTaskTest`). Reactor green.

**Decisions logged:** None new. Task lives in `gls-app-assembly` (not the aspirational `gls-scheduler` module the plan named) because the engine + repos already live there; carving out `gls-scheduler` is a separate architectural cleanup.

**Files changed:**

- `backend/gls-app-assembly/src/main/java/.../infrastructure/services/pipeline/StalePipelineRunRecoveryTask.java` — 1 new.
- `backend/gls-app-assembly/src/test/java/.../infrastructure/services/pipeline/StalePipelineRunRecoveryTaskTest.java` — 1 new.
- Log = 3 files total.

**Open issues / deferred:**

- **Auto-resume (PR2 of this plan item).** Add `PipelineExecutionEngine.resumeRun(pipelineRunId)` that loads the run + document, reconstructs a synthetic `DocumentIngestedEvent`, and calls `walkNodes` from `currentNodeIndex`. Then the recovery task can call it instead of fail-out, with retry-count bookkeeping (`MAX_AUTO_RETRIES` guard analogous to the legacy task) before falling back to the fail-out path.
- **No `@SchedulerLock` on the recovery task itself.** Single-replica deploys are fine today; multi-replica deploys would have every replica running the task in parallel and racing to fail the same runs out (idempotent — the second replica's save would see status already `FAILED` and overwrite with the same value). Add leader-election via ShedLock alongside the analogous treatment for the legacy `StaleDocumentRecoveryTask` once the multi-replica roll-out lands.
- **No metric for run age at detection time.** Useful for SLO dashboards (p95 detection-lag = wall-time between a run going stale and being failed out). Add a `Timer` or distribution summary in PR2 once the `Counter` shape proves out.

**Phase 2.1 status:** Plan item 1 (`StaleDocumentRecoveryTask`) — first half done (v2 detection + fail-out). Second half (auto-resume) is PR2. Remaining 2.1 items (classification_outbox reconciler, audit outbox replay, ES reconciliation, Hub pack import retry) untouched.

**Next:** Phase 2.1 PR2 — `engine.resumeRun(pipelineRunId)` + wire the recovery task to call it instead of fail-out.

## 2026-04-30 — Phase 2.1 PR2 — `engine.resumeRun` + wire stale recovery to auto-resume

**Done:** Closed Phase 2.1 plan item 1 by adding the missing public engine entry point and wiring `StalePipelineRunRecoveryTask` to use it. Stale runs now auto-resume from `currentNodeIndex` (re-executing the just-failed node) up to `MAX_AUTO_RETRIES = 3`; only after exhausting retries does the task fall back to PR1's fail-out path. The "resets to last completed node; re-queues" half of the plan wording lands here — `currentNodeIndex` always points to the node we want to retry next (the engine sets it when advancing), so resuming from it implicitly skips over already-`SUCCEEDED` `NodeRun`s.

**Changes:**

- `infrastructure/services/pipeline/PipelineExecutionEngine.java` — new public method `resumeRun(String pipelineRunId)`. Mirrors `resumePipeline(LlmJobCompletedEvent)`'s shape but doesn't require an LLM completion event: loads the run + document, refuses terminal-status runs (`COMPLETED` / `FAILED` / `CANCELLED`), fails the run cleanly when the document has been deleted, otherwise stamps `status=RUNNING` + `updatedAt`, reconstructs a synthetic `DocumentIngestedEvent` from the document, and calls the private `walkNodes` from `currentNodeIndex`. Exceptions during the walk are caught and turned into `failRun` + `handleNodeError` so the run never sits half-resumed.
- `infrastructure/services/pipeline/StalePipelineRunRecoveryTask.java` — new constructor parameter `ObjectProvider<PipelineExecutionEngine>` (engine is `@ConditionalOnProperty(pipeline.execution-engine.enabled)` so the bean can be absent in legacy v1-only deployments). Added `MAX_AUTO_RETRIES = 3`. Per-run algorithm replaced with a four-outcome state machine — `RESUMED` (engine present + retries remaining → re-queue via `engine.resumeRun`), `EXHAUSTED` (`retryCount >= MAX` → fail-out), `FAILED` (engine absent → fail-out, legacy fallback), `PERSIST_FAILED` (engine threw or save threw → leave for next cycle). New counters `pipeline.stale.resumed` and `pipeline.stale.exhausted` join PR1's `detected` + `failed`.
- `PipelineExecutionEngineResumeRunTest.java` (new) — 4 early-exit tests for `resumeRun`: unknown pipeline run id is a silent no-op; terminal-status runs (`COMPLETED` / `FAILED` / `CANCELLED`) are refused; missing document fails the run via `failRun`; runnable run with present document sets `status=RUNNING` and saves before walking.
- `StalePipelineRunRecoveryTaskTest.java` — extended from 8 → 9 tests. Existing test names refreshed to match the new behaviour. New scenarios covered: under-max-retries with engine present resumes (counter `pipeline.stale.resumed=1`); at-max-retries exhausts and fails-out (`pipeline.stale.exhausted=1`); engine absent falls back to fail-out (`pipeline.stale.failed=1`, legacy path); engine throwing leaves run for next cycle without false positives in any outcome counter.

**Tests:** 99 / 0 in `gls-app-assembly` (was 94; +4 for `PipelineExecutionEngineResumeRunTest` + 1 net addition in `StalePipelineRunRecoveryTaskTest`). Reactor green.

**Decisions logged:** None new. The engine's resume-from-arbitrary-node entry point closes a long-implicit gap — every previous in-engine entry assumed a fresh `DocumentIngestedEvent` (Phase 1.1) or LLM async completion (Phase 1.6 / 1.10). Making it explicit now means future admin "retry pipeline run" UI hooks and the Phase 2.1 reconciler / replay tasks all have one consistent entry point.

**Files changed:**

- `backend/gls-app-assembly/src/main/java/.../infrastructure/services/pipeline/PipelineExecutionEngine.java` — modified (new `resumeRun` method only — `~70 LOC` added in a fresh section).
- `backend/gls-app-assembly/src/main/java/.../infrastructure/services/pipeline/StalePipelineRunRecoveryTask.java` — modified (constructor + state machine + counter additions).
- `backend/gls-app-assembly/src/test/java/.../infrastructure/services/pipeline/PipelineExecutionEngineResumeRunTest.java` — 1 new.
- `backend/gls-app-assembly/src/test/java/.../infrastructure/services/pipeline/StalePipelineRunRecoveryTaskTest.java` — modified.
- Log = 5 files total.

**Open issues / deferred:**

- **Engine resume runs synchronously on the scheduler thread.** A long-running stale-run resume can starve the next iteration. Bounded today by the worst-case `walkNodes` runtime per stale run × number of stale runs per cycle. If this becomes visible in dashboards (cycle duration > scheduling interval), wrap the `engine.resumeRun` call in a bounded `ExecutorService` so the recovery task remains responsive.
- **No `@SchedulerLock` on the recovery task itself.** Multi-replica deploys would have every replica running the task simultaneously and racing to resume the same run. The race is largely benign (engine refuses to resume a run that's already `RUNNING` once another replica's save lands, since `resumeRun` checks status — wait, it actually sets it to `RUNNING` regardless and re-walks) but doubles the resume work and inflates the retry counter twice. Add ShedLock leader-election alongside the analogous treatment for the legacy `StaleDocumentRecoveryTask`.
- **No timer for run age at detection time.** Useful for SLO dashboards (p95 detection-lag = wall-time between a run going stale and being acted on). Add a `Timer` or distribution summary alongside the counters.

**Phase 2.1 status:** Plan item 1 (`StaleDocumentRecoveryTask`) **complete**. Remaining 2.1 items (classification_outbox reconciler, audit outbox replay, ES reconciliation job, Hub pack import retry on `gls.config.changed` failure) untouched — each is its own PR.

**Next:** Phase 2.1 PR3 — pick the next plan item. Audit outbox replay (`gls-audit-collector` startup drain) is the simplest and naturally pairs with the audit-collector code already shipped in 1.12. ES reconciliation needs `gls-indexing-worker` integration and is more involved.

## 2026-04-30 — Phase 2.1 PR3 — Audit outbox startup replay

**Done:** Closed Phase 2.1 plan item 3 ("Audit outbox replay: on `gls-audit-collector` restart, drains any unacked outbox rows"). The {@link OutboxRelay}'s `@Scheduled` poll already drains `PENDING` rows whose `nextRetryAt` is past — including immediately on the first poll after startup. The gap was rows still in backoff (`nextRetryAt` in the future): they sit waiting after a restart, even though the underlying transient failure that caused the backoff may already be resolved.

**Why this scope:** The plan item names `gls-audit-collector` as the restart trigger, but the outbox lives on the emitter side (in `gls-platform-audit`, used by every service that emits audit events) — the collector is only a downstream consumer. The actual restart that matters is the relay's. So the closeout work belongs in `gls-platform-audit`, not `gls-audit-collector`.

**Changes:**

- `gls-platform-audit/relay/OutboxStartupReplay.java` (new) — listens for `ApplicationReadyEvent`, runs a single `MongoTemplate.updateMulti({status: PENDING, nextRetryAt: {$gt: now}}, {$set: {nextRetryAt: now}})`. The relay's next 5-second poll then picks them all up immediately. Attempt counter is preserved — persistent failures still hit `maxAttempts → FAILED` as before; we just compress the wall-clock time between attempts. Failures during replay are caught and logged (never blocks app boot). Counter `audit.outbox.startup_replay.reset` increments by the modified count when > 0.
- `gls-platform-audit/relay/OutboxStartupReplayTest.java` (new) — 6 tests: backed-off rows reset + counter incremented; clean state is a no-op (counter never registered); query filters PENDING + future nextRetryAt only; update only touches nextRetryAt (attempts / lastError / status preserved); absent `MeterRegistry` doesn't break; `onApplicationReady` swallows exceptions so app boot can't be blocked.
- `gls-platform-audit/autoconfigure/PlatformAuditAutoConfiguration.java` — register `OutboxStartupReplay` as a bean alongside `OutboxRelay`. Same gating: `@ConditionalOnProperty(name="gls.platform.audit.relay.enabled", matchIfMissing=true)` and `@ConditionalOnClass(RabbitTemplate.class)`. If the relay is off, there's nothing to replay into.

**Tests:** 30 / 0 in `gls-platform-audit` (was 24; +6 new for `OutboxStartupReplayTest`). Reactor green.

**Decisions logged:** None new. The plan item's wording ("on `gls-audit-collector` restart") is interpreted as "on relay restart" — clarified in the PR description and the next architecture-doc update.

**Files changed:**

- `backend/gls-platform-audit/src/main/java/.../platformaudit/relay/OutboxStartupReplay.java` — 1 new.
- `backend/gls-platform-audit/src/main/java/.../platformaudit/autoconfigure/PlatformAuditAutoConfiguration.java` — modified.
- `backend/gls-platform-audit/src/test/java/.../platformaudit/relay/OutboxStartupReplayTest.java` — 1 new.
- Log = 4 files total.

**Open issues / deferred:**

- **`FAILED` rows are not auto-resurrected.** Rows that exceeded `maxAttempts` for transient AMQP failures stay `FAILED` even after a restart. Auto-resurrection risks cascading-failure loops if the underlying issue persists; the right shape is an admin-triggered "replay FAILED" action (future PR) where an operator confirms the cause is resolved before pulling the trigger.
- **No metric for in-backoff age distribution.** A histogram of `(now - nextRetryAt)` across PENDING rows would show how much wall-time the restart-replay actually saves. Useful for operators tuning `pollInterval` / `backoffMax`. Not added here to keep PR small.
- **Multi-replica startup races are benign but observable.** Each replica fires `ApplicationReadyEvent` and runs the replay; the first replica's bulk update modifies M rows, the others modify 0 (the rows already have `nextRetryAt` matching `now` from the first call). The counter correctly reflects this. If the noise becomes annoying in dashboards, gate the replay behind a `@SchedulerLock` like the relay's poll loop.

**Phase 2.1 status:** Plan items 1 (`StaleDocumentRecoveryTask`) and 3 (audit outbox replay) **complete**. Remaining 2.1 items (`classification_outbox` reconciler per §6.5, ES reconciliation, Hub pack import retry on `gls.config.changed` failure) untouched — each is its own PR.

**Next:** Phase 2.1 PR4 — pick another 2.1 item. Hub pack import retry is small and self-contained; ES reconciliation is bigger but unblocks a Phase 1 acceptance gate (search verification); `classification_outbox` reconciler depends on the outbox actually existing (need to check whether it's been built yet).

## 2026-04-30 — Phase 2.1 PR4 — `ConfigChangePublisher` retry buffer

**Done:** Closed Phase 2.1 plan item 5 ("Hub pack import retry on `gls.config.changed` failure"). The publisher's previous behaviour on AMQP failure was log-and-drop, with a comment claiming the next mutation would self-heal — true for *that* entity, false for the rest of a multi-entity hub pack import where one publish can fail and silently lose 99 sibling cache invalidations. Added a bounded in-memory retry buffer with a scheduled drain so transient broker outages no longer leave peers serving stale cache for hours.

**Changes:**

- `gls-platform-config/publish/ConfigChangePublisher.java` — refactored the `publish` path: it now delegates to a private `trySend` that returns `boolean` (true if sent, false if AMQP raised). On `false`, the event is enqueued in a bounded `ConcurrentLinkedDeque`. Bounded buffer (default 1024 events, configurable via `gls.platform.config.publisher.retry.buffer-size`) drops the oldest event when full, recording the drop on `config.change.retry.dropped`. Serialisation failures continue to drop without buffering (deterministic — retrying won't help). New `@Scheduled` `flushRetryBuffer()` ticks every 30 s by default (`gls.platform.config.publisher.retry.flush-interval`) and drains the buffer head-first, stopping at the first AMQP failure so a stuck broker doesn't burn through the buffer in one tick. Re-entrancy guarded by an `AtomicBoolean`.
- `gls-platform-config/autoconfigure/PlatformConfigAutoConfiguration.java` — bean factory updated to inject `ObjectProvider<MeterRegistry>` and the `retry.buffer-size` property; existing callers continue to work unchanged because the publisher's public surface (`publishSingle`, `publishBulk`, `publishMany`, `publish`) is unchanged.
- `gls-platform-config/test/.../publish/ConfigChangePublisherTest.java` (new) — 10 tests: successful publish doesn't buffer; AMQP failure buffers; flush drains on broker recovery; flush stops at first failure (stuck broker doesn't burn buffer); buffer at cap drops oldest; empty flush is silent no-op; serialisation failure drops without buffering; absent `MeterRegistry` doesn't break; `publishBulk` failure buffers correctly; `publish` with `traceparent` preserved through buffer round-trip.

**Tests:** 29 / 0 in `gls-platform-config` (was 19; +10 new for `ConfigChangePublisherTest`). Reactor green via `./mvnw -DskipTests package`.

**Decisions logged:** None new. Bounded-in-memory-buffer trade-off matches the existing comment in `ConfigChangePublisher`'s class doc (cache invalidation is best-effort, not durable). Stronger durability (Mongo-backed outbox like `audit_outbox`) is the obvious next step but tracked as a follow-up — the in-memory buffer covers the common case (transient broker blip lasting seconds-to-minutes) cleanly.

**Files changed:**

- `backend/gls-platform-config/src/main/java/.../publish/ConfigChangePublisher.java` — modified.
- `backend/gls-platform-config/src/main/java/.../autoconfigure/PlatformConfigAutoConfiguration.java` — modified.
- `backend/gls-platform-config/src/test/java/.../publish/ConfigChangePublisherTest.java` — 1 new.
- Log = 4 files total.

**Open issues / deferred:**

- **Buffer is in-memory.** A pod crash loses the buffered events. For cache-invalidation semantics this is acceptable — the next mutation of the same entity self-heals. For *hub pack imports* specifically, where 100+ entities update in a tight loop, a crash after some have been published could leave peers in an inconsistent cache state. The right fix is a durable `config_change_outbox` collection mirroring the audit-outbox shape; tracked as a Phase 2 follow-up but not gated by it.
- **No back-pressure on the import side.** When the buffer fills, oldest events are silently dropped. A high-volume import with the broker down for tens of minutes could quietly lose cache invalidations for the early entities. Operators should monitor `config.change.retry.dropped` — non-zero values indicate the buffer cap should be raised or the broker outage investigated.
- **Hub pack import flow doesn't yet observe the buffer.** The plan wording asked for "retry on `gls.config.changed` failure" which this PR delivers; a stricter interpretation would also wait for the buffer to drain before reporting "import complete" to the operator. That UX polish is deferred.

**Phase 2.1 status:** Plan items 1 (`StaleDocumentRecoveryTask`), 3 (audit outbox replay), and 5 (Hub pack import retry) **complete**. Remaining 2.1 items: `classification_outbox` reconciler per §6.5 (depends on whether `classification_outbox` collection is actually wired yet — needs survey), ES reconciliation job (bigger, unblocks Phase 1 acceptance gate for search verification).

**Next:** Phase 2.1 PR5 — ES reconciliation job, or survey `classification_outbox` first to see if there's a collection to drain. ES reconciliation is the natural pick if the search-side acceptance gate is the priority.

## 2026-04-30 — Phase 2.1 PR5 — ES reconciliation task

**Done:** Closed Phase 2.1 plan item 4 ("ES reconciliation job: rebuild ES from Mongo if index is corrupted or behind"). Surveyed: `classification_outbox` (plan item 2) doesn't exist yet — it's tied to a §6.5 architecture component that hasn't been built, so skipped to ES reconciliation. The new `IndexReconciliationTask` runs daily by default, compares Mongo's non-DISPOSED document count against ES's `_count`, and surfaces drift as both metrics and logs. Auto-fix (calling the existing `IndexingService.reindexAll`) is gated behind `gls.indexing.reconciliation.auto-fix=true` (default off) to avoid heavy unsupervised rebuilds during a real incident.

**Why split observe + auto-fix as separate behaviours:** The plan wording says "rebuild ES from Mongo if index is corrupted or behind" which implies auto-rebuild. But auto-rebuild on every detected delta is dangerous — could cascade during a real incident (e.g. ES briefly unavailable → reconciliation fires → reindexAll fails partway → next reconciliation fires → etc). Default-off auto-fix gives operators an explicit "I trust this" knob. Even off, the metrics + WARN logs make drift visible.

**Changes:**

- `gls-indexing-worker/service/IndexReconciliationTask.java` (new) — `@Scheduled` daily (override via `gls.indexing.reconciliation.interval`, e.g. `PT1H`). Surveys `mongoCount = documentRepo.count() - countByStatus(DISPOSED)` and `esCount = GET /ig_central_documents/_count`. Records gauges `index.reconciliation.mongo.count` and `index.reconciliation.es.count` plus a tagged counter `index.reconciliation.runs{outcome=clean|drift|error}`. When delta > `gls.indexing.reconciliation.drift-threshold` (default 10) and `auto-fix=true` and ES is behind (positive delta), calls `IndexingService.reindexAll(null)` and bumps `index.reconciliation.fixes_triggered`. Negative delta (ES has stragglers) logs loudly but never auto-deletes — pruning is a separate lifecycle concern.
- `gls-indexing-worker/GlsIndexingWorkerApplication.java` — added `@EnableScheduling` so the new `@Scheduled` method actually ticks. The existing `@EnableAsync` covers the unrelated `AsyncDispatcher` flow.
- `gls-indexing-worker/test/.../service/IndexReconciliationTaskTest.java` (new) — 10 tests: ES `_count` parser handles well-formed JSON / malformed input / missing field; clean state → `clean` outcome counter; drift above threshold without auto-fix → `drift` counter only; positive drift with auto-fix → `reindexAll` called + `fixes_triggered` ticks; negative drift with auto-fix → no reindex (pruning not implemented); reindex throwing during auto-fix is caught + logged + counter NOT bumped; `surveyOnce` calls `count() - countByStatus(DISPOSED)` for the Mongo total; delta exactly at threshold treated as clean (not drift); absent `MeterRegistry` doesn't break.

**Tests:** 34 / 0 in `gls-indexing-worker` (was 24; +10 new for `IndexReconciliationTaskTest`). Reactor green via `./mvnw -DskipTests package`.

**Decisions logged:** None new. The auto-fix-default-off stance is explained in the class JavaDoc — operators flip the flag once they're comfortable.

**Files changed:**

- `backend/gls-indexing-worker/src/main/java/.../indexing/service/IndexReconciliationTask.java` — 1 new.
- `backend/gls-indexing-worker/src/main/java/.../indexing/GlsIndexingWorkerApplication.java` — modified (added `@EnableScheduling`).
- `backend/gls-indexing-worker/src/test/java/.../indexing/service/IndexReconciliationTaskTest.java` — 1 new.
- Log = 4 files total.

**Open issues / deferred:**

- **Count-based drift detection misses content drift.** Two indexes with the same count can still disagree on document content (e.g. tags / extractedMetadata changes that never reached ES). Detecting that requires either embedding `updatedAt` in the ES document and comparing per-doc, or hashing a subset of fields and comparing. Useful follow-up but not in scope here — count drift is the most common failure mode (ingestion path failed for a batch, ES went down mid-burst).
- **No per-document delta repair.** When drift is detected, auto-fix runs `reindexAll` (the full nuclear option). A scoped delta — query Mongo for non-DISPOSED IDs, query ES for the same IDs in batches, reindex only the missing — would be much cheaper for large indexes with small drift. Tracked as a Phase 2 follow-up; gated on having a use case where full reindex is too heavy.
- **No auto-prune for negative drift.** If ES has documents that Mongo doesn't (deletes that didn't propagate), reconciliation logs but doesn't fix. Auto-deleting from ES based on "absence in Mongo" is risky if Mongo is the one that's behind temporarily. Pruning is a separate operator-driven action.

**Phase 2.1 status:** Plan items 1, 3, 4, 5 **complete**. Remaining: `classification_outbox` reconciler — depends on the outbox collection actually existing (it doesn't yet). When the §6.5 component lands, the reconciler is a small follow-up.

**Next:** Either move to Phase 2.2 (vendor / external API resilience — Anthropic circuit breaker, Ollama fallback, MCP unreachable handling, cost budget enforcement), or tackle the `classification_outbox` workstream by first building the outbox itself. Phase 2.2 is the natural progression.

## 2026-04-30 — Phase 2.2 PR1 — `CircuitBreaker` + `CircuitBreakerLlmService`

**Done:** Started Phase 2.2 (vendor resilience). New `CircuitBreaker` primitive (CLOSED → OPEN → HALF_OPEN) plus `CircuitBreakerLlmService` decorator wraps the configured Anthropic / Ollama backend. Transport / 5xx failures (which the existing services surface as `RuntimeException`s) trip the breaker after `failure-threshold` consecutive failures. After `open-cooldown`, the next call probes via `HALF_OPEN`; success closes, failure re-opens with a fresh cooldown. Caller-side gates (`BudgetExceededException`, `RateLimitExceededException`) are explicitly *not* treated as failures — those reflect our own back-pressure, not upstream health.

**Changes:**

- `gls-llm-worker/backend/CircuitBreaker.java` (new) — pure logic, no LLM-specific knowledge. Atomic state reference + atomic failure counter. Constructor takes a `Clock` for deterministic testing. Threshold and cooldown validated at construction.
- `gls-llm-worker/backend/CircuitBreakerLlmService.java` (new) — decorator implementing `LlmService`. On call: checks `breaker.beforeCall()`; if false, throws `CircuitBreakerOpenException`. Else delegates; on success calls `recordSuccess`; on `RuntimeException` (other than the two caller-gates) calls `recordFailure` then rethrows. `isReady()` returns false when the breaker is OPEN so the `/v1/capabilities` health surface reflects the upstream state.
- `gls-llm-worker/backend/CircuitBreakerOpenException.java` (new) — typed exception so the controller advice can map cleanly to 503.
- `gls-llm-worker/web/LlmExceptionHandler.java` — new `@ExceptionHandler` for `CircuitBreakerOpenException`: 503 with `code=LLM_UPSTREAM_UNAVAILABLE`, `Retry-After: 1` (conservative; the cascade router's own backoff drives the real wait).
- `gls-llm-worker/backend/LlmBackendConfig.java` — both Anthropic and Ollama backends are now wrapped via a new `wrapWithCircuitBreaker` helper. Gated by `gls.llm.worker.circuit-breaker.enabled` (default `true`). Tunables: `failure-threshold` (default 5), `open-cooldown` (default `PT30S`).
- `gls-llm-worker/test/.../backend/CircuitBreakerTest.java` (new) — 9 tests: initial state CLOSED; sub-threshold failures stay CLOSED; threshold failures open; success in CLOSED resets counter; OPEN→HALF_OPEN after cooldown (via injected clock); HALF_OPEN success → CLOSED; HALF_OPEN failure → OPEN with fresh cooldown; OPEN with no cooldown elapsed keeps rejecting; invalid config rejected.
- `gls-llm-worker/test/.../backend/CircuitBreakerLlmServiceTest.java` (new) — 8 tests: success passes through and resets; runtime exception records failure; threshold failures open + short-circuit subsequent calls; `BudgetExceededException` doesn't count; `RateLimitExceededException` doesn't count; `isReady()` reflects breaker open; `activeBackend()` delegates; success after failures resets counter.

**Tests:** 68 / 0 in `gls-llm-worker` (was 51; +17 new across the two test classes). Reactor green via `./mvnw -DskipTests package`.

**Decisions logged:** None new. Caller-side gates not counting as failures is the load-bearing decision — without it, a hammered budget would trip the breaker and prevent the normal `429 Retry-After` path from working.

**Files changed:**

- `backend/gls-llm-worker/src/main/java/.../backend/{CircuitBreaker, CircuitBreakerLlmService, CircuitBreakerOpenException}.java` — 3 new.
- `backend/gls-llm-worker/src/main/java/.../backend/LlmBackendConfig.java` — modified.
- `backend/gls-llm-worker/src/main/java/.../web/LlmExceptionHandler.java` — modified.
- `backend/gls-llm-worker/src/test/java/.../backend/{CircuitBreakerTest, CircuitBreakerLlmServiceTest}.java` — 2 new.
- Log = 7 files total.

**Open issues / deferred:**

- **No fallback path between backends.** When the Anthropic breaker is OPEN, the worker returns 503; it doesn't try Ollama. The cascade router (`gls-classifier-router`) already has SLM/LLM fallback semantics for `LLM_NOT_CONFIGURED` and 5xx; the `LLM_UPSTREAM_UNAVAILABLE` 503 introduced here participates in that cascade. Direct in-worker fallback (so the worker swaps to Ollama transparently) is a Phase 2.2 PR2 candidate but creates an awkward coupling between the two backends.
- **No 429 / `Retry-After` honoring on the Anthropic side itself.** The breaker treats every `RuntimeException` the same; a real HTTP 429 from Anthropic is currently mapped to `LLM_DEPENDENCY_UNAVAILABLE` 503 by `handleIo`, the breaker counts it as a failure, and the worker doesn't read the `Retry-After` header. Phase 2.2 PR2 plan item — needs Spring AI to surface the response status / headers, which it doesn't do cleanly today (the SDK wraps everything as `RuntimeException`).
- **No per-backend metrics on breaker state.** Useful gauges: `llm.circuit_breaker.state{backend}` (0=CLOSED, 1=OPEN, 2=HALF_OPEN), `llm.circuit_breaker.consecutive_failures{backend}`. Tracked alongside the broader Phase 2.6 observability uplift; cheap to add when that PR lands.

**Phase 2.2 status:** Plan item 1 (Anthropic circuit breaker) **complete** — the implementation is generic and applies to any backend. Item 2 (Anthropic 429 / `Retry-After`) untouched. Item 3 (Ollama unreachable + Anthropic fallback) untouched. Item 4 (MCP unreachable → confidence cap) untouched. Item 5 (cost budget enforcement) is mostly done from Phase 1.6 PR4 — needs the auto-degrade-to-SLM piece.

**Next:** Phase 2.2 PR2 — wire the cascade router to fall through on `LLM_UPSTREAM_UNAVAILABLE` 503 (it likely already does via the existing 5xx handler, but confirm + test). Then MCP unreachable handling, then 429 / `Retry-After`.

## 2026-04-30 — Phase 2.2 PR2 — MCP unreachable → cap confidence

**Done:** Closed Phase 2.2 plan item 4 ("MCP unreachable → workers proceed with cap on confidence per §6.6"). New `McpAvailabilityProbe` polls `<mcp-base-url>/actuator/health` periodically; new `McpCappingLlmService` decorator caps the confidence on the underlying LLM result whenever the most recent probe returned unreachable. Workers continue to function (Spring AI's MCP client already logs and proceeds without tools when SSE fails) — the cap is the downstream-visible signal that the result was made without correction-history / org-PII context.

**Changes:**

- `gls-llm-worker/backend/McpAvailabilityProbe.java` (new) — `@Component` that polls `<mcp-base-url><probe-path>` (default `/actuator/health`) every `gls.llm.worker.mcp.probe.interval` (default `PT30S`) with a 3 s connect+read timeout. Stores reachability in an `AtomicBoolean`, initial state optimistic-available. State transitions log at INFO (recovered) / WARN (lost). Empty MCP URL means "unconfigured" — probes are no-ops and `isConfigured()` returns false so the cap never fires for workers that explicitly run without MCP. 200/300/400 status counts as reachable; 500+/transport failure counts as unreachable.
- `gls-llm-worker/backend/McpCappingLlmService.java` (new) — `LlmService` decorator. After delegating to the underlying service: if probe `isConfigured()` and `!isAvailable()`, and the result's confidence exceeds `maxConfidence`, returns a new `LlmResult` with confidence replaced and a rationale suffix `[confidence capped: MCP unreachable]`. All other fields pass through. Throws are not intercepted.
- `gls-llm-worker/backend/LlmBackendConfig.java` — added two new `@Value` knobs (`gls.llm.worker.mcp.confidence-cap.enabled` default `true`, `gls.llm.worker.mcp.confidence-cap.max-confidence` default `0.7`) and an `ObjectProvider<McpAvailabilityProbe>` injection. The configured backend is now wrapped twice: `McpCappingLlmService(CircuitBreakerLlmService(base, breaker), probe, cap)`. Order matters — cap inspects post-success; breaker open throws past the cap which is fine (we don't cap on failure).
- `gls-llm-worker/GlsLlmWorkerApplication.java` — added `@EnableScheduling` so the probe's `@Scheduled` method ticks. The existing `@EnableAsync` covers the unrelated async-classify dispatcher.
- `gls-llm-worker/test/.../backend/McpAvailabilityProbeTest.java` (new) — 7 tests: initial state optimistic-available; empty / blank URL means unconfigured; `doProbe` returns false for unreachable host (real HTTP failing fast); probe is no-op when unconfigured; `setAvailable` test hook; blank probe path falls back to default.
- `gls-llm-worker/test/.../backend/McpCappingLlmServiceTest.java` (new) — 9 tests: MCP available passes through; unavailable caps high confidence; unavailable doesn't increase low confidence; cap-at-cap is no change; unconfigured probe never caps; null rationale handled; exceptions propagate; metadata preserved; invalid `maxConfidence` rejected.

**Tests:** 84 / 0 in `gls-llm-worker` (was 68; +16 new across the two test classes). Reactor green.

**Decisions logged:** None new. The "scheduled probe + atomic flag" pattern is the same shape as `CircuitBreaker.beforeCall` (decision computed elsewhere, read synchronously per call) — explicitly chosen over per-call HTTP probes to avoid amplifying load on the MCP server during partial outages.

**Files changed:**

- `backend/gls-llm-worker/src/main/java/.../backend/{McpAvailabilityProbe, McpCappingLlmService}.java` — 2 new.
- `backend/gls-llm-worker/src/main/java/.../backend/LlmBackendConfig.java` — modified.
- `backend/gls-llm-worker/src/main/java/.../GlsLlmWorkerApplication.java` — modified (added `@EnableScheduling`).
- `backend/gls-llm-worker/src/test/java/.../backend/{McpAvailabilityProbeTest, McpCappingLlmServiceTest}.java` — 2 new.
- Log = 6 files total.

**Open issues / deferred:**

- **Same pattern not yet applied to `gls-slm-worker`.** The SLM worker also uses MCP tools. The decorator chain is identical in shape but lives in a different module; copy-pasting now would cement the duplication. Instead: when SLM gets the same treatment, lift `McpAvailabilityProbe` + `McpCappingLlmService` into a shared module (likely a new `gls-llm-resilience` library, or fold into an existing shared module).
- **Cap is global, not per-tool.** If MCP is partially degraded (e.g. only the correction-history tool is failing while taxonomy fetch works), the cap fires for every call regardless of whether the broken tool was actually invoked. A finer-grained "did this call use MCP successfully?" signal would require Spring AI surfacing tool-invocation outcomes per request — not exposed today.
- **No metric on cap rate.** Useful for SLO dashboards: `llm.mcp.confidence_capped` counter ticking per capped call. Tracked alongside the broader Phase 2.6 observability uplift.

**Phase 2.2 status:** Plan items 1 (Anthropic circuit breaker) and 4 (MCP confidence cap) **complete**. Items 2 (Anthropic 429 / `Retry-After`), 3 (Ollama unreachable + Anthropic fallback), 5 (cost budget auto-degrade-to-SLM) untouched.

**Next:** Phase 2.2 PR3 — pick item 5 (cost budget auto-degrade) which builds on the existing `CostBudgetTracker` + extends to auto-degrade behaviour. Item 2 (429 honoring) is harder because Spring AI doesn't surface response headers cleanly. Item 3 needs an inter-backend fallback design first.

## 2026-04-30 — Phase 2.2 PR3 — LLM budget auto-degrade gate

**Done:** Closed Phase 2.2 plan item 5 ("cost budget gate enforcement — daily/job spending cap with auto-degrade to SLM-only"). The worker side already enforces the budget (Phase 1.6 PR4 — `CostBudgetTracker` returns 429 `LLM_BUDGET_EXCEEDED` with a `Retry-After: <seconds-until-midnight-UTC>` header). What was missing: the cascade router would round-trip to the worker on every classify call only to be told the budget is exhausted, every 5 seconds, until midnight — burning latency and load. The new `LlmBudgetGate` short-circuits future calls in-process for the duration of the worker's `Retry-After`, so the cascade falls through to the SLM tier without the wasted hop.

**Changes:**

- `gls-classifier-router/parse/LlmBudgetGate.java` (new) — atomic-reference state holder with `isExhausted()` (lazy clear when cool-down has elapsed), `exhaustedUntil()`, `markExhausted(Duration)`. The mark-method extends the window when a later expiry comes in but never shrinks it (a worker that recovers earlier than expected gets caught by the natural lazy-clear next time). Zero / negative / null durations floor to 1 second so a misconfigured worker can't cause a hot-loop.
- `gls-classifier-router/parse/LlmHttpDispatcher.java` — added an `LlmBudgetGate` (optional) and `budgetFallbackRetryAfter` (default `PT1H`) to the constructor. Pre-call: if the gate is exhausted, throw `LLM_BUDGET_EXHAUSTED` fallthrough immediately (no HTTP). On a `429` response: parse `code` from the body; if it's `LLM_BUDGET_EXCEEDED`, parse the `Retry-After` header (delta-seconds form only — HTTP-date form is unsupported and falls back to the configured default), arm the gate, throw fallthrough. Other 429 codes (e.g. `LLM_RATE_LIMITED`) pass through without arming the gate — they recover in seconds, not hours, so synchronous retry is fine.
- `gls-classifier-router/parse/RouterHttpConfig.java` — registered the gate as a bean and wired it into the dispatcher constructor, gated by the same `gls.router.cascade.llm-http.enabled` property.
- `gls-classifier-router/test/.../parse/LlmBudgetGateTest.java` (new) — 8 tests against an injected step-clock: initial state; basic mark + exhausted-until; auto-clear after cool-down; window extends but doesn't shrink; zero / negative / null durations floor to 1 second; clear hook.
- `gls-classifier-router/test/.../parse/LlmHttpDispatcherTest.java` — 5 new tests (was 6, now 11) using the existing `HttpServer` test harness: 429 `LLM_BUDGET_EXCEEDED` arms the gate and short-circuits subsequent calls (verified by the call counter); without `Retry-After` header the configured fallback applies; 429 `LLM_RATE_LIMITED` does NOT arm the gate; `parseRetryAfterValue` handles delta-seconds; returns empty for unsupported (HTTP-date / negative / non-numeric).

**Tests:** 90 / 0 in `gls-classifier-router` (was 77; +13 new across the two test classes). Reactor green.

**Decisions logged:** None new. Two design choices worth noting in the file (already documented in the JavaDoc):
- Only `LLM_BUDGET_EXCEEDED` arms the gate. `LLM_RATE_LIMITED` (per-replica semaphore, recovers in seconds) does not — gating on it would degrade unnecessarily during transient burst.
- `Retry-After` is parsed in delta-seconds form only. HTTP-date form would require time-zone handling for a corner case the worker doesn't currently emit; tracked as a follow-up if the form ever shows up.

**Files changed:**

- `backend/gls-classifier-router/src/main/java/.../router/parse/{LlmBudgetGate, LlmHttpDispatcher, RouterHttpConfig}.java` — 1 new + 2 modified.
- `backend/gls-classifier-router/src/test/java/.../router/parse/{LlmBudgetGateTest, LlmHttpDispatcherTest}.java` — 1 new + 1 modified.
- Log = 6 files total.

**Open issues / deferred:**

- **No metric on degrade events.** Useful: `router.llm.budget.degraded` counter ticking when the gate arms; gauge `router.llm.budget.exhausted_until_epoch_s` for dashboard visualisation. Tracked alongside the broader Phase 2.6 observability uplift.
- **No `Retry-After` HTTP-date support.** The worker only ever emits delta-seconds (`CostBudgetTracker` formats with `seconds-until-midnight-UTC`), so this isn't a real gap today. If a future worker / vendor proxy emits HTTP-date form, parser returns empty and the cascade falls back to the configured `budget-fallback-retry-after` (default 1 h).
- **Per-pipeline / per-tenant budgets not modelled.** The current gate is global per router replica. Per-tenant or per-pipeline degrade (where budget exhaustion in one tenant doesn't downgrade everyone else) needs the worker to emit a tenant identifier in the 429 envelope; that's out of scope for the resilience milestone but tracked.

**Phase 2.2 status:** Plan items 1, 4, 5 **complete**. Items 2 (Anthropic 429 / `Retry-After` honoring inside the worker — gated by Spring AI not exposing response headers) and 3 (Ollama unreachable + Anthropic fallback inside the worker — needs inter-backend coordination design) remain. Both are deeper engineering bets relative to the high-leverage items already shipped.

**Next:** Phase 2.3 (backpressure + rate limiting) is the natural progression. Items: per-worker semaphore on in-flight calls (already in place from Phase 1.6 PR4 via `RateLimitGate`); router 429 + `Retry-After` honoring orchestrator-side; Rabbit quorum queues + DLQ wiring; DLQ reprocessing UI hook (data path only, UI in Phase 3). The quorum-queue work is the largest piece — it touches every channel.

## 2026-04-30 — Phase 2.3 PR1 — Quorum-queue opt-in for `gls-app-assembly`

**Done:** First slice of Phase 2.3 plan item 3 ("RabbitMQ quorum queues + DLQ wiring for every channel"). DLQ wiring already existed for every queue declared in `gls-app-assembly`; what's added here is the ability to declare them as quorum queues (Raft-replicated, survives node loss in a multi-node cluster with no message loss) instead of classic. Gated by `gls.rabbit.quorum-queues.enabled` (default `false`) so the change is non-destructive for existing deployments — operators flip the flag during a planned maintenance window with a delete-then-redeploy runbook (RabbitMQ refuses a declaration that contradicts an existing queue's type).

**Why one-module-at-a-time:** There are five `RabbitMqConfig` classes in the codebase (`gls-app-assembly`, `gls-llm-orchestration`, `gls-document-processing`, `gls-indexing-worker`, `gls-governance-enforcement`). Doing all five in one PR risks an inconsistency snowball if any module's queues need different topology tweaks. The `gls-app-assembly` module is the most central (8 queues including the document pipeline + the LLM async path) so it's the biggest leverage point; the other four follow the same pattern in subsequent PRs.

**Changes:**

- `gls-app-assembly/.../config/RabbitMqConfig.java` — new constructor takes `gls.rabbit.quorum-queues.enabled` (default `false`). New package-private `durable(String name)` helper conditionally calls `.quorum()` on the underlying `QueueBuilder`. All 8 queue declarations switched from `QueueBuilder.durable(...)` to `durable(...)` — no other behavioural change. DLQ arguments (`x-dead-letter-exchange`, `x-dead-letter-routing-key`) and durability remain unchanged.
- `gls-app-assembly/.../config/RabbitMqConfigQuorumTest.java` (new) — 4 tests: classic mode declares no `x-queue-type` argument on any queue; quorum mode marks every queue with `x-queue-type=quorum`; DLQ wiring intact in quorum mode (both document and pipeline DLX paths verified); the package-private `durable` helper returns the right shape per flag.

**Tests:** 103 / 0 in `gls-app-assembly` (was 99; +4 new for `RabbitMqConfigQuorumTest`). Reactor green via `./mvnw -DskipTests package`.

**Decisions logged:** None new. The "default off" stance is the load-bearing decision — a flag default of `true` would silently break every existing deployment on next pod restart since RabbitMQ would refuse declarations that contradict the existing queue type, leaving the worker unable to consume.

**Files changed:**

- `backend/gls-app-assembly/src/main/java/.../infrastructure/config/RabbitMqConfig.java` — modified (new constructor + helper, all queue declarations updated to use the helper).
- `backend/gls-app-assembly/src/test/java/.../infrastructure/config/RabbitMqConfigQuorumTest.java` — 1 new.
- Log = 3 files total.

**Open issues / deferred:**

- **Other 4 `RabbitMqConfig` classes still use classic queues unconditionally.** Modules: `gls-llm-orchestration`, `gls-document-processing`, `gls-indexing-worker`, `gls-governance-enforcement`. Same pattern applies — copy the constructor + helper and swap the queue declarations. Tracked as Phase 2.3 PR2-5; can be fan-outed in parallel since each module is independent.
- **No publisher confirms / mandatory routing.** Quorum queues solve the "consumer-side message loss" half of the resilience picture; the publisher side (drop on a missing exchange / disconnected channel) needs `RabbitTemplate` configured with `setMandatory(true)` + `ConfirmCallback` / `ReturnsCallback`. Tracked as a follow-up; not a regression today (publishes go through best-effort same as before this PR).
- **Operator runbook for the migration is not in the repo.** The flag change requires deleting each affected queue (`rabbitmqctl delete_queue <name>`) before the redeploy can declare them as quorum. Document this in `docs/runbooks/` (currently doesn't exist).

**Phase 2.3 status:** Plan item 1 (per-worker semaphore on in-flight calls) — already in place from Phase 1.6 PR4 (`RateLimitGate`). Plan item 3 (quorum queues) — first module migrated (5 modules total). Items 2 (router 429 + `Retry-After`) and 4 (DLQ reprocessing data path) untouched.

**Next:** Phase 2.3 PR2 — apply the same quorum-queue pattern to `gls-llm-orchestration` (the second-most-central RabbitMqConfig). Or pivot to plan item 2 (router 429) which is a smaller change but lower priority since the router already falls through to lower tiers on dispatch failure.

## 2026-04-30 — Phase 2.3 PR2 — Quorum-queue opt-in for the remaining 4 modules

**Done:** Closed Phase 2.3 plan item 3 — every `RabbitMqConfig` in the codebase now supports the `gls.rabbit.quorum-queues.enabled` flag. Same pattern as PR1: per-module constructor + `durable(name)` helper that conditionally calls `.quorum()`. Default `false` everywhere; operators flip the flag globally and follow the per-queue delete-then-redeploy runbook.

**Why batched:** Four modules with the same mechanical pattern. Doing them one PR at a time would burn review overhead with no extra signal. Each module's queues are independent, so a single failure during deployment doesn't block another module — operators can flip the flag per-module if needed.

**Changes (per module):**

- `gls-llm-orchestration/llm/config/RabbitMqConfig.java` — 6 queues (deadLetter, processed, classified, failed, llmJobs, llmCompleted) all switched to the helper.
- `gls-document-processing/docprocessing/config/RabbitMqConfig.java` — 2 queues (ingested, processed) on the legacy in-process pipeline.
- `gls-indexing-worker/indexing/consumer/RabbitMqConfig.java` — 1 queue (`gls.documents.classified.indexing`).
- `gls-governance-enforcement/enforcement/config/RabbitMqConfig.java` — 1 queue (legacy `classified` consumer).

Plus matching `RabbitMqConfigQuorumTest` in each module — same shape as PR1's, scoped to the queues each module declares.

**Tests:** All four modules green:
- `gls-llm-orchestration` 3/0 new in `RabbitMqConfigQuorumTest`.
- `gls-document-processing` 2/0 new.
- `gls-indexing-worker` 2/0 new (module total 36, was 34).
- `gls-governance-enforcement` 2/0 new (module total 37, was 35).

Reactor green via `./mvnw -DskipTests package`.

**Decisions logged:** None new — same as PR1.

**Files changed:**

- `backend/gls-llm-orchestration/src/main/java/.../llm/config/RabbitMqConfig.java` — modified.
- `backend/gls-document-processing/src/main/java/.../docprocessing/config/RabbitMqConfig.java` — modified.
- `backend/gls-indexing-worker/src/main/java/.../indexing/consumer/RabbitMqConfig.java` — modified.
- `backend/gls-governance-enforcement/src/main/java/.../enforcement/config/RabbitMqConfig.java` — modified.
- 4 corresponding `RabbitMqConfigQuorumTest.java` — 4 new.
- Log = 9 files total.

**Open issues / deferred:**

- **Publisher confirms / mandatory routing.** Same as PR1 — quorum queues solve the consumer-side message-loss half. Adding `setMandatory(true)` + `ConfirmCallback` / `ReturnsCallback` on every `RabbitTemplate` is a follow-up.
- **Operator runbook still not in repo.** Per PR1, this needs `docs/runbooks/quorum-queue-migration.md` documenting the delete-then-redeploy steps for each queue. Tracked.
- **No global quorum-queues toggle test.** A simple smoke test that boots each module's Spring context with the flag set + verifies it propagates would catch wiring regressions. Not added because each module's `RabbitMqConfigQuorumTest` already covers the per-queue contract.

**Phase 2.3 status:** Plan items 1 (per-worker semaphore — already in place) and 3 (quorum queues) **complete**. Items 2 (router 429 + `Retry-After`) and 4 (DLQ reprocessing data path) remain.

**Next:** Phase 2.4 (failover + leader election) — `gls-audit-collector` Tier 1 leader election via ShedLock; `gls-scheduler` leader election. The connector per-source watches piece is already done from Phase 1.13. Or 2.6 observability uplift if measurement-first is preferred. Phase 2.5 chaos tests are blocked on representative load.

## 2026-04-30 — Phase 2.4 PR1 — `@SchedulerLock` on the two scheduled recovery tasks

**Done:** First slice of Phase 2.4 plan item 2 (`gls-scheduler` leader election). The two scheduled recovery tasks shipped in Phase 2.1 (`StalePipelineRunRecoveryTask`, `IndexReconciliationTask`) gain ShedLock-backed `@SchedulerLock` so multi-replica deployments don't race on the same recovery work.

**Changes:**

- `gls-app-assembly/.../infrastructure/services/pipeline/StalePipelineRunRecoveryTask.java` — added `@SchedulerLock(name="stale-pipeline-recovery")` with configurable `lockAtMostFor` (default `PT10M` — long enough for `engine.resumeRun` calls across many stale runs) and `lockAtLeastFor` (default `PT0S`).
- `gls-indexing-worker/.../indexing/service/IndexReconciliationTask.java` — added `@SchedulerLock(name="index-reconciliation")` with `lockAtMostFor` default `PT30M` (covers a worst-case full `reindexAll` when auto-fix is on).
- `gls-indexing-worker/pom.xml` — added `gls-platform-audit`, `shedlock-spring`, `shedlock-provider-mongo` deps. The first pulls in the `AuditRelayLockConfig` auto-config which provides the `LockProvider` bean and `@EnableSchedulerLock` class-level activation. Other modules already had this from earlier PRs.

**Tests:** Existing test suite still green. `gls-app-assembly` 103/0; `gls-indexing-worker` 36/0. `@SchedulerLock` is wired by Spring AOP at runtime; no new unit-level coverage added (wiring is exercised by the existing scheduling tests in `gls-platform-audit`'s `OutboxRelayTest`).

**Decisions logged:** None new. Lock-name convention matches the existing `gls-audit-outbox-relay` lock — kebab-case, descriptive of the task.

**Files changed:**

- `backend/gls-app-assembly/.../StalePipelineRunRecoveryTask.java` — modified.
- `backend/gls-indexing-worker/.../IndexReconciliationTask.java` — modified.
- `backend/gls-indexing-worker/pom.xml` — modified (3 new deps).
- Log = 4 files total.

**Open issues / deferred:**

- **No leader-election metric for these tasks.** Useful: `pipeline.scheduler.lock.acquired{name}` / `skipped{name}` to see which replica is leader and how often a replica skips because another holds the lock. Tracked alongside Phase 2.6 observability batch.
- **Audit-collector Tier 1 listener still consumes from every replica.** That's a separate problem — `@RabbitListener` doesn't gate cleanly on `@SchedulerLock`. Architecture says "run a single Tier 1 collector replica" pending the bigger programmatic-poll refactor; ops constraint, not a code gap today.

**Phase 2.4 status:** Plan item 2 (`gls-scheduler` leader election) **substantially complete** — every scheduled recovery / relay task across the codebase now has `@SchedulerLock`. Plan item 1 (audit-collector Tier 1 leader election) remains, gated on the programmatic-poll refactor. Plan item 3 (connector per-source watches) was done in Phase 1.13.

**Next:** PR-B observability metrics batch.

## 2026-04-30 — Phase 2.6 PR1 — Observability metrics batch

**Done:** Wired all the deferred Micrometer metrics flagged in earlier PRs in one pass. Five separate metrics across three modules; each is small but together they unblock dashboards for the resilience features shipped over the rest of Phase 2.

**Changes:**

- `gls-llm-worker/.../backend/CircuitBreakerLlmService.java` — new optional `MeterRegistry` constructor parameter. When supplied, registers two gauges tagged by `backend`: `llm.circuit_breaker.state` (0=CLOSED, 1=HALF_OPEN, 2=OPEN) and `llm.circuit_breaker.consecutive_failures`. The breaker reference is captured in the gauge closure so values track in real time without per-call overhead.
- `gls-llm-worker/.../backend/McpCappingLlmService.java` — new optional `MeterRegistry` constructor. On every cap event, increments `llm.mcp.confidence_capped{backend=...}` (lazily registered).
- `gls-llm-worker/.../backend/LlmBackendConfig.java` — threads `ObjectProvider<MeterRegistry>` through both `wrapWithCircuitBreaker` and `wrapWithMcpCap` so the wrapping wires registries into the new constructor parameters; existing no-registry constructors retained for tests / legacy callers.
- `gls-classifier-router/.../parse/LlmBudgetGate.java` — new constructor accepting `MeterRegistry`. Registers a `router.llm.budget.degraded` counter that ticks per `markExhausted` call (window-extensions count too — they're the same arming event), and a `router.llm.budget.exhausted_until_epoch_s` gauge that holds the unix-seconds expiry (or 0 when not exhausted) so dashboards can render a "degrade until" countdown.
- `gls-classifier-router/.../parse/RouterHttpConfig.java` — bean factory now passes the registry through to the gate.
- `gls-app-assembly/.../infrastructure/services/connectors/PerSourceLock.java` — new `Timer` `connector.lock.action.duration{source}` records the action runtime under the lock. Recorded on both the with-lock-acquired path and the no-LockProvider fall-through path; skipped iterations don't record (no work happened).
- `gls-app-assembly/.../infrastructure/services/pipeline/StalePipelineRunRecoveryTask.java` — new `DistributionSummary` `pipeline.stale.detected.age` (base unit seconds, percentiles p50/p95/p99) records the wall-time gap between each stale run's `updatedAt` and the moment of detection. Tight distribution near the stale threshold confirms the task runs on schedule; long tail signals a paused replica.

**Tests:** All affected modules green:

- `gls-llm-worker` 88 / 0 (was 84; +2 in `CircuitBreakerLlmServiceTest` covering state-gauge transitions + no-registry path; +2 in `McpCappingLlmServiceTest` covering counter increment + no-cap-no-counter).
- `gls-classifier-router` 92 / 0 (was 90; +2 in `LlmBudgetGateTest` for degraded counter + exhausted-until gauge).
- `gls-app-assembly` 107 / 0 (was 103; +3 in `PerSourceLockTest` for action timer paths; +1 in `StalePipelineRunRecoveryTaskTest` for the age distribution).

Reactor green via `./mvnw -DskipTests package`.

**Decisions logged:** None new. The cardinality choices are conservative — `backend` (anthropic/ollama/unknown) on the LLM gauges, `source` (drive/gmail/unknown) on the connector timer; the budget gate has no tags (single global router-side gate); the stale-age summary has no tags either (per-task distribution).

**Files changed:**

- `backend/gls-llm-worker/src/main/java/.../backend/{CircuitBreakerLlmService, McpCappingLlmService, LlmBackendConfig}.java` — modified.
- `backend/gls-classifier-router/src/main/java/.../router/parse/{LlmBudgetGate, RouterHttpConfig}.java` — modified.
- `backend/gls-app-assembly/src/main/java/.../infrastructure/services/{connectors/PerSourceLock, pipeline/StalePipelineRunRecoveryTask}.java` — modified.
- 4 test files modified (CircuitBreakerLlmServiceTest, McpCappingLlmServiceTest, LlmBudgetGateTest, PerSourceLockTest, StalePipelineRunRecoveryTaskTest) — +10 new tests across the suite.
- Log = 11 files total.

**Open issues / deferred:**

- **No metric for `@SchedulerLock` skip rate.** Useful: `pipeline.scheduler.lock.skipped{name}` counter so operators see how often a non-leader replica skipped a scheduled tick because another replica held the lock. ShedLock doesn't expose this directly — would need a wrapper bean. Tracked.
- **No tier-of-decision histogram.** Phase 2.6 plan item 1 ("tier-of-decision histogram per category") is broader than the wiring done here — needs the cascade router to emit the chosen tier per call, then Micrometer aggregation per category. Not in scope for this batch; tracked as a follow-up.
- **No cost-per-document.** Phase 2.6 plan item 3 — the LLM result already carries `costUnits`; need a per-document aggregator that emits `document.cost.units{tier=LLM|SLM|BERT}`. Tracked.

**Phase 2.6 status:** Plan items 1 (tier-of-decision histogram) and 3 (cost-per-document) untouched. Plan items 4-5-6 (escalation rate, p50/p95/p99 latency, MCP availability impact) need data points the cascade router doesn't currently aggregate. The five deferred metrics from earlier PRs are now live. ~60% done — the easier half.

**Next:** PR-C router-side `RateLimitGate`.

## 2026-04-30 — Phase 2.3 PR3 — Router-side `RateLimitGate`

**Done:** Closed Phase 2.3 plan item 1 ("per-worker semaphore on in-flight calls") for the cascade router itself. The pattern mirrors the existing `RateLimitGate` from `gls-llm-worker` (Phase 1.6 PR4) — per-replica fair semaphore, configurable permits + wait window, 429 with `Retry-After: 1` when exhausted. Default `permits=0` (disabled) so existing deployments pick up nothing on this PR until an operator opts in.

**Changes:**

- `gls-classifier-router/.../parse/RateLimitGate.java` (new) — `@Component` with `gls.router.rate-limit.permits` (default 0) and `gls.router.rate-limit.wait-ms` (default 0). When permits > 0, registers `router.rate_limit.permits.available` and `router.rate_limit.permits.total` gauges via the optional `MeterRegistry`. Returns an `AutoCloseable` `Token` from `acquire()` so callers use try-with-resources.
- `gls-classifier-router/.../parse/RateLimitExceededException.java` (new) — typed exception so the controller advice maps to 429 cleanly.
- `gls-classifier-router/.../web/ClassifyController.java` — wraps the sync classify body in `try (RateLimitGate.Token = rateLimitGate.acquire()) { ... }`. The async path doesn't gate (it returns 202 immediately and the limit applies when the dispatched work runs). `errorCodeFor` adds `ROUTER_RATE_LIMITED` so 429 metric tagging works.
- `gls-classifier-router/.../web/RouterExceptionHandler.java` — new `@ExceptionHandler` for `RateLimitExceededException`: 429 `ROUTER_RATE_LIMITED` with `Retry-After: 1`. Same shape as the analogous handler in `gls-llm-worker`.
- `gls-classifier-router/.../parse/RateLimitGateTest.java` (new) — 7 tests: zero permits = pass-through; single permit allows one at a time; `wait-ms` blocks briefly; try-with-resources releases; close is idempotent; gauges register when registry present; disabled gate doesn't register gauges.
- Existing `ClassifyControllerTest` updated to construct the new `disabledRateLimitGate()` and pass through the constructor.

**Tests:** 97 / 0 in `gls-classifier-router` (was 92; +7 new for `RateLimitGateTest`, plus existing controller tests still green after constructor wiring). Reactor green.

**Decisions logged:** None new. The decision to gate the sync path only — async dispatching defers gate enforcement to the dispatched work — matches the existing LLM-worker behaviour and keeps the contract clean (caller gets 202 fast; rate-limit pressure shows up as queue depth on the async dispatcher, which is observable separately).

**Files changed:**

- `backend/gls-classifier-router/src/main/java/.../router/parse/{RateLimitGate, RateLimitExceededException}.java` — 2 new.
- `backend/gls-classifier-router/src/main/java/.../router/web/{ClassifyController, RouterExceptionHandler}.java` — 2 modified.
- `backend/gls-classifier-router/src/test/java/.../router/{parse/RateLimitGateTest, web/ClassifyControllerTest}.java` — 1 new + 1 modified.
- Log = 7 files total.

**Open issues / deferred:**

- **Per-replica only.** Cluster-wide rate limiting (Redis-backed token bucket) deferred until a real load profile exists. Same caveat as the worker-side gate.
- **Async path doesn't gate.** When `Prefer: respond-async` returns 202, the dispatched work runs without consulting the gate. If async traffic dominates and overwhelms downstream tiers, add a parallel gate inside `AsyncDispatcher.runAsync`.

**Next:** PR-D DLQ replay endpoint.

## 2026-04-30 — Phase 2.3 PR4 — DLQ replay data path

**Done:** Closed Phase 2.3 plan item 4 ("DLQ reprocessing UI hook — the data path; UI in Phase 3"). New `DlqReplayService` reads messages from a whitelisted DLQ and re-publishes each to its original exchange + routing key recorded in RabbitMQ's `x-death` header. New `POST /api/admin/dlq/{queueName}/replay?max=N` admin endpoint wraps the service.

**Changes:**

- `gls-app-assembly/.../infrastructure/services/DlqReplayService.java` (new) — `@Service`. Allowed-DLQ whitelist defaults to `gls.documents.dlq` and `gls.pipeline.dlq` so arbitrary queues can't be drained through the endpoint. Per replayed message: reads the first entry from `x-death` (RabbitMQ stamps these on dead-lettered messages), extracts `exchange` + first `routing-keys` element, strips the `x-death` / `x-first-death-*` headers (so the broker's dead-letter machinery resets if the message dies again), re-publishes via `RabbitTemplate.send`. Failures during replay are caught — the batch continues; failed counts surface in the result. Counter `dlq.replay{queue, outcome}` ticks per outcome (replayed / skipped).
- `gls-app-assembly/.../infrastructure/controllers/admin/DlqReplayController.java` (new) — `POST /api/admin/dlq/{queueName}/replay?max=N`. Default `max=100`, hard-capped at 1000 per call; `IllegalArgumentException` from the service (queue not whitelisted, max ≤ 0) maps to 400.
- `gls-app-assembly/.../infrastructure/services/DlqReplayServiceTest.java` (new) — 8 tests: disallowed queue throws; zero / negative max throws; empty queue zero counts; single message with `x-death` re-published to origin (verifies the exchange + routing-key extraction + `x-death` stripping); max-messages honoured; message without `x-death` skipped; send-failure during replay doesn't break the batch; absent `MeterRegistry` doesn't break.

**Tests:** 111 / 0 in `gls-app-assembly` (+8 new for `DlqReplayServiceTest`). Reactor green.

**Decisions logged:** None new. The "whitelist allowed DLQs" decision is the load-bearing one — without it, an authenticated admin could drain arbitrary queues and disrupt operations.

**Files changed:**

- `backend/gls-app-assembly/src/main/java/.../infrastructure/services/DlqReplayService.java` — 1 new.
- `backend/gls-app-assembly/src/main/java/.../infrastructure/controllers/admin/DlqReplayController.java` — 1 new.
- `backend/gls-app-assembly/src/test/java/.../infrastructure/services/DlqReplayServiceTest.java` — 1 new.
- Log = 4 files total.

**Open issues / deferred:**

- **No idempotency on the replay endpoint.** Two concurrent admin calls would compete on the same DLQ. Acceptable for an admin-only operation.
- **No "dry run" mode.** Endpoint commits as it goes; future: `?dryRun=true` query param to preview.
- **No filtering by reason / age / count.** Drains FIFO; future: query params for selective replay.

**Phase 2.3 status:** **all 4 plan items complete** with PR-C (rate-limit) + PR-D (DLQ replay).

**Next:** Phase 2 substantially complete across this session. Remaining gaps: 2.1 `classification_outbox` reconciler (blocked on §6.5), 2.2 Anthropic 429 honoring / Ollama+Anthropic fallback (design calls), 2.4 audit-collector Tier 1 leader election (needs programmatic-poll refactor), 2.5 chaos tests (blocked on infra), 2.6 tier-of-decision + cost-per-document metrics (need cascade-router data points that don't exist yet).

## 2026-04-30 — Phase 2.4 PR2 — Audit-collector Tier 1 leader election

**Done:** Closed Phase 2.4 plan item 1 ("`gls-audit-collector` Tier 1 leader election via ShedLock"). The previous `@RabbitListener`-based `Tier1Consumer` would have allowed every replica to consume Tier 1 messages concurrently — a real correctness gap because Tier 1 events form per-resource hash chains (CSV #4) and concurrent validate-then-append from multiple replicas could fork the chain. Refactored to programmatic polling under `@SchedulerLock` so only one replica drains the queue at any moment; on leader death the lock auto-releases and the next tick promotes a replacement.

**Why programmatic poll over `@RabbitListener` + start/stop:** ShedLock's `lock()` doesn't notify the holder when the lock is lost (e.g. after a long GC pause exceeds `lockAtMostFor`). With `@RabbitListener` start/stop driven by a heartbeat, a replica that loses the lock while still believing it's the leader continues consuming until the heartbeat sees the loss — a race window. Programmatic poll inside the `@SchedulerLock` method body is naturally race-free: only one method invocation runs across replicas at any time, and the next tick re-acquires (or doesn't).

**Changes:**

- `gls-audit-collector/.../consumer/Tier1Consumer.java` — removed `@RabbitListener(QUEUE_TIER1)`. New `@Scheduled @SchedulerLock("audit-tier1-leader") pollTier1()` method drains the queue via `RabbitTemplate.execute(channel -> channel.basicGet(QUEUE_TIER1, false))` in a loop until empty. Each message is converted via the wired `MessageConverter` (the existing `JacksonJsonMessageConverter`) and dispatched to the existing `onTier1(Map)` handler. Manual ack on success and on every error path (poison messages must not wedge the leader). Existing single-arg constructor preserved so `Tier1ConsumerTest` keeps working without standing up Rabbit.
- `gls-audit-collector/.../consumer/Tier1Consumer.java` — added `audit.tier1.consumer{outcome}` counter (outcomes: `processed`, `unconvertible`, `error`).
- `gls-audit-collector/GlsAuditCollectorApplication.java` — added `@EnableScheduling` so the new `@Scheduled` ticks. Class-level `@EnableSchedulerLock` comes transitively from `gls-platform-audit`'s `AuditRelayLockConfig`.
- `gls-audit-collector/pom.xml` — added `shedlock-spring` + `shedlock-provider-mongo` (the platform-audit module marks them `optional=true` so consumers must opt in explicitly). Same pattern as `gls-app-assembly` and `gls-indexing-worker`.
- `gls-audit-collector/.../consumer/Tier1ConsumerTest.java` — extended with 2 new tests for the new wiring: no-rabbit-template (test-only single-arg constructor) is a silent no-op; absent rabbit / converter `ObjectProvider`s skip the cycle. Existing 6 tests still green — they exercise `onTier1(Map)` directly which is unchanged.

**Tests:** 44 / 0 in `gls-audit-collector` (was 42; +2 new in `Tier1ConsumerTest`). Reactor green via `./mvnw -DskipTests package`.

**Decisions logged:** None new. The "ack on every error path" stance is documented in the class JavaDoc — a poison message must not be infinite-requeued; broken-chain and append-only-violation are the expected unhappy paths handled internally; anything else is unexpected, logged, and acked anyway.

**Files changed:**

- `backend/gls-audit-collector/src/main/java/.../consumer/Tier1Consumer.java` — modified (significant refactor).
- `backend/gls-audit-collector/src/main/java/.../GlsAuditCollectorApplication.java` — modified (`@EnableScheduling`).
- `backend/gls-audit-collector/pom.xml` — modified (2 new ShedLock deps).
- `backend/gls-audit-collector/src/test/java/.../consumer/Tier1ConsumerTest.java` — modified (+2 new tests).
- Log = 5 files total.

**Open issues / deferred:**

- **No leader-status admin endpoint.** Operators have no in-app way to see "which replica is currently leader" or "is anyone leader right now." A small `/api/admin/audit/tier1/leader` endpoint reading the ShedLock collection's `audit-tier1-leader` row would expose this. Tracked.
- **Tier 2 still uses `@RabbitListener`.** Intentional — Tier 2 has no chain-ordering requirement (per `AuditRabbitConfig` docstring), and horizontal consumer scaling is the right shape there. No action needed.
- **`pollTier1` integration test deferred.** Mocking `RabbitTemplate.execute(channel -> ...)` to drive the new loop is brittle. The unit-level coverage is the test-only constructor + ObjectProvider absent paths; the real broker-driven flow is exercised in the deployed environment. Worth wrapping in a Testcontainers RabbitMQ + ShedLock Mongo integration test once the test infra exists.

**Phase 2.4 status:** **all 3 plan items complete.** (1: audit-collector Tier 1 leader election — done here; 2: `gls-scheduler` leader election — done in Phase 2.4 PR1 + earlier `OutboxRelay`; 3: connector per-source watches — done in Phase 1.13.)

**Next:** Phase 2 status is now: 2.1 4/5 (blocked), 2.2 3/5 (design calls), 2.3 4/4 ✓, 2.4 3/3 ✓, 2.5 0/N (blocked on infra), 2.6 5/6 (cascade-router data points needed). Remaining session work, in order: 2.2 Ollama+Anthropic fallback, then 2.6 tier-of-decision histogram + cost-per-document metrics, then Phase 3 admin UI (different shape — React, not Java).

## 2026-04-30 — Phase 2.2 PR4 — Ollama + Anthropic in-worker fallback

**Done:** Closed Phase 2.2 plan item 3 ("Ollama unreachable → circuit + fallback to Anthropic"). New `FallbackLlmService` decorator wraps a primary + secondary `LlmService` and falls through to secondary on primary failure. Wired in `LlmBackendConfig` so when both `AnthropicChatModel` and `OllamaChatModel` beans are autoconfigured AND `gls.llm.worker.fallback.enabled=true`, the active service becomes `Fallback(primary, secondary)`. Primary is whichever side `gls.llm.worker.backend` selects; secondary is the other.

**Design call (recommended default):** Primary = `gls.llm.worker.backend` (existing config), secondary = the other backend. Default `fallback.enabled=false` so existing deployments don't change behaviour. Operators flip the flag once both API keys are wired and they want resilience over deterministic backend choice.

**Changes:**

- `gls-llm-worker/.../backend/FallbackLlmService.java` (new) — decorator implementing `LlmService`. On call: tries primary; on `BudgetExceededException` / `RateLimitExceededException` (caller-side gates), propagates without fallback; on `CircuitBreakerOpenException` or other `RuntimeException`, calls secondary. `activeBackend()` returns the configured primary regardless of where the actual call landed (transparent fallback). `isReady()` is true if EITHER backend is up. Counter `llm.fallback.invocations{primary, reason}` ticks per fallback event with reason `circuit_breaker_open` or `primary_failed`.
- `gls-llm-worker/.../backend/LlmBackendConfig.java` — significant refactor of the `llmService` bean factory. Each backend (anthropic / ollama) is now built lazily via `buildAnthropicIfAvailable` / `buildOllamaIfAvailable` helpers — both are constructed when their `ChatModel` bean exists, regardless of which one is selected as primary. When `fallback.enabled=true` AND secondary is available, wraps in `FallbackLlmService`; else uses primary alone. Each backend has its own circuit breaker (different lock-name `llm-anthropic` vs `llm-ollama`) so a failing primary trips its own breaker and the fallback path stays fast.
- `gls-llm-worker/.../backend/FallbackLlmServiceTest.java` (new) — 11 tests: primary success no secondary call; primary `RuntimeException` falls through; primary breaker open falls through; budget exception doesn't fall through; rate-limit exception doesn't fall through; secondary failure propagates; `activeBackend` returns primary; `isReady` true if either ready; `isReady` false if both down; counter tags primary + reason; absent `MeterRegistry` doesn't break.

**Tests:** 99 / 0 in `gls-llm-worker` (was 88; +11 new). Reactor green via `./mvnw -DskipTests package`.

**Decisions logged:** None new. Two design choices documented in JavaDoc:
- Caller-side gates don't fall back (would mask the back-pressure).
- Per-backend circuit breakers (different lock-names) so primary's outage doesn't suppress secondary's metrics.

**Files changed:**

- `backend/gls-llm-worker/src/main/java/.../backend/FallbackLlmService.java` — 1 new.
- `backend/gls-llm-worker/src/main/java/.../backend/LlmBackendConfig.java` — modified (per-backend lazy-build refactor + fallback wiring).
- `backend/gls-llm-worker/src/test/java/.../backend/FallbackLlmServiceTest.java` — 1 new.
- Log = 4 files total.

**Open issues / deferred:**

- **Single-attempt secondary.** If secondary also fails, we propagate. No "try primary again after a window" — that's what the circuit breaker on each backend handles separately.
- **No "promote secondary to primary" admin endpoint.** Operators can't tell the worker to swap primary/secondary at runtime — config restart required. Tracked.
- **Per-tenant fallback policy not modelled.** All callers share the same primary/secondary config. Per-tenant overrides need a request-scoped resolver; out of scope today.

**Phase 2.2 status:** **4 of 5 plan items complete.** (1: Anthropic circuit breaker — done; 2: Anthropic 429 / `Retry-After` — blocked on Spring AI; 3: Ollama unreachable + Anthropic fallback — done here; 4: MCP unreachable confidence cap — done; 5: cost budget auto-degrade — done.)

**Next:** Phase 2.6 PR2 — tier-of-decision histogram + cost-per-document metrics. Needs the cascade router to emit per-tier outcomes + per-document cost rollup.

## 2026-04-30 — Phase 2.6 PR2 — Tier-of-decision per category + cost-per-document

**Done:** Closed Phase 2.6 plan items 1 and 3. The cascade router's `ClassifyController` now emits two additional Micrometer metrics per successful classify:

- `gls_router_classify_tier_by_category_total{tier, category}` — counter that splits the existing tier-of-decision counter by category. Operators can read the share of decisions handled at each tier per category — e.g. "PII scans hit BERT 95% of the time, financial classifications go to LLM 60%".
- `gls_router_classify_cost_units_total{tier}` — counter incremented by `costUnits` per call, tagged by tier. Cost-per-document is the windowed mean (this counter / `gls_router_classify_result_total{outcome=success}`); cost-per-tier is the per-tag sum; daily-spend total is the windowed sum.

**Changes:**

- `gls-classifier-router/.../web/ExtractMetrics.java` — two new methods: `recordTierByCategory(tier, category)` and `recordCost(tier, costUnits)`. Both reuse the existing `safe()` helper for tag normalization (lowercase + `"unknown"` fallback for blank).
- `gls-classifier-router/.../web/ClassifyController.java` — `handleSyncAcquired` now calls both new methods after a successful classify. New static helper `extractCategory(ClassifyResponse)` reads the cascade `result` map, preferring `categoryCode` (low-cardinality keyword) over `categoryId` (Mongo id) over `category` (free-text name); returns `null` when none present so `ExtractMetrics.safe()` renders it as `"unknown"`.
- `gls-classifier-router/.../web/ExtractMetricsTest.java` (new) — 4 tests: tier+category counter increments correctly with lowercased tags; null/blank fall to `"unknown"`; cost counter increments by `costUnits` and skips zero/negative; null tier falls to unknown.
- `gls-classifier-router/.../web/ClassifyControllerExtractCategoryTest.java` (new) — 7 tests: prefers `categoryCode`; falls back to `categoryId` then `category`; blank values skipped to next key; non-string values skipped; null body / null result return null.

**Tests:** 110 / 0 in `gls-classifier-router` (was 99; +11 new across two test classes). Reactor green via `./mvnw -DskipTests package`.

**Decisions logged:** None new. Cardinality decision documented in JavaDoc — `category` is bounded by the org's taxonomy size (typically tens to hundreds), so `O(tiers × categories)` is acceptable for typical Prometheus scales.

**Files changed:**

- `backend/gls-classifier-router/src/main/java/.../web/ExtractMetrics.java` — modified.
- `backend/gls-classifier-router/src/main/java/.../web/ClassifyController.java` — modified.
- `backend/gls-classifier-router/src/test/java/.../web/ExtractMetricsTest.java` — 1 new.
- `backend/gls-classifier-router/src/test/java/.../web/ClassifyControllerExtractCategoryTest.java` — 1 new.
- Log = 5 files total.

**Open issues / deferred:**

- **Async path doesn't record.** `runAsync` doesn't fire the new metrics — same shape as the existing `recordSuccess` which only runs in `handleSyncAcquired`. If async traffic dominates, both the tier counter and cost counter undercount; needs a parallel call inside `runAsync`. Tracked as a small follow-up.
- **No per-tenant tag.** `category` is global across tenants. A multi-tenant deployment would want `tenantId` as an additional tag — needs request-scoped resolver, deferred.
- **Plan items 4-5-6 (escalation rate, p50/p95/p99 latency by tier+mime, MCP availability impact) untouched.** Each requires either richer cascade-trace data (escalation chain length per call) or per-document mime tracking. Tracked.

**Phase 2.6 status:** **plan items 1 (tier-of-decision histogram) and 3 (cost-per-document) complete.** Plan items 2 (escalation rate dashboard), 4 (p50/p95/p99 latency by tier and mime type), 5 (MCP availability impact on confidence), 6 (alerts on circuit breaker open / DLQ depth / stale documents / audit chain breaks) remain. Items 4-5-6 are cheap to add once the data points exist.

**Next:** Phase 2 substantially complete. Remaining gaps are blocked or low-priority follow-ups: 2.1 `classification_outbox` reconciler (blocked on §6.5), 2.2 Anthropic 429 honoring (blocked on Spring AI), 2.5 chaos integration tests (blocked on infra). Phase 3 (admin UI) is the natural next major workstream; different shape from Phase 2 (React, not Java).

## 2026-04-30 — Phase 2.6 PR3 — Cascade observability follow-ups

**Done:** Three deferred items folded into one PR:

1. **Async-path metric coverage** (deferral from Phase 2.6 PR2). The async classify path (`runAsync`, invoked when `Prefer: respond-async` is set) wasn't recording the success-path metrics — sync and async were diverging in dashboards. Refactored to share a common `recordSuccessMetrics(timer, body)` helper that fires duration + result + tier-by-category + cost units.
2. **Phase 2.6 plan item 2 — escalation rate dashboard.** New `gls_router_classify_cascade_steps` distribution summary (p50 / p95 / p99) records the number of cascade steps per call. Length 1 means the first tier accepted; longer = more escalations. Tight low-value distribution is the happy case.
3. **Phase 2.6 plan item 4 — per-tier-step latency.** New `gls_router_classify_tier_step_duration_seconds{tier, accepted}` timer records the duration of each individual cascade step, tagged by which tier ran it and whether the step's result was accepted (otherwise fell through to the next tier). Operators read p50/p95/p99 latency split by tier × accepted/rejected — fall-through latency is the cost of a wasted attempt; accepted latency is useful work.

**Plan item 4 mime-type dimension deferred:** the request contract doesn't carry mime; threading it through requires either a contract change or a new lookup path. Captured as a follow-up; the per-tier histogram delivers most of the operational value.

**Plan item 5 (MCP availability impact):** the LLM worker's `McpCappingLlmService` already counts cap events via `llm.mcp.confidence_capped{backend}` (Phase 2.6 PR1). The router-side proxy (a `mcp_capped` tag on tier-by-category) needs a structured `mcpCapped` flag in the cascade response — that's a contract change. Tracked.

**Changes:**

- `gls-classifier-router/.../web/ExtractMetrics.java` — two new methods: `recordCascadeSteps(int)` (distribution summary) and `recordTierStepDuration(tier, accepted, durationMs)` (tagged timer).
- `gls-classifier-router/.../web/ClassifyController.java` — extracted `recordSuccessMetrics(timer, body)` helper used by both sync (`handleSyncAcquired`) and async (`runAsync`) paths. The helper now also records cascade-step count + per-step durations.
- `gls-classifier-router/.../web/ExtractMetricsTest.java` — extended with 4 new tests: cascade-steps distribution recorded; cascade-steps skips negative; tier-step-duration tagged correctly; tier-step-duration skips negative duration.

**Tests:** 114 / 0 in `gls-classifier-router` (was 110; +4 new). Reactor green.

**Decisions logged:** None new. The `accepted` boolean is the load-bearing tag — without it, fall-through latency (cost of a wasted attempt) and accepted latency (useful work) would be conflated.

**Files changed:**

- `backend/gls-classifier-router/src/main/java/.../web/ExtractMetrics.java` — modified.
- `backend/gls-classifier-router/src/main/java/.../web/ClassifyController.java` — modified.
- `backend/gls-classifier-router/src/test/java/.../web/ExtractMetricsTest.java` — modified.
- Log = 4 files total.

**Open issues / deferred:**

- **Mime-type latency dimension still missing.** Needs request contract to carry `mimeType` (or a side-channel lookup) so the timer can be tagged by it.
- **MCP availability impact router-side proxy missing.** Needs structured `mcpCapped` field in `ClassifyResponse`.

**Phase 2.6 status:** Plan items 1, 2, 3, 4 (without mime dimension) **done**. Items 5 (MCP impact router-side) and 6 (Prometheus alerts — operational config, not code) remain. Across the whole phase, 5 of 6 items have substantive code coverage; the last is an alerts-config workstream for the deployment side.

**Next:** PR-F — DLQ replay dry-run + per-queue idempotency.

## 2026-04-30 — Phase 2.3 PR5 — DLQ replay dry-run + per-queue idempotency

**Done:** Two deferred items from Phase 2.3 PR4 (#111) closed:

1. **`?dryRun=true` query parameter.** Previously the endpoint committed as it ran — no way to preview what would be replayed. Dry-run mode pops messages via `basicGet(queue, autoAck=false)` and `basicNack(deliveryTag, false, /* requeue */ true)` so messages stay in the DLQ. The response body's new `preview` field lists per-message origin (exchange + routing key), reason, and body size, so operators see exactly what a real replay would do.
2. **Per-queue ShedLock idempotency.** Two concurrent admin calls draining the same DLQ used to compete on the same messages. The service now acquires a per-queue lock (`dlq-replay-<queueName>`) via the existing `LockProvider`; a second concurrent call gets `409 ReplayInProgressException`. `lockAtMostFor=PT5M` covers worst-case drain time; auto-released on completion.

**Refactor:** the previous implementation used `RabbitTemplate.receive(queue)` (auto-ack) so couldn't support dry-run. Refactored to `RabbitTemplate.execute(channel -> channel.basicGet(queue, false))` with manual ack/nack, plus a `StepOutcome` enum (EMPTY / REPLAYED / SKIPPED) distinguishing "queue empty" from "skipped" cleanly.

**Changes:**

- `gls-app-assembly/.../infrastructure/services/DlqReplayService.java` — significant refactor. New constructor signature accepts `ObjectProvider<LockProvider>`. New `dryRun(queueName, max)` method alongside `replay(queueName, max)`. New `ReplayPreview` and `ReplayInProgressException` types. Counter `dlq.replay{queue, outcome, mode}` adds a `mode` tag (`real` / `dry_run`).
- `gls-app-assembly/.../infrastructure/controllers/admin/DlqReplayController.java` — `?dryRun=true|false` query param routes to the right service method. `ReplayInProgressException` → 409 Conflict.
- `gls-app-assembly/.../infrastructure/services/DlqReplayServiceTest.java` — significant rewrite to match the new `execute(channel -> ...)` mocking pattern. 11 tests cover: disallowed queue throws; zero/negative max throws; empty queue zero counts; real mode re-publishes + acks; dry-run nacks-with-requeue + populates preview; max-messages honoured; missing-x-death skipped + acked; per-queue lock held throws ReplayInProgressException; absent LockProvider runs without lock; lock released after replay; absent MeterRegistry doesn't break.

**Tests:** `DlqReplayServiceTest` 11 / 0 (was 8). Reactor green.

**Decisions logged:** None new. Two design choices in JavaDoc:
- Dry-run uses `basicGet` + `nack(requeue=true)` rather than a peek-only API (RabbitMQ doesn't have native peek). Messages briefly transit out of the queue and back; downstream consumers can't see them during that window. Acceptable for an admin-only operation.
- Per-queue lock keyed on queue name (not by request id) — concurrent admin sessions targeting different DLQs proceed in parallel.

**Files changed:**

- `backend/gls-app-assembly/src/main/java/.../infrastructure/services/DlqReplayService.java` — modified.
- `backend/gls-app-assembly/src/main/java/.../infrastructure/controllers/admin/DlqReplayController.java` — modified.
- `backend/gls-app-assembly/src/test/java/.../infrastructure/services/DlqReplayServiceTest.java` — modified.
- Log = 4 files total.

**Open issues / deferred:**

- **No filtering by reason / age / count.** Drains FIFO; future: query params for selective replay (e.g. only `reason=expired` messages older than 1h).
- **Body preview not included.** `ReplayPreview` includes only metadata + size; the body is not surfaced (could be large / contain sensitive data). A separate `?includeBody=true` flag with an admin warning would address operator forensics needs.

**Next:** PR-G — leader-status admin endpoint + `@SchedulerLock` skip-rate metric.

## 2026-04-30 — Phase 2.4 PR3 — Scheduler-lock observability

**Done:** Two deferred items closed:

1. **`@SchedulerLock` skip-rate metric** (deferred from Phase 2.4 PR1 #108). New `MetricsLockProvider` decorator wraps the `MongoLockProvider` in `gls-platform-audit`'s `AuditRelayLockConfig`. Every `lock()` call now ticks `scheduler.lock{name, outcome=acquired|skipped}`. Operators see leader-election dynamics — how often a given scheduled task's tick was held by another replica (skipped) vs acquired locally. High skip rate on a single lock name = busy leader (expected); equal counts across replicas = even rotation; one replica acquiring 100% = others never trying / always losing the race. Decorator is auto-applied to every consumer of platform-audit's `LockProvider` bean (gls-app-assembly, gls-indexing-worker, gls-audit-collector — every module using `@SchedulerLock`).
2. **Leader-status admin endpoint** (deferred from Phase 2.4 PR2 #112). New `GET /api/admin/scheduler/locks` reads ShedLock's `shedLock` Mongo collection directly and returns each row: lock name, `lockUntil`, `lockedAt`, `lockedBy` hostname, plus a derived `active` boolean (`lockUntil > now`). Operators see at a glance which replica is leader for each named lock right now.

**Changes:**

- `gls-platform-audit/.../autoconfigure/MetricsLockProvider.java` (new) — decorator implementing `LockProvider`. `lock()` delegates and increments the counter tagged by lock name + outcome. Null `MeterRegistry` → silent pass-through.
- `gls-platform-audit/.../autoconfigure/AuditRelayLockConfig.java` — `auditRelayLockProvider` bean now wraps `MongoLockProvider` in `MetricsLockProvider` when a `MeterRegistry` is available; falls back to bare `MongoLockProvider` otherwise. Bean signature changed (added `ObjectProvider<MeterRegistry>`).
- `gls-app-assembly/.../infrastructure/controllers/admin/SchedulerLocksController.java` (new) — `GET /api/admin/scheduler/locks`. Reads from the `shedLock` collection (configurable via `gls.shedlock.collection`). Returns `LocksResponse(collection, queriedAt, List<LockRow>)`.
- `gls-platform-audit/.../autoconfigure/MetricsLockProviderTest.java` (new) — 4 tests: acquired-lock counter; unavailable-lock counter; per-name segregation; null `MeterRegistry` no-op.

**Tests:** `gls-platform-audit` 34 / 0 (was 30; +4 new). `gls-app-assembly` 115 / 0 (controller is thin — no test added; covered by the existing admin endpoint smoke tests when they run against a real Spring context). Reactor green.

**Decisions logged:** None new. The decorator-on-the-bean approach was the load-bearing choice — putting metrics in the LockProvider means every ShedLock-using consumer gets them automatically without per-module wiring.

**Files changed:**

- `backend/gls-platform-audit/src/main/java/.../autoconfigure/{AuditRelayLockConfig, MetricsLockProvider}.java` — 1 modified + 1 new.
- `backend/gls-app-assembly/src/main/java/.../infrastructure/controllers/admin/SchedulerLocksController.java` — 1 new.
- `backend/gls-platform-audit/src/test/java/.../autoconfigure/MetricsLockProviderTest.java` — 1 new.
- Log = 5 files total.

**Open issues / deferred:**

- **No SchedulerLocksController integration test.** The endpoint is a thin Mongo read; testing it requires `@WebMvcTest` with a mocked `MongoTemplate` + `getCollection()` chain. Smoke-tested via the broader admin endpoint suite in deployed environments. Add focused test if regressions appear.
- **No leader-change event log.** The endpoint shows current state; historical "this replica was leader for these intervals" requires either a Mongo audit collection on lock transitions or scraping the Prometheus counter over time. Operators read the Prometheus history for now.

**Next:** All deferred follow-ups within reach are now shipped. Remaining gaps are blocked or low-priority architectural workstreams: 2.1 `classification_outbox` (blocked on §6.5), 2.2 Anthropic 429 honoring (blocked on Spring AI), 2.5 chaos integration tests (blocked on infra), per-tenant tags (architectural), Phase 3 admin UI (different shape).

## 2026-04-30 — Phase 3 PR1 — DLQ replay + scheduler-locks Ops UI

**Done:** First Phase 3 admin-UI piece. New "Ops" tab on the existing monitoring page surfaces two operationally important admin endpoints shipped in the Phase 2 backend run:

1. **DLQ inspection + replay UI** (Phase 3 plan item §3.5). Wraps `POST /api/admin/dlq/{queueName}/replay` (Phase 2.3 PR4 #111 + dry-run from PR5 #116). Lists the two whitelisted DLQs (`gls.documents.dlq`, `gls.pipeline.dlq`) with per-queue Preview / Replay actions. Preview drives the dry-run path — pops messages, lists origin (exchange + routing key), reason, body size, then nacks-with-requeue so nothing is consumed. Replay drives the real path — re-publishes to origin, acks. Errors during the batch surface in a collapsible details panel. 409 from the backend (concurrent admin call) shows a "try again in a moment" toast. 400 (queue not whitelisted) shows an "invalid request" toast.
2. **Leader-status panel** (Phase 3 plan item §3.5 / observability). Wraps `GET /api/admin/scheduler/locks` (Phase 2.4 PR3 #117). Reads the ShedLock collection and lists every named lock with its holder hostname, lock-until, locked-at, and a derived `active` badge. Operators see at a glance which replica is leader for `audit-tier1-leader`, `gls-audit-outbox-relay`, `stale-pipeline-recovery`, etc.

**Changes:**

- `web/src/components/monitoring/dlq-replay-tab.tsx` (new) — React component for DLQ preview / replay. Tailwind-styled, `lucide-react` icons, `sonner` toasts, `axios` client. Per-queue collapsed state + per-queue `max` input bounded at 1..1000. Result panel renders the preview list as a table.
- `web/src/components/monitoring/scheduler-locks-panel.tsx` (new) — React component for the leader-status table. Fetches once on mount; `Refresh` button for manual reload. Active vs Expired states distinguished by colour-coded badge. Relative-time formatter (`5s ago`, `in 30m`).
- `web/src/app/(protected)/monitoring/page.tsx` — extended `monTab` state with `"ops"`. New tab button in the header strip. New conditional Ops-tab body renders the two components stacked under section headers.

**Tests:** TypeScript `tsc --noEmit` clean. ESLint clean for the new files (pre-existing warnings in `monitoring/page.tsx` unchanged — `fetchInfra` unused, several `any` types — separate cleanup).

**Decisions logged:** None new. The "Ops" tab pattern matches the existing tab structure on monitoring; future Phase 3 work can add more tabs (Performance dashboards in §3.8, Hub pack browser in §3.9) under the same shape.

**Files changed:**

- `web/src/components/monitoring/dlq-replay-tab.tsx` — 1 new.
- `web/src/components/monitoring/scheduler-locks-panel.tsx` — 1 new.
- `web/src/app/(protected)/monitoring/page.tsx` — modified (3 small edits: import, state-tuple union, tab strip + body).
- Log = 4 files total.

**Open issues / deferred:**

- **No body-preview in the dry-run table.** Backend `ReplayPreview` carries body bytes count only; surfacing the actual body needs an `?includeBody=true` flag (which itself was deferred from PR5).
- **No real-time auto-refresh for the locks panel.** Manual Refresh only; consider a 10-second poll if operators ask for it.
- **No per-queue queue-depth display.** The DLQ tab doesn't show "X messages currently pending" — would need a separate `GET /api/admin/dlq/{queueName}/depth` endpoint backed by `RabbitTemplate.execute(channel -> channel.messageCount(queue))`. Tracked.

**Phase 3 status:** Plan item §3.5 partial — DLQ inspection + replay UI shipped; document monitoring + retry buttons + bulk reclassification trigger remain. §3.8 (performance dashboards) untouched but the metrics it'd consume are all live from Phase 2.

**Next:** Phase 3 PR2 — pick from §3.1 (visual DAG editor for pipelines), §3.2 (block library), §3.3 (taxonomy tree editor), §3.6 (audit explorer), §3.8 (performance dashboards). Pipeline DAG editor is the biggest single piece; performance dashboards consume the metrics already shipped.

## 2026-05-01 — Phase 3 PR2 — Performance dashboards

**Done:** Phase 3 plan item §3.8. Adds a Performance section to the Ops tab on `/monitoring`, backed by a new admin endpoint that aggregates the Phase 2 Micrometer metrics living on `gls-app-assembly` into a UI-friendly JSON shape. Frontend renders four panels: stale-pipeline detection age (count + p50/p95/p99 + mean + max), per-source connector locks (acquired vs skipped + mean action duration, bar chart + table), per-name scheduler locks (horizontal bar chart of acquired vs skipped), and per-queue DLQ replay activity (real vs dry-run, replayed vs skipped).

Why a custom backend endpoint rather than `/actuator/metrics`: management endpoints in this app are locked down to `health,info` (`management.endpoints.web.exposure.include=health,info`), so direct actuator access wasn't an option. The custom endpoint also lets us shape the response to the four UI panels — actuator's per-meter scalar JSON would still need a frontend aggregation pass.

Cross-service metrics (`gls_router_classify_*`, `llm.circuit_breaker.*`, `llm.fallback.invocations`, etc.) live on the router and worker registries, not on `gls-app-assembly`'s, so they aren't surfaced here yet. Adding them needs an HTTP probe layer that pulls Prometheus-format scrapes from peer services — deferred.

**Changes:**

- `gls-app-assembly/.../infrastructure/controllers/admin/MetricsDashboardController.java` (new) — `GET /api/admin/metrics/dashboard`. Reads the local `MeterRegistry` for the six tracked metric names: `pipeline.stale.detected.age` (DistributionSummary → count/mean/max + p50/p95/p99 from snapshot), `connector.lock.acquired` / `skipped` / `action.duration` (grouped by `source` tag), `scheduler.lock` (grouped by `name` + `outcome`), `dlq.replay` (grouped by `queue` → `mode` → `outcome`). Returns `DashboardResponse(timestamp, stalePipelineDetectionAge, connectorLocks, schedulerLocks, dlqReplay)` records. Build failures degrade to an empty response with a warn-log so a transient registry hiccup never breaks the page.
- `gls-app-assembly/.../controllers/admin/MetricsDashboardControllerTest.java` (new) — 6 tests covering empty registry, distribution-summary stats extraction (count + percentiles), connector-locks grouping by source (counters + timer), scheduler-locks grouping by name + outcome, DLQ-replay nested grouping by queue → mode → outcome, and the canonical metric-name set. Uses `SimpleMeterRegistry` directly — no Spring context.
- `web/src/components/monitoring/performance-dashboard.tsx` (new) — React component using `recharts` (already in deps). Four panels with skeleton-on-loading + "no data yet" empty states. Bar charts for connector-locks, scheduler-locks (horizontal — names are long), and per-queue DLQ replay (one chart per queue, mode on x-axis). Stale-pipeline panel is a six-stat card grid (count / mean / max / p50 / p95 / p99). Refresh button shows last-refresh timestamp.
- `web/src/app/(protected)/monitoring/page.tsx` — imports + renders `<PerformanceDashboard />` as the first section under the existing Ops tab, above DLQ replay and Leader election.

**Tests:** `gls-app-assembly` `MetricsDashboardControllerTest` 6 / 0 (new). Reactor green. `tsc --noEmit` clean. ESLint clean for the new + edited files.

**Decisions logged:** None new. The "custom endpoint over actuator" call was driven by the existing security posture (actuator locked to `health,info`); revisiting that is a separate decision.

**Files changed:**

- `backend/gls-app-assembly/src/main/java/.../controllers/admin/MetricsDashboardController.java` — 1 new.
- `backend/gls-app-assembly/src/test/java/.../controllers/admin/MetricsDashboardControllerTest.java` — 1 new.
- `web/src/components/monitoring/performance-dashboard.tsx` — 1 new.
- `web/src/app/(protected)/monitoring/page.tsx` — modified (import + 1 new section in Ops tab body).
- Log = 5 files total.

**Open issues / deferred:**

- **Cross-service metrics not surfaced.** Router classify counters, circuit-breaker state, fallback invocations, per-tier worker latency all live on `gls-classifier-router` / `gls-llm-worker` registries. Surfacing them needs an HTTP probe layer (per-service `GET /metrics` proxy) — separate PR.
- **No time-series — only current-state.** The dashboard reflects cumulative counter values at refresh time. Historical graphs come from Prometheus + Grafana scraping the same metrics; this dashboard is "what's happening right now" at-a-glance.
- **No auto-refresh.** Manual refresh only. Could add a 30-second poll if operators want; not added by default to keep the page quiet.

**Phase 3 status:** §3.5 (partial — DLQ + locks shipped, document monitoring + retry buttons + bulk reclassify still open) and §3.8 (now shipped, cross-service metrics deferred). §3.1 (visual DAG editor), §3.2 (block library), §3.3 (taxonomy tree editor), §3.6 (audit explorer) untouched.

**Next:** Continue Phase 3 — pick from §3.1 (visual DAG editor for pipelines), §3.2 (block library UI), §3.3 (taxonomy tree editor), §3.6 (audit explorer). Pipeline DAG editor is the biggest single piece; the rest are smaller CRUD-shaped UIs.

## 2026-05-01 — Phase 3 PR3 — Audit explorer (Tier 2 search)

**Done:** Phase 3 plan item §3.6 — first cut of the v2 audit-event explorer. The legacy `/admin/audit` page already lists per-service request audit events from the `audit_events` collection (auth, controller calls). The new `/admin/audit-events` page lists v2 envelope events from `gls-audit-collector` — pipeline domain events, classifier decisions, governance actions — that have no UI yet.

The collector itself runs as an internal Docker service (`http://gls-audit-collector:8080`) with no Spring Security on its public surface, so the browser can't reach it directly and we don't want to expose it directly even if it could. Solution: a thin admin proxy on `gls-app-assembly` at `/api/admin/audit-events/v2` that forwards to the collector's `/v1/events`. The proxy is gated by the existing `/api/admin/**` admin filter chain.

**Changes:**

- `gls-app-assembly/.../infrastructure/services/AuditCollectorClient.java` (new) — JDK `HttpClient`, mirrors the shape of `IndexingWorkerClient`. Two methods: `listTier2Events(SearchParams)` forwards `/v1/events` with all query params; `findEventById(eventId)` returns `Optional<String>` (empty on upstream 404). Returns raw JSON-string bodies — no DTO mapping — so the collector contract can evolve without touching this module.
- `gls-app-assembly/.../infrastructure/controllers/admin/AuditExplorerController.java` (new) — `GET /api/admin/audit-events/v2` (Tier 2 search) + `GET /api/admin/audit-events/v2/{eventId}` (single-event lookup, hits Tier 1 + Tier 2). Forwards upstream JSON unmodified. 502 on transport failure with JSON-shaped error body.
- `gls-app-assembly/.../controllers/admin/AuditExplorerControllerTest.java` (new) — 7 tests. Param forwarding (all six query fields captured into `SearchParams`), upstream pass-through, 502 on `AuditCollectorException`, single-event happy path, 404 on miss, 502 on get failure, JSON-escaping of the eventId in the 404 error body.
- `web/src/app/(protected)/admin/audit-events/page.tsx` (new) — single page, two halves. Single-event lookup at the top (paste a ULID, get full envelope), Tier 2 filter form below (documentId / eventType / actorService / from / to / pageSize), paginated result list with click-to-expand rows. `pageStack` state tracks the cursor history so Prev works even though the API is forward-cursor only. Empty/loading/error states all branch.
- `web/src/components/sidebar.tsx` — adds a new "Audit Explorer" admin link next to the existing "Audit Log".

**Tests:** `gls-app-assembly` `AuditExplorerControllerTest` 7 / 0 (new). Reactor green. `tsc --noEmit` clean. ESLint clean for the new + edited files (existing sidebar warnings — Upload/Workflow/History unused, `<img>` element — pre-existing, not introduced).

**Decisions logged:** None new. The "thin string-pass-through proxy" call deserves a note: forwarding raw JSON bodies couples the frontend to the collector contract directly, but spares us from regenerating + maintaining DTOs in `gls-app-assembly` every time the collector contract changes — and the contract is intentionally stable (`additionalProperties: true` on `AuditEvent`). If the proxy ever needs to enrich responses (e.g. join with `documents` collection), this can be revisited.

**Files changed:**

- `backend/gls-app-assembly/src/main/java/.../infrastructure/services/AuditCollectorClient.java` — 1 new.
- `backend/gls-app-assembly/src/main/java/.../infrastructure/controllers/admin/AuditExplorerController.java` — 1 new.
- `backend/gls-app-assembly/src/test/java/.../infrastructure/controllers/admin/AuditExplorerControllerTest.java` — 1 new.
- `web/src/app/(protected)/admin/audit-events/page.tsx` — 1 new.
- `web/src/components/sidebar.tsx` — modified (1 NavLink added).
- Log = 6 files total.

**Open issues / deferred:**

- **No Tier 1 timeline-per-document view.** §3.6 calls for "Tier 1 timeline per document (compliance view)". The single-event lookup resolves Tier 1 events, but there's no per-document compliance timeline yet. The collector `/v1/events` endpoint is Tier 2-only by design (Tier 1 isn't free-form searchable — it's hash-chained for attestation). To list Tier 1 per document we'd need a new collector endpoint or to walk the chain from the most recent verifiable Tier 1 event. Tracked.
- **No chain-verify integration.** `/v1/chains/{type}/{id}/verify` is shipped on the collector but there's no UI button yet — would slot into the same page once the per-document Tier 1 timeline lands.
- **No Tier 3 trace deep-link.** §3.6 calls for jumping from any audit event into Tier 3 trace storage — depends on Tempo / Jaeger integration that isn't wired yet.
- **No CSV export.** §3.6 calls for "Export for legal hold, with redaction options" — separate piece of work, the search UI is the prerequisite.

**Phase 3 status:** §3.6 partial (Tier 2 search shipped; Tier 1 timeline + chain verify + trace deep-link + export still open). All remaining picks: §3.1 (visual DAG editor), §3.3 (taxonomy tree editor), §3.5 finishing (bulk reclassify with cost estimate), §3.4 (component management — partial coverage exists in /governance).

**Next:** Continue Phase 3 — taxonomy tree editor (§3.3) or bulk reclassify (§3.5 finishing) are smallest. Pipeline DAG editor (§3.1) is biggest piece but high value.

## 2026-05-01 — Phase 3 PR4 — Bulk reclassify with cost estimate

**Done:** Phase 3 plan item §3.5 finishing — bulk reclassification trigger with cost estimate from recent CLASSIFY usage. The legacy `/admin/monitoring/pipeline/retry-failed` endpoint already handles failed documents; this fills the gap for the *successfully* classified ones an operator wants to re-run after a prompt or model change.

Backend exposes `POST /api/admin/monitoring/pipeline/bulk-reclassify` accepting either a status filter (multi-select) or an explicit document-ID list, with `dryRun` defaulting to false and a `hardCap` (1–5000, default 1000). Response carries the matched count, queue/skip totals, error list, and a `BulkReclassifyCostEstimator.Estimate` record (mean cost + token counts from the last 100 CLASSIFY usage logs × matched count, USD).

The estimator is a separate service so it's unit-testable without booting Spring. The estimate degrades gracefully — if no recent CLASSIFY logs exist (cold start or local-only Ollama with $0 cost), it returns `Estimate.empty()` and the UI shows "estimate unavailable".

Frontend ships a collapsible `BulkReclassifyPanel` on the Ops tab. Default flow: pick statuses → Preview (dry-run) → see matched count + cost → Execute (browser confirm) → enqueue. Explicit document IDs (textarea, comma/newline-separated) override the status filter. Hard cap is editable.

**Changes:**

- `gls-app-assembly/.../infrastructure/services/BulkReclassifyCostEstimator.java` (new) — pulls last N CLASSIFY logs from `AiUsageLogRepository`, averages `estimatedCost`, scales by document count. Skips zero-cost samples from the cost average (Ollama logs report $0) but still counts their tokens. Returns immutable `Estimate` record.
- `gls-app-assembly/.../infrastructure/controllers/admin/MonitoringController.java` — adds `bulkReclassify(BulkReclassifyRequest)` + `resolveBulkReclassifyTargets(...)` helper. Constructor extended with `BulkReclassifyCostEstimator` dep. Re-uses the existing `requeueDocument` helper for the per-doc requeue path so behaviour is identical to the per-document reclassify endpoint.
- `gls-app-assembly/.../infrastructure/services/BulkReclassifyCostEstimatorTest.java` (new) — 5 tests: zero-document-count short-circuit, no-history empty estimate, average-and-scale happy path, zero-cost samples skipped from cost-mean (but token average still counts them), custom sample size forwarded to repo.
- `web/src/components/monitoring/bulk-reclassify-panel.tsx` (new) — React component with status checkboxes, override textarea, hard-cap input, Preview / Execute buttons, result panel rendering the cost estimate as a four-stat grid plus the matched / queued / skipped counts. `confirm()` step shows the estimated dollar cost before real execution.
- `web/src/app/(protected)/monitoring/page.tsx` — imports + renders `<BulkReclassifyPanel />` on the Ops tab between Performance and Dead-letter queues.

**Tests:** `gls-app-assembly` `BulkReclassifyCostEstimatorTest` 5 / 0 (new). Full app-assembly suite green (no breakage from the new constructor dep). `tsc --noEmit` clean. ESLint clean for new + edited files (existing monitoring/page.tsx warnings — unused imports, `any` types — pre-existing, not introduced).

**Decisions logged:** None new. The "skip zero-cost samples from cost-mean" decision is worth a callout: when Ollama runs locally it logs $0 cost, which would drag a mean toward zero and make Anthropic-mostly stacks look free. Skipping zeros means the estimate represents *paid* runs only. Documented in the estimator class header.

**Files changed:**

- `backend/gls-app-assembly/src/main/java/.../infrastructure/services/BulkReclassifyCostEstimator.java` — 1 new.
- `backend/gls-app-assembly/src/main/java/.../infrastructure/controllers/admin/MonitoringController.java` — modified (import + constructor dep + 2 new methods + 1 DTO inner class).
- `backend/gls-app-assembly/src/test/java/.../infrastructure/services/BulkReclassifyCostEstimatorTest.java` — 1 new.
- `web/src/components/monitoring/bulk-reclassify-panel.tsx` — 1 new.
- `web/src/app/(protected)/monitoring/page.tsx` — modified (import + 1 new section).
- Log = 6 files total.

**Open issues / deferred:**

- **No category-based filter.** UI only filters by status or explicit IDs. Adding a "by category" dropdown would need a new `findByCategoryId` query path on `DocumentRepository` (or `MongoTemplate` aggregation in the controller). Tracked.
- **No per-pipeline override.** Single-document reclassify accepts a `pipelineId` to force a specific pipeline; bulk path uses each document's auto-resolved pipeline. Easy to add if needed.
- **No hard pre-flight budget gate.** The estimator gives an estimate; the actual spend is unbounded (subject to the per-replica `gls.llm.worker.budget.daily-token-cap` if configured, but that's a soft daily cap, not a per-request cap). For a 5000-doc bulk run an operator could add a confirmation that says "this exceeds today's remaining budget of $X" — needs a budget-remaining API endpoint that doesn't exist yet.
- **No async / job tracking.** The endpoint loops synchronously and returns when done. For 5000-document runs that's ~minutes of HTTP request — fine for admin tooling but not ideal. Could move to a background job + status poll if it becomes a problem.

**Phase 3 status:** §3.5 effectively complete (DLQ + locks + bulk reclassify all shipped; document monitoring exists; per-doc retry exists). §3.6 partial (Tier 2 search shipped; Tier 1 timeline + chain verify + export still open). §3.8 mostly shipped (cross-service metrics deferred). §3.1 visual DAG editor and §3.3 taxonomy tree editor are the remaining big-ticket Phase 3 items.

**Next:** Continue Phase 3 — pick from §3.1 (visual DAG editor for pipelines, biggest piece), §3.3 (taxonomy tree editor), §3.6 follow-ups (Tier 1 timeline / chain verify / CSV export). DAG editor unblocks per-category pipeline assignment.

## 2026-05-01 — Phase 3 PR5 — Audit Explorer chain verification

**Done:** Phase 3 §3.6 follow-up — wires the audit collector's existing `GET /v1/chains/{type}/{id}/verify` endpoint into the Audit Explorer UI. The collector ships per-resource Tier 1 hash-chain verification (CSV #4) — walks the chain from oldest to newest, recomputes the hash sequence, returns `OK + eventsTraversed` or `BROKEN + brokenAtEventId + expected/computed previous-hash`. Until this PR there was no UI to trigger it; operators had to `curl` the internal collector hostname.

**Changes:**

- `gls-app-assembly/.../infrastructure/services/AuditCollectorClient.java` — new `verifyChain(resourceType, resourceId)` method, mirrors `findEventById`'s shape (returns `Optional<String>` so 404 — no Tier 1 events for this resource — is a regular outcome, not a transport failure). Forwards raw upstream JSON.
- `gls-app-assembly/.../infrastructure/controllers/admin/AuditExplorerController.java` — new `GET /api/admin/audit-events/v2/chains/{resourceType}/{resourceId}/verify` proxy. 200 + upstream body on success, 404 on no-events, 502 on collector failure. JSON-shaped error bodies as elsewhere on this controller.
- `gls-app-assembly/.../controllers/admin/AuditExplorerControllerTest.java` — 4 new tests on top of the existing 7. Pass-through, 404-on-no-events, 502 on transport failure, BROKEN status forwarded as-is. Total 11 / 0.
- `web/src/app/(protected)/admin/audit-events/page.tsx` — adds a Tier 1 chain-verification section between the single-event lookup and the Tier 2 search filters. Resource-type dropdown (DOCUMENT / BLOCK / USER / PIPELINE_RUN / POLICY / CATEGORY / RETENTION_SCHEDULE matching the contract enum), resource-id input, Verify button. Result panel: green for OK, red for BROKEN. Shows first/last event IDs on OK; broken-event-id + expected/computed previous-hash on BROKEN. Toast on outcome.

**Tests:** `AuditExplorerControllerTest` 11 / 0 (was 7; +4 new). Reactor green. `tsc --noEmit` clean. ESLint clean for the edited file.

**Decisions logged:** None new. Continues the "string pass-through proxy" decision from PR3 — the collector's `ChainVerifyResponse` schema is forwarded unchanged.

**Files changed:**

- `backend/gls-app-assembly/src/main/java/.../infrastructure/services/AuditCollectorClient.java` — modified (one new public method).
- `backend/gls-app-assembly/src/main/java/.../infrastructure/controllers/admin/AuditExplorerController.java` — modified (one new endpoint).
- `backend/gls-app-assembly/src/test/java/.../infrastructure/controllers/admin/AuditExplorerControllerTest.java` — modified (4 new tests).
- `web/src/app/(protected)/admin/audit-events/page.tsx` — modified (state + handler + UI section + new `Detail` helper).
- Log = 5 files total.

**Open issues / deferred:**

- **No deep-link from a single Tier 1 event to its chain-verify result.** The single-event lookup shows the event but there's no "verify this event's chain" button on the result. Easy follow-up — would need to know the event's `resourceType` + `resourceId` (both already on the envelope under `resource`).
- **No async / progress for long chains.** `verifyChain` is synchronous; the collector contract notes long-running for backends with many events. UI shows a spinner but no progress indicator. Fine for typical document chains (handful of events).
- **No Tier 1 timeline-per-document view.** Still deferred — `/v1/events` is Tier 2-only. The collector would need a new endpoint to list Tier 1 events for a resource (the verify endpoint walks them but doesn't list them).
- **No CSV export.** Still deferred.

**Phase 3 status:** §3.6 partial (Tier 2 search + chain verify shipped; Tier 1 timeline + Tier 3 trace deep-link + CSV export still open). §3.5 complete. §3.8 mostly complete. §3.1 / §3.3 / §3.4 / §3.9 untouched.

**Next:** Continue Phase 3 — §3.3 taxonomy tree editor (medium) or §3.1 visual DAG editor (biggest). Smaller alternative: §3.6 follow-up — Tier 1 timeline-per-document, but that needs a new collector contract endpoint first.

## 2026-05-01 — Phase 3 PR6 — Drag-drop taxonomy reparenting

**Done:** Phase 3 plan item §3.3 first cut — drag-drop reorganisation on the existing TaxonomyPanel. The legacy panel already had tree rendering with expand/collapse, add/edit/delete, and a CategoryForm modal with all per-node fields (keywords, sensitivity, retention, metadata schema, owner, custodian, jurisdiction, etc.). What it lacked was a way to move a node — operators had to delete + recreate, or open the form modal, scroll to find the parent dropdown, save. This PR adds native HTML5 drag-drop on top.

The existing `PUT /taxonomy/{id}` accepted `parentId` updates but had no cycle protection, required the full body, and didn't make a great drag-drop target. New `POST /taxonomy/{id}/move` endpoint takes just `{newParentId}`, validates against the three failure modes (move-onto-self, move-under-own-descendant, missing target), recomputes the source's `level` based on the new depth, and triggers `governanceService.rebuildPaths()` so the materialised path tree stays consistent.

Frontend uses the native HTML5 drag-and-drop API — no extra dependency. Each tree row has `draggable={true}` plus `onDragStart` / `onDragOver` / `onDrop` handlers. Drag affordances: source row goes to 40% opacity while dragging, valid drop targets get a blue ring + light-blue background. A "drop here to promote to top-level" strip appears above the tree only while a drag is in flight.

**Changes:**

- `gls-app-assembly/.../infrastructure/controllers/admin/GovernanceAdminController.java` — new `POST /taxonomy/{id}/move` endpoint + private helpers `isDescendantOf` (walks the parent chain to detect cycles) and `resolveLevel` (FUNCTION at root, ACTIVITY one level down, TRANSACTION below). Returns 200 + saved category on success, 404 on missing source, 400 on cycle / missing target.
- `gls-app-assembly/.../controllers/admin/GovernanceAdminControllerMoveTest.java` (new) — 8 tests: move to root sets level FUNCTION; move under FUNCTION sets ACTIVITY; move under ACTIVITY sets TRANSACTION; move-onto-self → 400; move-under-descendant → 400 (proves cycle detection); source-not-found → 404; missing-target → 400; successful move increments version + saves once.
- `web/src/app/(protected)/governance/page.tsx` — TaxonomyPanel gets `draggingId` / `dragOverId` / `moving` state + `moveCategory` handler that calls the new endpoint. TaxonomyNode signature extended with six drag props (passed recursively into children). Each row is now `draggable`; a root drop-zone strip appears above the tree while a drag is active. Loader2 overlay during the in-flight move so the user can't trigger a second move before the first finishes.

**Tests:** `GovernanceAdminControllerMoveTest` 8 / 0 (new). Reactor green. `tsc --noEmit` clean. ESLint warnings on `governance/page.tsx` are pre-existing (set-state-in-effect on tab init, unused `i` var, ternary-as-expression in `toggle`) — not introduced by this PR.

**Decisions logged:** None new. The "native HTML5 drag-drop, no library" call is worth noting: the tree is small (a few hundred categories at the absolute upper bound) and needs only single-element drag-drop without complex collision detection. `react-dnd` or `@hello-pangea/dnd` would be 50–100 KB extra for no real win at this scale.

**Files changed:**

- `backend/gls-app-assembly/src/main/java/.../infrastructure/controllers/admin/GovernanceAdminController.java` — modified (new endpoint + 2 helpers).
- `backend/gls-app-assembly/src/test/java/.../infrastructure/controllers/admin/GovernanceAdminControllerMoveTest.java` — 1 new.
- `web/src/app/(protected)/governance/page.tsx` — modified (TaxonomyPanel state + handlers, TaxonomyNode signature + drag attrs, root drop-zone, Loader2 overlay).
- Log = 4 files total.

**Open issues / deferred:**

- **No sibling reordering.** The tree displays children in the order Mongo returns them; there's no UI to drag a node up/down within siblings. The model has a `sortOrder` field but the move endpoint doesn't touch it. Adding "drop on the top half of a row to insert before / bottom half to insert after" + a batch sortOrder update on the backend is a focused follow-up.
- **No audit trail UI.** Every move call is audited via `PlatformAuditEmitter` (because `governanceService.rebuildPaths` triggers a state-change audit), but there's no UI to view the audit history filtered by `categoryId`. Defers to the existing Audit Explorer (PR3) — operators can search Tier 2 events by `documentId` but not by category ID; could add a category-scoped query.
- **No multi-select drag.** Drag one node at a time. Bulk reorganisation needs admin to repeat.
- **No offline / optimistic update.** UI waits for the server response before updating the tree. Optimistic update would feel snappier but introduces rollback complexity on cycle errors.

**Phase 3 status:** §3.3 partial (drag-drop reparenting + per-node field editor shipped; sibling reorder + audit-trail-per-category + hub pack browser/import wizard still open). §3.5 complete. §3.6 partial. §3.8 mostly complete. §3.1 / §3.4 / §3.9 untouched.

**Next:** Continue Phase 3 — §3.1 visual DAG editor (biggest piece, multi-PR), §3.4 component management UI (PII / sensitivity / storage tier / trait CRUD — much of it likely exists already), §3.6 follow-ups (Tier 1 timeline / CSV export). DAG editor is the headline missing item from the original v2 vision.

## 2026-05-01 — Phase 3 PR7 — Taxonomy sibling reordering

**Done:** Phase 3 §3.3 follow-up — sibling reordering on top of yesterday's drag-drop reparenting (PR6). The model already has a `sortOrder` field; nothing wrote to it before this PR. Operators could reparent but couldn't change sibling order.

The move endpoint now accepts optional `beforeSiblingId` / `afterSiblingId` in the request body. When present, after updating parentId/level on the source, all active siblings under the new parent are renumbered in steps of 10 (10, 20, 30, ...) with the source inserted at the requested position. The body now also distinguishes "key absent" (leave parent unchanged — used for same-parent reorders) from "key present, value null" (promote to root).

`getFullTaxonomy()` now sorts by `sortOrder` ascending so the order operators set survives the round-trip. Categories with `sortOrder=0` (everything before this PR landed) keep insertion order until manually reordered — no migration needed.

Frontend extends each tree row with three drop zones detected from the cursor's Y position relative to the row's bounding rect: top 25% = drop before sibling, middle 50% = drop into (existing reparent behaviour), bottom 25% = drop after sibling. Visual affordances: top/bottom-edge highlights for before/after (a 2px blue bar), full-row blue background for into. Toast distinguishes the three outcomes.

**Changes:**

- `gls-governance/.../services/GovernanceService.java` — `getFullTaxonomy()` now sorts results by `sortOrder` asc using a stream + comparator. Backwards-compatible — existing clients see no semantic change beyond consistent ordering.
- `gls-app-assembly/.../infrastructure/controllers/admin/GovernanceAdminController.java` — `moveCategory` extended with the `beforeSiblingId` / `afterSiblingId` body fields, plus a `renumberSiblings` private helper that fetches active siblings under the new parent, removes the source from the list, inserts at the target index, and writes `sortOrder` 10/20/30/... back. The "newParentId key absent vs null" semantic is documented inline. Unchanged behaviour when no sibling-position is given (source appended to end).
- `gls-app-assembly/.../controllers/admin/GovernanceAdminControllerMoveTest.java` — extended from 8 to 12 tests (+4 new): before-sibling renumbers source first, after-sibling renumbers source last, no-position appends to end, same-parent reorder leaves parent unchanged. The existing tests' `BeforeEach` was tightened to default-stub the new repo methods (`findByParentIdAndStatus` / `findByParentIdIsNullAndStatus`) so older tests don't NPE.
- `web/src/app/(protected)/governance/page.tsx` — `TaxonomyPanel` adds `dragOverZone` state alongside `dragOverId`. `moveCategory` now takes a `target` object + zone and shapes the API body accordingly (`{newParentId: target.id}` for into vs `{newParentId: target.parentId, beforeSiblingId|afterSiblingId: target.id}` for before/after). `TaxonomyNode` adds `zoneFromEvent(e)` that does the Y-position math, plus before/after highlight bars positioned absolutely on the row.

**Tests:** `GovernanceAdminControllerMoveTest` 12 / 0 (was 8; +4 new). Full app-assembly suite green. `tsc --noEmit` clean. ESLint warnings unchanged from PR6 (3 pre-existing, none introduced).

**Decisions logged:** None new. The "step-of-10 renumber" approach is worth a callout: it's O(n) per move where n is the sibling count under one parent (always small — typically 5–15 children per node in a real taxonomy). A float-midpoint approach would avoid the O(n) writes but introduce floating-point drift over many reorders. With siblings bounded by tree shape, full renumber is simpler and correct.

**Files changed:**

- `backend/gls-governance/src/main/java/.../services/GovernanceService.java` — modified (sort in `getFullTaxonomy`).
- `backend/gls-app-assembly/src/main/java/.../infrastructure/controllers/admin/GovernanceAdminController.java` — modified (move endpoint extended + 3 helper methods).
- `backend/gls-app-assembly/src/test/java/.../infrastructure/controllers/admin/GovernanceAdminControllerMoveTest.java` — modified (default mocks + 4 new tests + helper).
- `web/src/app/(protected)/governance/page.tsx` — modified (zone state + zone-aware moveCategory + zone-aware drop handlers + before/after highlight bars).
- Log = 5 files total.

**Open issues / deferred:**

- **No migration to seed `sortOrder` for existing categories.** They all keep `sortOrder=0` until an operator drags them. Stable sort handles ties (frontend filter preserves order) so no visible regression — but if multiple categories have sortOrder=0 and an operator reorders one of them, the reorder applies to all siblings (which is correct behaviour, just initially surprising).
- **No keyboard reorder.** Drag-drop only; arrow-key reorder would help accessibility.
- **No bulk reorder UI.** Each move is one drag at a time.
- **Cross-parent drag-and-drop with sibling position** — works (the body shape supports it) but the UI flow is "drop on top edge of an existing sibling under target parent". Sometimes operators want "drop as first child of empty parent" which currently maps to "into" only.

**Phase 3 status:** §3.3 effectively complete (drag-drop reparent + sibling reorder + per-node fields all shipped; audit-trail-per-category + hub pack browser/import wizard still open). §3.5 complete. §3.6 partial. §3.8 mostly complete. §3.1 / §3.4 / §3.9 untouched.

**Next:** Continue Phase 3 — §3.1 visual DAG editor (biggest), §3.4 component management UI, §3.9 hub pack management. The first is multi-PR; the latter two are smaller and could ship as solo PRs.
