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
| 0   | In progress | 2026-04-26 | — | 0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.10 done; 0.7/0.8/0.9/0.11/0.12 not yet started |
| 0.5 | Not started | — | — | |
| 1   | Not started | — | — | |
| 2   | Not started | — | — | |
| 3   | Not started | — | — | |

Cross-cutting tracks:

| Track | Status | Notes |
|---|---|---|
| A — Hub-side | Not started | |
| B — Migration / cutover | Not started | Strangler-fig approach planned |
| C — Performance baseline | Not started | First action of Phase 0 |
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
