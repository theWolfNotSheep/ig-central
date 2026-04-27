---
title: contracts/extraction
lifecycle: forward
---

# `contracts/extraction/`

OpenAPI 3.1.1 spec for `gls-extraction-tika` — the Phase 0.5 reference service. Synchronous text extraction; first per-service contract in the v2 cutover, and the cloneable pattern future services copy.

## Layout

- `openapi.yaml` — the spec.
- `VERSION` — semver, currently `0.1.0`.
- `CHANGELOG.md` — append-only.
- `generated/` — generated server stubs + client SDKs land here once the build wires them in (Phase 0.5.2).

## Operations

| Method + path | Op id | Notes |
|---|---|---|
| `POST /v1/extract` | `extractDocument` | Tika extraction. `Idempotency-Key` honoured (CSV #16). Returns `TextPayload` (inline ≤ 256 KB else `textRef` to MinIO) per CSV #19. |
| `GET /v1/capabilities` | `getCapabilities` | Mime-type families + flags per CSV #21. |
| `GET /actuator/health` | `getHealth` | Liveness + readiness; readiness checks Tika init + MinIO reach. |

## Cross-references

- `_shared/error-envelope.yaml` — RFC 7807 (CSV #17).
- `_shared/security-schemes.yaml` — service JWT (CSV #18).
- `_shared/common-headers.yaml` — `traceparent` + `Idempotency-Key`.
- `_shared/text-payload.yaml` — `TextPayload` shape (CSV #19).
- `_shared/capabilities.yaml` — `Capabilities` shape (CSV #21).
- `_shared/idempotency.yaml` — 409 in-flight response.
- `_shared/retry.yaml` — 429 with `Retry-After`.

## Implementation status

**Contract only (Phase 0.5.1).** Maven module + Dockerfile + generated stubs land in 0.5.2; full happy + unhappy path with audit, traces, JWT, metrics in 0.5.3 — see `version-2-implementation-plan.md`.
