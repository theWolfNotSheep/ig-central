# Changelog — `blocks/`

Format: [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

(Block content schemas land alongside the phases that introduce each block type.)

## [0.3.0] — 2026-04-29

### Added

- `bert-classifier.schema.json` — BERT_CLASSIFIER block content. `modelVersion` (semver), optional `artifactRef` MinIO pointer, `labelMapping[]` from model labels to taxonomy categoryIds, optional `trainingMetadata` block (trainer version, dataset size, per-label evaluation metrics), optional `minTextLength` skip threshold. Phase 1.4 — schema lands now; bert-inference consumes it as soon as the trainer's first artefact is published.

## [0.2.0] — 2026-04-29

### Added

- `router.schema.json` — ROUTER block content. Per-tier `enabled` + `accept` thresholds for BERT / SLM / LLM, optional fallback strategy (`LLM_FLOOR` / `ROUTER_SHORT_CIRCUIT`), per-category overrides, and an optional `costBudget.maxCostUnits`. Phase 1.2 close-off — schema lands now; the router consumes it once tier-aware dispatch wires in (Phases 1.4–1.6).

## [0.1.0] — 2026-04-26

### Added

- Folder scaffolding (Phase 0.3).
