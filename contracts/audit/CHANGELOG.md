# Changelog — `audit/`

Format: [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

(Phase 0.7 — common envelope schema, audit-event payload bindings, and the `gls-platform-audit` shared library land here.)

## [0.2.0] — 2026-04-26

### Added

- `asyncapi.yaml` (stub) — declares the three audit tier channel families (`audit.tier1.{eventType}`, `audit.tier2.{eventType}`, `audit.tier3.{eventType}`) with parameterised routing-key suffix and forward-looking `AuditEventEnvelope` schema placeholder. Full payload binding lands in 0.7 once CSV #3 / #4 / #5 / #6 / #7 / #8 close.

## [0.1.0] — 2026-04-26

### Added

- Folder scaffolding (Phase 0.3). Content lands in 0.7.
