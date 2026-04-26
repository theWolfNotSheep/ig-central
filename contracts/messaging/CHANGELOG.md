# Changelog — `messaging/`

Format: [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

## [0.3.0] — 2026-04-26

### Added

- `configChanged` channel on routing key `config.changed` (exchange `gls.config`, topic). Cache-invalidation fanout for governance entity writes per CSV #30. Publisher convention covers both local writes and Hub-driven imports; consumer convention is a non-durable exclusive queue per replica. No DLX (missed events are transient and self-healing on the next write).
- `publishConfigChanged` / `consumeConfigChanged` operations.
- `ConfigChangedEvent` message + payload schema. Required: `entityType`, `changeType`, `timestamp`, `actor`. Optional: `entityIds[]` (empty/absent means bulk wildcard), `traceparent`. `additionalProperties: false`.

### Changed

- `info.description` reframed: `gls.audit.*` is no longer "stub" — declared in `contracts/audit/asyncapi.yaml`, publisher side now in `gls-platform-audit` library. `gls.config.changed` is no longer "forward-looking" — channel + payload now defined here; the publisher/dispatcher implementation lands in `gls-platform-config`.

## [0.2.0] — 2026-04-26

### Added

- `asyncapi.yaml` — initial AsyncAPI 3.0 declaration of the existing Rabbit topology. Two exchanges (`gls.documents` topic, `gls.pipeline` topic) plus their DLQs; eight channels covering document lifecycle (`document.{ingested, processed, classified, classification.failed}`) and pipeline orchestration (`pipeline.{llm.requested, llm.completed, resume, dlq}`); five message envelopes (`DocumentIngestedEvent`, `DocumentProcessedEvent`, `DocumentClassifiedEvent`, `LlmJobRequestedEvent`, `LlmJobCompletedEvent`); per-channel producer/consumer + DLQ documentation.
- Forward-looking declarations for `gls.config.changed` (lands with Phase 0.8) and `gls.audit.*` (declared in `contracts/audit/asyncapi.yaml`; full payloads in 0.7) called out in the file's description.

## [0.1.0] — 2026-04-26

### Added

- Folder scaffolding (Phase 0.3). Content lands in 0.6.
