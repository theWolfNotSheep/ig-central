# Changelog — `slm-worker/`

Format: [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

## [0.1.0] — 2026-04-29

### Added

- `openapi.yaml` — initial OpenAPI 3.1.1 declaration of `gls-slm-worker`. Six operations:
  - `POST /v1/classify` — synchronous SLM classification (sync 200 / async 202 with `Prefer: respond-async`). Caller supplies a `PROMPT` block coordinate + text payload; service routes to the configured backend (Anthropic Haiku cloud or Ollama local).
  - `GET /v1/jobs/{nodeRunId}` — poll an async classification job. Same shape as `gls-extraction-audio` and `gls-classifier-router`'s async surface (CSV #47).
  - `GET /v1/backends` — list available SLM backends with their config + readiness.
  - `GET /v1/capabilities` — per CSV #21.
  - `GET /actuator/health` — liveness + backend-readiness gate.
- Cross-references `_shared/` for the error envelope (RFC 7807, CSV #17), service-account JWT (CSV #18), `traceparent` + `Idempotency-Key` + `Prefer` headers (CSV #16 / #20), in-flight 409, 429 retry, `TextPayload` (CSV #19), `Capabilities` (CSV #21).
- Phase 1.5 first cut: schema lands now; the `gls-slm-worker` module ships with a stub `NotConfiguredSlmService` that returns `SLM_NOT_CONFIGURED` 503 until either Anthropic Haiku creds or a local Ollama endpoint is wired.
