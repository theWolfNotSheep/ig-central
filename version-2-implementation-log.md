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
| 0   | In progress | 2026-04-26 | — | 0.1, 0.2, 0.3, 0.4 done; 0.5 tooling decisions made (CI wiring + reference service next); 0.6/0.7/0.8/0.9/0.10/0.11/0.12 not yet started |
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
