# Changelog — `extraction-audio/`

Format: [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

## [0.1.0] — 2026-04-29

### Added

- `openapi.yaml` — initial OpenAPI 3.1.1 declaration of `igc-extraction-audio`. Four operations:
  - `POST /v1/extract` — sync (200) by default; with `Prefer: respond-async` returns 202 + `Location: /v1/jobs/{nodeRunId}` (CSV #13 / #47). Pluggable transcription backend per CSV #46.
  - `GET /v1/jobs/{nodeRunId}` — polls async transcription jobs.
  - `GET /v1/capabilities` — capability advertisement per CSV #21.
  - `GET /actuator/health` — liveness + readiness.
- Cross-references `_shared/` for the error envelope (RFC 7807, CSV #17), service JWT (CSV #18), `traceparent` + `Idempotency-Key` headers (CSV #16 / #20), in-flight 409, 429 retry, `TextPayload`, `Capabilities`.
- 503 response carries `code=AUDIO_NOT_CONFIGURED` for builds without a configured transcription provider.
- Initial supported mime types: `audio/mpeg`, `audio/mp4`, `audio/wav`, `audio/x-wav`, `audio/flac`, `audio/ogg`, `audio/webm`.
