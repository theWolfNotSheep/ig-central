# Changelog — `extraction-ocr/`

Format: [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

## [0.1.0] — 2026-04-29

### Added

- `openapi.yaml` — initial OpenAPI 3.1.1 declaration of `igc-extraction-ocr`. Three operations:
  - `POST /v1/extract` — synchronous OCR (Tesseract via Tess4J per CSV #45). Returns `TextPayload` (inline ≤ 256 KB else `textRef`) per CSV #19. Per-request `languages` array selects Tesseract language packs.
  - `GET /v1/capabilities` — capability advertisement per CSV #21.
  - `GET /actuator/health` — liveness + readiness.
- Cross-references `_shared/` for the error envelope (RFC 7807, CSV #17), service-account JWT (CSV #18), `traceparent` + `Idempotency-Key` headers (CSV #16 / #20), in-flight 409, 429 retry, `TextPayload`, `Capabilities`.
- Initial supported mime types: `image/png`, `image/jpeg`, `image/tiff`, `image/bmp`, `application/pdf` (image-only). Text-extractable PDFs route to `igc-extraction-tika` instead.
