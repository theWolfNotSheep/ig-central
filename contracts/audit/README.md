---
title: Audit contracts
lifecycle: forward
---

# Audit contracts

Schemas for the three-tier audit model (per `version-2-architecture.md` §7).

## Tiers

- **Tier 1 — compliance.** Append-only, hash-chained per resource. Backend RECOMMENDED in CSV #3 (external WORM); not yet DECIDED.
- **Tier 2 — operations / debugging.** Hot-then-cold time-series store; OpenSearch + S3 ILM proposed.
- **Tier 3 — distributed traces.** OpenTelemetry collector; references back to Tier 1 / 2 events via `traceparent`.

## Content (target — populated in Phase 0.7)

- `event-envelope.schema.json` — JSON Schema 2020-12 for the common envelope across all tiers (per architecture doc §7.4). Defines `metadata` (always retained) vs `content` (subject to right-to-erasure) per CSV #6.
- `asyncapi.yaml` — Tier 1, Tier 2, Tier 3 channels and routing.

## Why a separate folder from `messaging/`

`messaging/` holds business-event channels (document ingested, document classified). `audit/` holds the audit-event channels — different consumer (`gls-audit-collector`), different retention policy, different schema discipline (hash-chained, redaction-aware).

## Versioning

Audit envelope changes are *especially* sensitive: a schema change can break the per-resource hash chain (per CSV #4). Versioning is strict — additive only at minor versions; field removal or required-ness change requires a major bump and a chain-migration plan.
