---
title: gls-extraction-ocr
lifecycle: forward
---

# gls-extraction-ocr

Phase 1.1 OCR service. Tesseract via Tess4J (CSV #45). Synchronous OCR over images and image-only PDFs; returns text inline (≤ 256 KB) or by reference per CSV #19. Cloned from the `gls-extraction-tika` reference pattern.

## Status

ZIP / MBOX / PST archive walkers shipped under `gls-extraction-archive`; this service handles the image-and-scanned-PDF surface. Substantively complete bar the standard blockers:

- **JWT** — blocked on JWKS infra.
- **Native-engine integration tests** — blocked on issue #7 (and on building a runtime image that Testcontainers can launch with the apt-installed tesseract binary).
- **PDF page count** — `OcrResult.pageCount` is best-effort; the follow-up wires PDFBox in to expose it.

## Engine + language packs

- Engine: Tesseract via `net.sourceforge.tess4j:tess4j` 5.13.0.
- Default language: `eng`.
- Additional language packs installed via the Dockerfile's `OCR_LANGUAGES` build arg (`docker build --build-arg OCR_LANGUAGES="eng fra deu"`). Per-request `languages` array selects from what's installed; unknown / uninstalled languages return 422 with code `OCR_LANGUAGE_UNSUPPORTED`.
- Tessdata path: `${TESSDATA_PREFIX}` (set to `/usr/share/tesseract-ocr/5/tessdata` on the runtime image, override via env).

## Cross-references

- `contracts/extraction-ocr/openapi.yaml` — the contract.
- `version-2-decision-tree.csv` #45 — engine choice rationale (Tesseract over managed OCR).
- `docs/service-template.md` — generic v2 service pattern.
- `backend/gls-extraction-tika/` — reference implementation; this service clones the controller / source / sink / idempotency / audit shape.
