# `contracts/slm-worker/`

OpenAPI 3.1.1 contract for the `igc-slm-worker` service — the **SLM**
(Small Language Model) tier of the cascade router. Same shape as the
forthcoming `igc-llm-worker` rework (Phase 1.6) so the cascade can
dispatch to either tier through identical client code.

Per CSV #1 (cascade dispatch is task-agnostic), the SLM worker runs a
`PROMPT` block against an extracted text payload and returns a
classification. Two pluggable backends:

- **Anthropic Haiku** (cloud) — selected when
  `igc.slm.worker.backend=anthropic` and
  `ANTHROPIC_API_KEY` is set.
- **Ollama** (local) — selected when
  `igc.slm.worker.backend=ollama`. A local model name is configured
  per replica (e.g. `llama3.1:8b`).

Phase 1.5 first cut ships the contract + JVM module + a stub backend
(`NotConfiguredSlmService`) that returns `SLM_NOT_CONFIGURED`. Real
backends + cascade wire-in land as Phase 1.5 follow-ups.

## Conventions

- Error envelope (RFC 7807 + extensions) — CSV #17 / `_shared/error-envelope.yaml`.
- Service-account JWT — CSV #18 / `_shared/security-schemes.yaml`.
- `traceparent` + `Idempotency-Key` + `Prefer` — CSV #16 / #20 / `_shared/common-headers.yaml`.
- `TextPayload` (inline ≤ 256 KB or by reference) — CSV #19 / `_shared/text-payload.yaml`.
- `Capabilities` — CSV #21 / `_shared/capabilities.yaml`.
- Sync / async response semantics — CSV #13 / #47. Same poll surface as `igc-extraction-audio` and `igc-classifier-router`.
