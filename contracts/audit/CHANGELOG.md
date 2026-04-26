# Changelog — `audit/`

Format: [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

(Remaining Phase 0.7 — `audit_outbox` MongoDB collection schema and the `gls-platform-audit` shared library.)

## [0.3.0] — 2026-04-26

### Added

- `event-envelope.schema.json` — JSON Schema 2020-12 for the common audit event envelope. Single contract for cross-tier correlation and per-resource hash-chain integrity. Aligns with `version-2-architecture.md` §7.4. Implements CSV #4 (per-resource hash chain via `previousEventHash`), CSV #6 (metadata/content partition under `details`), CSV #7 (`supersedes` / `supersededBy` links), CSV #20 (`traceparent` propagation). Tier 1 (`DOMAIN`) and Tier 2 (`SYSTEM`) tiers; conditional `allOf` enforces chain link + resource + retentionClass on Tier 1 events.

## [0.2.0] — 2026-04-26

### Added

- `asyncapi.yaml` (stub) — declares the three audit tier channel families (`audit.tier1.{eventType}`, `audit.tier2.{eventType}`, `audit.tier3.{eventType}`) with parameterised routing-key suffix and forward-looking `AuditEventEnvelope` schema placeholder. Full payload binding lands in 0.7 once CSV #3 / #4 / #5 / #6 / #7 / #8 close.

## [0.1.0] — 2026-04-26

### Added

- Folder scaffolding (Phase 0.3). Content lands in 0.7.
