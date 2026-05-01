---
title: igc-extraction-audio
lifecycle: forward
---

# igc-extraction-audio

Phase 1.1 audio transcription service. Sync (200) by default; with `Prefer: respond-async` returns 202 + poll URL per CSV #13 / #47. Pluggable backend per CSV #46.

## Status

Substantively complete:

- Contract surface — sync + async paths, idempotency shared between them via the `audio_jobs` Mongo collection.
- Backend interface + two impls in this repo: **`OpenAiWhisperService`** (cloud, multipart upload to `api.openai.com/v1/audio/transcriptions`) and **`NotConfiguredAudioTranscriptionService`** (default; returns `503 AUDIO_NOT_CONFIGURED`).
- Async dispatch via Spring `@Async` to the bounded `audioAsyncExecutor` thread pool.
- Audit, health, metrics, RFC 7807 mapping, Dockerfile + Compose entry.

Outstanding:

- **Local Whisper** (`whisper.cpp` / ggml bindings) and **Deepgram** — both deferred behind the same `AudioTranscriptionService` interface.
- **JWT** — blocked on JWKS infra.
- **Real-backend integration tests** — blocked on issue #7 + an `OPENAI_API_KEY` available to the test runner.

## Configuration

| Property | Default | Notes |
|---|---|---|
| `igc.extraction.audio.provider` | `none` | `openai` swaps in `OpenAiWhisperService`. |
| `igc.extraction.audio.openai.api-key` | _empty_ | Provide via `OPENAI_API_KEY`. Falls back to not-configured if blank. |
| `igc.extraction.audio.openai.model` | `whisper-1` | OpenAI Whisper model id. |
| `igc.extraction.audio.openai.endpoint` | `https://api.openai.com/v1/audio/transcriptions` | Overridable for proxies / Azure-OpenAI. |
| `igc.extraction.audio.openai.timeout` | `PT10M` | HTTP timeout per request. |
| `igc.extraction.audio.async.max-size` | `8` | Upper bound on concurrent async jobs per replica. |
| `igc.extraction.audio.max-source-bytes` | `524288000` (500 MB) | 413 cap. |
| `igc.extraction.audio.inline-byte-ceiling` | `262144` (256 KB) | Inline vs textRef threshold per CSV #19. |

## Cross-references

- `contracts/extraction-audio/openapi.yaml` — the contract.
- CSV #13 / #46 / #47 — sync vs async; backend choice; async semantics.
- `docs/service-template.md` — generic v2 service pattern.
