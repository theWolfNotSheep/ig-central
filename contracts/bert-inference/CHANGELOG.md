# Changelog — `bert-inference/`

Format: [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

## [0.1.0] — 2026-04-29

### Added

- `openapi.yaml` — initial OpenAPI 3.1.1 declaration of `igc-bert-inference`. Five operations:
  - `POST /v1/infer` — synchronous BERT classification. Returns `MODEL_NOT_LOADED` 503 until a trained artefact is wired (Phase 1.4 follow-up).
  - `GET /v1/models` — list loaded models with version + labels + block ids.
  - `POST /v1/models/reload` — ops trigger to re-fetch the configured ONNX from MinIO.
  - `GET /v1/capabilities` — per CSV #21.
  - `GET /actuator/health` — liveness + model-readiness gate.
- Cross-references `_shared/` for the error envelope (RFC 7807, CSV #17), service-account JWT (CSV #18), `traceparent` header (CSV #20), `TextPayload` (CSV #19), `Capabilities` (CSV #21).
- No idempotency endpoint — inference is stateless and the cascade router's idempotency layer covers replay correlation upstream.
