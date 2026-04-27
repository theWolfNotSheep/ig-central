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
