# Changelog — `classifier-router/`

Format: [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

## [0.2.0] — 2026-04-29

### Added

- `Prefer: respond-async` semantics on `POST /v1/classify` (CSV #13 / #47). Without the header the call blocks and returns 200 with the cascade result. With `respond-async`, the call returns 202 with `Location: /v1/jobs/{nodeRunId}` and a `JobAccepted` body; the cascade proceeds in the background.
- `GET /v1/jobs/{nodeRunId}` — poll the state of an async cascade. Returns `PENDING` / `RUNNING` / `COMPLETED` / `FAILED`; `COMPLETED` carries the same `ClassifyResponse` shape as the sync 200.
- `JobAccepted` and `JobStatus` schemas. Sync and async paths share the same idempotency row, so a `Prefer: respond-async` retry after a completed sync run returns the cached result via the poll URL.

### Notes

- Backwards compatible — sync clients that don't send `Prefer` see no behaviour change. New 202 + `/v1/jobs` are additive.

## [0.1.0] — 2026-04-29

### Added

- `openapi.yaml` — initial OpenAPI 3.1.1 declaration of `igc-classifier-router`. Three operations:
  - `POST /v1/classify` — task-agnostic cascade dispatch. Idempotent on `nodeRunId` per CSV #16. Block coordinates per CSV #14 (version absent = active). Phase 1.2 first cut returns `tierOfDecision=MOCK` until BERT / SLM / LLM are wired in 1.4–1.6.
  - `GET /v1/capabilities` — per CSV #21.
  - `GET /actuator/health` — liveness + readiness.
- Cross-references `_shared/` for the error envelope (RFC 7807, CSV #17), service-account JWT (CSV #18), `traceparent` + `Idempotency-Key` headers (CSV #16 / #20), in-flight 409, 429 retry, `TextPayload` (CSV #19), `Capabilities` (CSV #21).
- `cascadeTrace` array on the response body — empty / single-element in the first cut; populated as tiers wire in.
