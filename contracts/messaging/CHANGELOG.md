# Changelog — `messaging/`

Format: [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

## [0.2.0] — 2026-04-26

### Added

- `asyncapi.yaml` — initial AsyncAPI 3.0 declaration of the existing Rabbit topology. Two exchanges (`gls.documents` topic, `gls.pipeline` topic) plus their DLQs; eight channels covering document lifecycle (`document.{ingested, processed, classified, classification.failed}`) and pipeline orchestration (`pipeline.{llm.requested, llm.completed, resume, dlq}`); five message envelopes (`DocumentIngestedEvent`, `DocumentProcessedEvent`, `DocumentClassifiedEvent`, `LlmJobRequestedEvent`, `LlmJobCompletedEvent`); per-channel producer/consumer + DLQ documentation.
- Forward-looking declarations for `gls.config.changed` (lands with Phase 0.8) and `gls.audit.*` (declared in `contracts/audit/asyncapi.yaml`; full payloads in 0.7) called out in the file's description.

## [0.1.0] — 2026-04-26

### Added

- Folder scaffolding (Phase 0.3). Content lands in 0.6.
