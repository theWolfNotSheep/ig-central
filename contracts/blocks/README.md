---
title: Pipeline-block content schemas
lifecycle: forward
---

# Pipeline-block content schemas

JSON Schema 2020-12 definitions for the `content` field of every `PipelineBlock` type.

## Why JSON Schema 2020-12

Block content is dynamic — different block types have different schemas. Storing them as MongoDB documents with a typed schema lets us:

- Validate `content` on save (no malformed blocks reaching the engine).
- Generate admin-UI forms automatically from the schema.
- Document the block's contract in the same vocabulary as everything else (OpenAPI 3.1.1 aligns to JSON Schema 2020-12 — same parser, same validator).

## Content (target — populated alongside the block types that need them)

- `prompt.schema.json` — PROMPT block (system prompt + user template + optional model config + `kind` discriminator that groups prompts by purpose: CLASSIFICATION / SCAN / METADATA_EXTRACTION / GENERAL). Phase 1.9 PR1 ✓ (v0.5.0).
- `regex-set.schema.json` — REGEX_SET block (patterns + types + confidence).
- `extractor.schema.json` — EXTRACTOR block (text extraction config).
- `router.schema.json` — ROUTER block (cascade thresholds, fallback policy). Phase 1.2 ✓ (v0.2.0).
- `enforcer.schema.json` — ENFORCER block (governance enforcement rules).
- `bert-classifier.schema.json` — BERT_CLASSIFIER block (model version, label mapping, optional artifact ref + training metadata + minTextLength skip). Phase 1.4 ✓ (v0.3.0).
- `policy.schema.json` — POLICY block (per-category required scans, metadata-schema dispatch, governance-policy refs, optional sensitivity-conditioned overrides). Per CSV #35. Phase 1.8 ✓ (v0.4.0).

## Convention

Every schema declares `$id` as a stable URL: `https://gls.local/contracts/blocks/<name>.schema.json`. Schemas reference `_shared/` for cross-cutting types (e.g. duration, currency).

## Versioning

`blocks/VERSION` covers the directory's schema surface. Block content schemas are referenced from `PipelineBlock` documents in MongoDB — an old block stored under v1 of the schema must remain readable under v2; migrations may add fields with defaults but must not remove required fields without a major bump and a Mongock change unit.
