# Changelog — `enforcement-worker/`

Format: [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

(Entries land alongside the PRs that touch the spec.)

## [0.2.0] — 2026-04-29

### Changed

- `AppliedSummary.retentionTrigger` enum aligned with the canonical
  Java `ClassificationCategory.RetentionTrigger` values
  (`DATE_CREATED`, `DATE_LAST_MODIFIED`, `DATE_CLOSED`, `EVENT_BASED`,
  `END_OF_FINANCIAL_YEAR`, `SUPERSEDED`). PR1 used aspirational
  values that didn't exist in the codebase.
- `AppliedSummary.expectedDispositionAction` enum aligned with the
  canonical Java `RetentionSchedule.DispositionAction` values
  (`DELETE`, `ARCHIVE`, `TRANSFER`, `REVIEW`, `ANONYMISE`,
  `PERMANENT`).

Both changes are technically breaking, but the contract has no live
consumers yet (Phase 1.10 PR2 is the first implementation). Major
version bump deferred to 1.0.0 when the surface stabilises.

## [0.1.0] — 2026-04-29

### Added

- Initial contract for `gls-enforcement-worker` — Phase 1.10 PR1.
  Carves out the enforcement stage from the monolith into its own
  deployable. Sync + async surface (`Prefer: respond-async` per CSV
  #13 / #47), `nodeRunId`-keyed idempotency (CSV #16), RFC 7807
  error envelope (CSV #17), service-account JWT (CSV #18), capability
  advertisement (CSV #21).
- `POST /v1/enforce` — apply governance rules to a classified
  document: copy classification onto the doc, compute retention
  schedule + disposition trigger, migrate storage tier, write the
  audit trail.
- `GET /v1/jobs/{nodeRunId}` — poll an async enforcement job.
- `GET /v1/capabilities`, `GET /actuator/health` — standard meta
  surface.
