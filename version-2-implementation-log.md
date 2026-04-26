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
| 0   | In progress | 2026-04-26 | — | 0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.9, 0.10 done; audit decisions #3–#8 DECIDED; 0.7 envelope + outbox indexes + library skeleton landed; relay/auto-config/validation deferred as 0.7 follow-ups; 0.8 / 0.11 / 0.12 still open |
| 0.5 | Not started | — | — | |
| 1   | Not started | — | — | |
| 2   | Not started | — | — | |
| 3   | Not started | — | — | |

Cross-cutting tracks:

| Track | Status | Notes |
|---|---|---|
| A — Hub-side | Not started | |
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
