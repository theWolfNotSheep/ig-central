# Changelog — `audit-collector/`

Format: [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

(Entries land alongside the PRs that touch the spec.)

## [0.1.0] — 2026-04-30

### Added

- Initial REST contract for `gls-audit-collector` — Phase 1.12 PR1.
  The async consumer side of the collector is declared in
  `contracts/audit/asyncapi.yaml` (channels `audit.tier1.{eventType}`
  and `audit.tier2.{eventType}`); this OpenAPI document covers the
  REST query + admin surface that the admin UI + ops tooling call
  to inspect stored events.
- `GET /v1/events` — query Tier 2 events. Filters: `documentId`,
  `eventType`, `actor`, `from` / `to` (RFC 3339), `pageToken`. Capped
  page size; cursor-based pagination.
- `GET /v1/events/{eventId}` — fetch a single event by id (works for
  both Tier 1 and Tier 2; the collector knows which store to consult
  from the id's `tier` byte / lookup index).
- `GET /v1/chains/{resourceType}/{resourceId}/verify` — Tier 1
  per-resource hash-chain verification (CSV #4). Walks the chain
  from oldest to newest, recomputes the hash sequence, returns OK +
  count or detailed mismatch info.
- `GET /v1/capabilities`, `GET /actuator/health` — standard meta
  surface (advertises the configured Tier 1 / Tier 2 backends).
- RFC 7807 problem+json error envelope (CSV #17), service-account JWT
  (CSV #18), `traceparent` header (CSV #20).
