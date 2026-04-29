# Changelog — `extraction-archive/`

Format: [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

## [0.1.0] — 2026-04-29

### Added

- `openapi.yaml` — initial OpenAPI 3.1.1 declaration of `gls-extraction-archive`. Three operations:
  - `POST /v1/extract` — synchronous archive unpack. Idempotent on `nodeRunId` per CSV #16. Returns the list of direct child `documentRef`s landed in MinIO. One-level walk per invocation; nested archives re-route through this service via the orchestrator (CSV #43). `costUnits` per CSV #22.
  - `GET /v1/capabilities` — capability advertisement per CSV #21.
  - `GET /actuator/health` — liveness + readiness.
- Cross-references `_shared/` for the error envelope (RFC 7807, CSV #17), service-account JWT (CSV #18), `traceparent` + `Idempotency-Key` headers (CSV #16 / #20), in-flight 409 (idempotency), 429 retry, `Capabilities`.
- 4XX / 5XX catch-alls keep the spec lint-clean while concrete codes (413, 422, 429, 409) document the unhappy paths the implementation must recognise. The 413 envelope's `code` extension identifies which configured cap was hit (size / child-count / nesting depth).
- Initial supported archive types: `application/zip`, `application/mbox`, `application/vnd.ms-outlook` (`.pst`).
