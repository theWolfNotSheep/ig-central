---
title: audit-collector
lifecycle: forward
---

# `igc-audit-collector` contract

The audit collector consumes from `audit.tier1.{eventType}` and
`audit.tier2.{eventType}` (declared in `contracts/audit/asyncapi.yaml`)
and persists events to durable backends. Phase 1.12 split per the
implementation plan: Tier 1 is single-writer (Class D singleton with
ShedLock leader election, hash-chain integrity per CSV #4); Tier 2 is
horizontally scaled.

This OpenAPI document describes the **REST query + admin surface**
that the admin UI + ops tooling call to read events back out. The
write path is exclusively async (Rabbit) — there is no `POST /v1/events`.

## Surface

- `GET /v1/events?documentId=…&eventType=…&from=…&to=…&pageToken=…`
  — list / search Tier 2 events. Cursor-paginated.
- `GET /v1/events/{eventId}` — fetch a single event by ULID id.
  Works for both tiers; the collector resolves which backend to
  consult from the id index.
- `GET /v1/chains/{resourceType}/{resourceId}/verify` — Tier 1
  per-resource hash-chain verification (CSV #4). Walks oldest →
  newest, recomputes the hash sequence, returns either an OK +
  event count or a detailed mismatch report (which event broke the
  chain, what hash was expected vs computed).
- `GET /v1/capabilities`, `GET /actuator/health`.

## What the collector does

- **Tier 1 (DOMAIN, compliance).** Validates the per-resource hash
  chain on receipt — rejects any event whose `previousEventHash`
  doesn't match the recorded tail of the chain. Writes to the
  configured WORM backend (Mongo append-only with role-based deny
  in PR4; S3 Object Lock as a future follow-up). Single writer per
  CSV #4.
- **Tier 2 (SYSTEM, operations).** No chain validation. Writes to
  the configured time-series store (Elasticsearch in PR3; OpenSearch
  + S3 ILM as a future follow-up). Horizontally scaled.

## Dependencies

- RabbitMQ (consume `audit.tier1.*` + `audit.tier2.*`).
- Tier 1 backend (Mongo `audit_tier1_events` collection in PR4 first
  cut, role-based-deny + append-only-via-app-layer; WORM backend
  swap is a future follow-up).
- Tier 2 backend (Elasticsearch `audit_tier2_events` index in PR3).
- ShedLock (existing Mongo lock collection) for Tier 1 leader
  election.

## Versioning

Per the API Contracts rule in `CLAUDE.md`. `VERSION` bumps on every
spec change; major bumps for breaking changes; `CHANGELOG.md` records
each version.
