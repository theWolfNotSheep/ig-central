# Changelog — `extraction/`

Format: [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

## [0.1.1] — 2026-04-27

### Changed

- `HealthResponse.components.description` reformatted as a folded scalar. The previous inline form contained an embedded backticked snippet ending in a colon, which the snakeyaml parser used by `openapi-generator-maven-plugin` interpreted as a YAML mapping key. Pure docstring change; no semantic shift.

## [0.1.0] — 2026-04-27

### Added

- `openapi.yaml` — initial OpenAPI 3.1.1 declaration of `igc-extraction-tika`. Three operations:
  - `POST /v1/extract` — synchronous text extraction. Idempotent on `nodeRunId` per CSV #16. Returns `TextPayload` (inline ≤ 256 KB else `textRef`) per CSV #19. `costUnits` per CSV #22.
  - `GET /v1/capabilities` — capability advertisement per CSV #21.
  - `GET /actuator/health` — liveness + readiness.
- Cross-references `_shared/` for the error envelope (RFC 7807, CSV #17), service-account JWT (CSV #18), `traceparent` + `Idempotency-Key` headers (CSV #16 / #20), in-flight 409 (idempotency), 429 retry, `TextPayload`, and `Capabilities`.
- 4XX / 5XX catch-alls keep the spec lint-clean while concrete codes (413, 422, 429, 409) document the unhappy paths the implementation must recognise.
