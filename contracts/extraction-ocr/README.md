---
title: contracts/extraction-ocr
lifecycle: forward
---

# `contracts/extraction-ocr/`

OpenAPI 3.1.1 spec for `igc-extraction-ocr` — Phase 1.1 OCR service. Synchronous Tesseract-based OCR over images and image-only PDFs. Cloned from the `igc-extraction-tika` reference pattern (Phase 0.5).

## Operations

| Method + path | Op id | Notes |
|---|---|---|
| `POST /v1/extract` | `extractOcr` | Tesseract OCR over image / scanned-PDF bytes. Per-request `languages` array selects Tesseract language packs; default `["eng"]`. `Idempotency-Key` honoured (CSV #16). Returns `TextPayload` (inline ≤ 256 KB else `textRef`) per CSV #19. |
| `GET /v1/capabilities` | `getCapabilities` | Supported mime families + installed language packs + flags per CSV #21. |
| `GET /actuator/health` | `getHealth` | Liveness + readiness; readiness checks Tesseract reachable + MinIO reach. |

## Engine choice

Tesseract via `net.sourceforge.tess4j:tess4j` 5.13.0 — see CSV #45. Local OCR rather than managed (Document AI / Textract) keeps the v2 stack on-prem-friendly per CSV #38, avoids per-page cost, and keeps document bytes inside the container fleet for compliance. The contract surface is engine-agnostic, so a future managed-OCR variant (`igc-extraction-ocr-cloud`) can ship behind the same operations.

## Cross-references

- `_shared/error-envelope.yaml` — RFC 7807 (CSV #17).
- `_shared/security-schemes.yaml` — service JWT (CSV #18).
- `_shared/common-headers.yaml` — `traceparent` + `Idempotency-Key`.
- `_shared/text-payload.yaml` — `TextPayload` shape (CSV #19).
- `_shared/capabilities.yaml` — `Capabilities` shape (CSV #21).
- `_shared/idempotency.yaml` — 409 in-flight response.
- `_shared/retry.yaml` — 429 with `Retry-After`.

## Supported mime types

Initial scope:

- `image/png`
- `image/jpeg`
- `image/tiff`
- `image/bmp`
- `application/pdf` — image-only / scanned PDFs (text-extractable PDFs go to Tika).

Other formats (e.g. `image/webp`, `image/heic`) added by extending the runtime's parser dispatch + bumping the contract.
