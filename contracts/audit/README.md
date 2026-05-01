---
title: Audit contracts
lifecycle: forward
---

# Audit contracts

Schemas for the three-tier audit model (per `version-2-architecture.md` §7).

## Tiers

- **Tier 1 — compliance.** Append-only, hash-chained per resource. Backend DECIDED in CSV #3 (external WORM — S3 Object Lock + Athena, or managed audit service). Concrete vendor / configuration choice deferred to Phase 1.12 implementation.
- **Tier 2 — operations / debugging.** Hot-then-cold time-series store; OpenSearch + S3 ILM proposed.
- **Tier 3 — distributed traces.** OpenTelemetry collector; references back to Tier 1 / 2 events via `traceparent`.

## Content

**Delivered (VERSION 0.3.0):**

- `event-envelope.schema.json` — JSON Schema 2020-12 for the common audit event envelope. Cross-tier correlation, per-resource hash-chain integrity, metadata/content partition for right-to-erasure, supersession links. Aligns with `version-2-architecture.md` §7.4 and the audit decisions CSV #4 / #6 / #7 / #20.
- `asyncapi.yaml` (stub) — declares the three audit tier channel families with parameterised routing-key suffix. Channel ↔ envelope binding lands as a follow-up in this folder.

**Target (remaining Phase 0.7):**

- `asyncapi.yaml` — bind the channels to `event-envelope.schema.json` (currently the asyncapi has its own opaque `AuditEventEnvelope` schema; cross-format `$ref` between AsyncAPI YAML and JSON Schema 2020-12 needs a small follow-up).
- `audit_outbox` MongoDB collection schema and indexes — designed alongside the `igc-platform-audit` library since they're operationally coupled.
- `igc-platform-audit` shared library (JVM) — envelope construction, outbox writer, relay-to-Rabbit, retry/backoff. Single dependency every service imports.

## Why a separate folder from `messaging/`

`messaging/` holds business-event channels (document ingested, document classified). `audit/` holds the audit-event channels — different consumer (`igc-audit-collector`), different retention policy, different schema discipline (hash-chained, redaction-aware).

## Versioning

Audit envelope changes are *especially* sensitive: a schema change can break the per-resource hash chain (per CSV #4). Versioning is strict — additive only at minor versions; field removal or required-ness change requires a major bump and a chain-migration plan.
