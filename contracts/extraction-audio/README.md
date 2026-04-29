---
title: contracts/extraction-audio
lifecycle: forward
---

# `contracts/extraction-audio/`

OpenAPI 3.1.1 spec for `gls-extraction-audio` — Phase 1.1 audio transcription. Sync (`200`) or async (`202` + poll) via the `Prefer: respond-async` header per CSV #13 / #47. Pluggable backend per CSV #46 (default impls in this repo: OpenAI Whisper cloud + a `not-configured` fallback).

## Operations

| Method + path | Op id | Notes |
|---|---|---|
| `POST /v1/extract` | `extractAudio` | Sync (200) by default; with `Prefer: respond-async` returns 202 + `Location: /v1/jobs/{nodeRunId}`. `Idempotency-Key` honoured (CSV #16). Returns `TextPayload` (inline ≤ 256 KB else `textRef`) per CSV #19. |
| `GET /v1/jobs/{nodeRunId}` | `getJob` | Poll the state of an async job. |
| `GET /v1/capabilities` | `getCapabilities` | Provider + supported mime types + flags. |
| `GET /actuator/health` | `getHealth` | Provider readiness. |

## Backend

`AudioTranscriptionService` is an interface; the runtime chooses an implementation via `gls.extraction.audio.provider`:

- `openai` — `OpenAiWhisperService`, calls `api.openai.com/v1/audio/transcriptions` with model `whisper-1`. Auth via `OPENAI_API_KEY`. Per-request `language` + `prompt` honoured.
- `none` (default) — `NotConfiguredAudioTranscriptionService`, returns `503 AUDIO_NOT_CONFIGURED` so deployments without a configured backend fail loud rather than silently.

Local Whisper (`whisper.cpp` / ggml bindings) and Deepgram are deferred — both can ship behind the same interface without a contract change.

## Async semantics

Sync and async share one idempotency row in the `audio_jobs` Mongo collection. A `Prefer: respond-async` retry after a successful sync run returns the cached result via the poll URL. Async dispatch uses Spring's `@Async` worker pool; the deployment unit stays one container per service per CSV #38.

## Cross-references

- `_shared/error-envelope.yaml` — RFC 7807 (CSV #17).
- `_shared/security-schemes.yaml` — service JWT (CSV #18).
- `_shared/common-headers.yaml` — `traceparent` + `Idempotency-Key`.
- `_shared/text-payload.yaml` — `TextPayload` shape (CSV #19).
- `_shared/capabilities.yaml` — `Capabilities` shape (CSV #21).
- CSV #13 / #47 — sync vs async response model.
- CSV #46 — backend choice.
