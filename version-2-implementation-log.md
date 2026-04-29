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
| 1   | 1.1 / 1.2 / 1.3 complete; 1.4 in flight | 2026-04-29 | — | 1.1 extraction triad shipped. 1.2 closed (router mock + LLM dispatch + ROUTER schema + Mongock seed). 1.3 cutover wired behind `pipeline.classifier-router.enabled` (default off; perf-comparison checkbox still gated on Phase 0.11 baseline capture). 1.4 PR1 lands `gls-bert-inference` JVM module + `BERT_CLASSIFIER` block schema (stub backend). 1.4 PR2 wires the BERT cascade tier in `gls-classifier-router` behind `gls.router.cascade.bert.enabled` (default off; falls through to LLM on `MODEL_NOT_LOADED`). Trainer + ROUTER threshold reading + per-category enable remain. Phases 1.5+ open. |
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
