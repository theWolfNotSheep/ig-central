# `contracts/llm-worker/`

OpenAPI 3.1.1 contract for the `igc-llm-worker` service — the **LLM**
tier of the cascade router. Same shape as `igc-slm-worker` so the
router dispatches to either tier through identical client code.

Phase 1.6 PR1 ships the contract + JVM module + stub backend. PR2
lifts the existing `igc-llm-orchestration` Anthropic + MCP
integration into the new shape and retires the legacy Rabbit-based
dispatch.

## Conventions

- Error envelope (RFC 7807 + extensions) — CSV #17.
- Service-account JWT — CSV #18.
- `traceparent` + `Idempotency-Key` + `Prefer` — CSV #16 / #20.
- `TextPayload` — CSV #19.
- Sync / async response semantics — CSV #13 / #47.
