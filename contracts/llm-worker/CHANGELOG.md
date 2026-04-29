# Changelog — `llm-worker/`

Format: [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

## [0.1.0] — 2026-04-29

### Added

- `openapi.yaml` — initial OpenAPI 3.1.1 declaration of `gls-llm-worker`. Same shape as `gls-slm-worker` (the SLM contract was authored first as the template) so the cascade router can dispatch to either tier through identical client code.
  - `POST /v1/classify` — synchronous LLM classification (sync 200 / async 202 with `Prefer: respond-async`).
  - `GET /v1/jobs/{nodeRunId}` — async classify poll surface (CSV #47).
  - `GET /v1/capabilities` — per CSV #21.
  - `GET /actuator/health` — liveness + provider-readiness gate.
- Cross-references `_shared/` for the error envelope, service-account JWT, `traceparent` / `Idempotency-Key` / `Prefer` headers, in-flight 409, 429 retry, `TextPayload`, `Capabilities`.
- Phase 1.6 first cut: contract + JVM module + a stub `NotConfiguredLlmService` returning `LLM_NOT_CONFIGURED` 503. The Anthropic / Ollama integration lifts from `gls-llm-orchestration` in PR2.
