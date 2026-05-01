# Changelog — `blocks/`

Format: [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

(Block content schemas land alongside the phases that introduce each block type.)

## [0.5.0] — 2026-04-29

### Added

- `prompt.schema.json` — PROMPT block content. Carries `systemPrompt` + `userPromptTemplate` (at least one required), an optional `kind` discriminator (`CLASSIFICATION` / `SCAN` / `METADATA_EXTRACTION` / `GENERAL`), `scanType` (required when `kind == SCAN`), `metadataSchemaId` (required when `kind == METADATA_EXTRACTION`), `applicableCategoryIds[]` (CSV #31–34 scope convention), optional `model` config (`provider`, `modelId`, `temperature`, `maxTokens`), and `outputFormat` (`JSON` default / `TEXT`). Phase 1.9 PR1 — schema lands now; the seeder for default scan PROMPT blocks per `PiiTypeDefinition` lands in this same PR; the engine consumes them at stage ④ in PR2.

### Note

- The PROMPT schema formalises a shape the SLM / LLM workers have been reading in practice (`systemPrompt`, `userPromptTemplate`). Existing blocks remain valid because both fields are optional individually (the `anyOf` requires at least one), and the new fields (`kind`, `scanType`, `metadataSchemaId`, `applicableCategoryIds`, `model`, `outputFormat`) all default to permissive values.

## [0.4.0] — 2026-04-29

### Added

- `policy.schema.json` — POLICY block content (Phase 1.8 / CSV #35). Carries post-classification policy for a category: `requiredScans[]` (PII / PHI / PCI / CUSTOM scans), `metadataSchemaIds[]` (extraction schemas), `governancePolicyIds[]` (storage / retention / access enforcement). Optional `conditions.bySensitivity[]` for per-sensitivity overrides (e.g. RESTRICTED docs needing stricter scans than the category default).
- `BlockType.POLICY` added to `igc-governance.PipelineBlock` so the engine + admin UI can identify POLICY blocks distinctly from PROMPT / ROUTER / BERT_CLASSIFIER / etc.

## [0.3.0] — 2026-04-29

### Added

- `bert-classifier.schema.json` — BERT_CLASSIFIER block content. `modelVersion` (semver), optional `artifactRef` MinIO pointer, `labelMapping[]` from model labels to taxonomy categoryIds, optional `trainingMetadata` block (trainer version, dataset size, per-label evaluation metrics), optional `minTextLength` skip threshold. Phase 1.4 — schema lands now; bert-inference consumes it as soon as the trainer's first artefact is published.

## [0.2.0] — 2026-04-29

### Added

- `router.schema.json` — ROUTER block content. Per-tier `enabled` + `accept` thresholds for BERT / SLM / LLM, optional fallback strategy (`LLM_FLOOR` / `ROUTER_SHORT_CIRCUIT`), per-category overrides, and an optional `costBudget.maxCostUnits`. Phase 1.2 close-off — schema lands now; the router consumes it once tier-aware dispatch wires in (Phases 1.4–1.6).

## [0.1.0] — 2026-04-26

### Added

- Folder scaffolding (Phase 0.3).
