# Changelog — `enforcement-worker/`

Format: [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

(Entries land alongside the PRs that touch the spec.)

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
