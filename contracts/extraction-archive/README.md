---
title: contracts/extraction-archive
lifecycle: forward
---

# `contracts/extraction-archive/`

OpenAPI 3.1.1 spec for `gls-extraction-archive` — Phase 1.1 of the v2 plan. Synchronous archive unpacking; second per-service contract, cloned from the `gls-extraction-tika` pattern (Phase 0.5 reference) and `docs/service-template.md`.

## Layout

- `openapi.yaml` — the spec.
- `VERSION` — semver, currently `0.1.0`.
- `CHANGELOG.md` — append-only.
- `generated/` — generated server stubs land here once the build wires them in (Phase 1.1 implementation PRs).

## Operations

| Method + path | Op id | Notes |
|---|---|---|
| `POST /v1/extract` | `extractArchive` | One-level archive walk. Lands each direct child in MinIO; returns the list of `documentRef`s. Caller (`gls-app-assembly` / orchestrator) creates `DocumentModel` rows and publishes `gls.documents.ingested` events per child. `Idempotency-Key` honoured (CSV #16). |
| `GET /v1/capabilities` | `getCapabilities` | Supported archive types + caps + flags per CSV #21. |
| `GET /actuator/health` | `getHealth` | Liveness + readiness; readiness checks parser init + MinIO reach. |

## Behaviour notes

- **One-level unpack per invocation.** Nested archives are returned as children with `detectedMimeType` set to the inner archive's mime; the orchestrator re-routes them through this service on a fresh `nodeRunId`. Recursion depth is bounded by the pipeline's per-document depth cap, not by this service. Decision: CSV #43.
- **Caller owns fan-out.** The service does not write to Mongo and does not publish to RabbitMQ for child ingest — it returns the child list and the caller commits the per-child DocumentModel + ingest event in `gls-app-assembly`'s existing transaction-safe path.
- **Bounded scope.** Per-archive size, max child count, and max nesting depth are configurable runtime caps; exceeding any cap returns `413` with the envelope `code` extension identifying which cap was hit (zip-bomb defence).

## Cross-references

- `_shared/error-envelope.yaml` — RFC 7807 (CSV #17).
- `_shared/security-schemes.yaml` — service JWT (CSV #18).
- `_shared/common-headers.yaml` — `traceparent` + `Idempotency-Key`.
- `_shared/capabilities.yaml` — `Capabilities` shape (CSV #21).
- `_shared/idempotency.yaml` — 409 in-flight response.
- `_shared/retry.yaml` — 429 with `Retry-After`.

## Supported archive types

Initial scope:

- `application/zip`
- `application/mbox` (RFC 4155)
- `application/vnd.ms-outlook` (`.pst`)

`.tar`, `.tar.gz`, `.7z` and other formats are out-of-scope for the initial cut; add them by extending the parser dispatch and bumping the contract.

## Implementation status

**Contract + module skeleton only (Phase 1.1, first PR).** Generated stubs, parser layer, MinIO source/sink, idempotency store, audit emission, health indicators, metrics, tracing, JWT, and Dockerfile land in subsequent PRs that mirror the Tika service's 0.5.2–0.5.6 sequence — see `version-2-implementation-plan.md` and `docs/service-template.md`.
