# Classification Failure Considerations

Notes on observed classification failures and the options to address them. Park
this until we revisit — no code changes triggered yet.

## Symptom

Documents land in `CLASSIFICATION_FAILED` with `lastError = "LLM did not produce
a classification result"`. The matching `ai_usage_log` rows show:

- `status = NO_RESULT`
- `errorMessage = "LLM did not call save_classification_result"`
- `outputTokens = 0`
- `response` contains a prose conclusion (e.g. "Based on the content, I would
  classify this as Confidential...") rather than a tool invocation
- Durations vary widely (22s – 140s); the 140s case was a long playbook PDF

## Root cause

The classification pipeline only accepts results delivered via the MCP tool
`save_classification_result`. The LLM is instead writing prose answers and
never calling the tool, so the worker has no structured result to persist.

This is **a model behaviour problem**, not an infrastructure problem:
- The taxonomy is correctly populated (72 active categories at time of failure)
- MCP tools are reachable (the LLM successfully calls
  `get_classification_taxonomy`, `get_correction_history`,
  `get_org_pii_patterns`, `get_metadata_schemas` earlier in the same call)
- The failure consistently happens at the *final* step — wrapping the conclusion
  in a tool call

### Why it's happening

1. **Tool-calling reliability is model-dependent.** All failures observed used
   `qwen2.5:32b` via Ollama. Qwen has tool support but is notably weaker than
   Claude at remembering to invoke tools after multi-step reasoning.
2. **Multi-tool flows weaken the final commitment.** After the model has
   already invoked 4 MCP tools to gather context, it tends to "summarise" rather
   than tool-call again.
3. **Long context degrades behaviour.** The 140s playbook case shows the model
   wandering — at one point it claimed "no classification categories have been
   defined" despite the `get_classification_taxonomy` tool clearly returning
   72 categories. Likely context-pollution / mid-context loss.
4. **One-shot failure is terminal.** A single missed tool-call sends the doc to
   `CLASSIFICATION_FAILED`; no automatic retry, even though the model often
   succeeds on a second attempt.

## Options to address (when we come back to this)

Listed in increasing effort. Combine as appropriate.

### A. Switch the model (no code changes)

- **Claude (cloud)** via Settings → AI & Classification → LLM Provider →
  Anthropic. Tool-calling is materially more reliable. Cost ~$0.01-0.05 per
  doc. Best short-term option for production reliability.
- **`qwen2.5:72b` instead of 32b** if staying on Ollama. Larger model has
  noticeably better tool-call adherence. Needs ~42 GB RAM.

### B. Tighten the system prompt

Find the active CLASSIFICATION prompt block in the Block Library and append a
hardline final instruction:

> After analysing the document, you MUST call `save_classification_result`.
> Do not produce a written conclusion — only the tool call. If you cannot
> determine a confident category, call the tool with the most specific
> applicable category and a low confidence score.

Cheap, model-agnostic improvement. Won't fix all cases but should help.

### C. Auto-retry once on NO_RESULT

Currently `ClassificationPipeline` sets `CLASSIFICATION_FAILED` after a single
NO_RESULT. Add a single automatic retry with a stricter "you forgot the tool"
prompt before giving up. Many models succeed second try. Needs:
- A retry counter on the document (`classificationAttempts`)
- A different system prompt for retries (or just a prepended reminder)
- Cap at 1-2 retries to bound cost/latency

### D. Hard timeout per classification call

The 140s case wasted a worker. Add a hard `Duration` cap (e.g. 60s); if the
LLM hasn't tool-called by then, abort the call and surface as failure. Frees
the worker for the next message.

### E. Fallback: parse prose response (not recommended)

Last resort — when a NO_RESULT happens but a prose response exists, attempt
to extract category/sensitivity from the text via regex or a second tiny LLM
pass. Fragile and creates inconsistent provenance — better to retry with
proper tool-calling.

### F. Switch to a structured-output model

Some models (Claude, OpenAI GPT-4o) support strict JSON-schema outputs that
are guaranteed to conform. If we ever drop tool-calling in favour of structured
JSON, the "forgot the tool" failure mode vanishes. Larger refactor.

## Recommended sequence (when revisited)

1. **Switch to Claude in Settings** — instant fix, validate the rest of the
   pipeline is healthy.
2. **Add the retry on NO_RESULT** (option C) and the hard timeout (option D) —
   makes the platform robust regardless of which model is chosen.
3. **Harden the classification prompt** (option B) — improves baseline for any
   model.
4. **Re-test with `qwen2.5:72b`** — if it works reliably with the retry +
   tightened prompt, can return to fully-local for cost/privacy.

## Affected files (for reference)

- `backend/gls-llm-orchestration/src/main/java/co/uk/wolfnotsheep/llm/pipeline/ClassificationPipeline.java`
- `backend/gls-app-assembly/src/main/java/co/uk/wolfnotsheep/infrastructure/services/pipeline/SyncLlmNodeExecutor.java`
- Active CLASSIFICATION prompt block (managed via Block Library UI)
- `Document` model: `lastError`, `lastErrorStage`, `failedAt`, `retryCount`
- `AiUsageLog`: `status` (NO_RESULT), `errorMessage`, `response`
