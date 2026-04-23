# Pipeline Diagnostics — Demo Script

**Duration: 2:30**

**Goal:** Show the deep-inspection tooling admins use to debug individual document journeys — every block, every prompt, every LLM response.

## Script

1. **Open with the admin pain.** *"When a classification goes wrong, the natural question is 'why?' Most platforms give you a final result and nothing else. Pipeline Diagnostics gives you every step, every prompt, every response, every token."*

2. **Navigate to Admin → AI → Diagnostics.**

3. **Search for a document** — paste a document ID or slug. Load its diagnostic trace.

4. **Walk the pipeline trace.** *"Four stages — text extraction, PII scan, classification, governance enforcement. Each one expands to show its inputs, its outputs, and its duration. You see where time was spent."*

5. **Drill into the classification step.**
   - *"The block version used — and a link to view it."*
   - *"The full system prompt and user prompt sent to the model."*
   - *"The MCP tool calls made during inference — `get_correction_history` returned three examples, here they are."*
   - *"The full model response — JSON, reasoning, confidence."*

6. **Show token accounting.** *"Tokens in, tokens out, model used, cost to the penny. Useful when you're trying to track down a spike in AI spend."*

7. **Show the comparison view.** *"Pick two documents that were classified differently and compare their traces side by side. Often the difference is visible in the MCP tool responses — one document's past corrections led the model one way, the other's led it another."*

8. **Enable trace on a live pipeline.** *"For production debugging, you can toggle tracing on specific pipelines or blocks — don't trace everything (it doubles storage), but trace what you need while you're investigating."*

9. **Close with the ops story.** *"When your records manager reports 'something weird happened with this document' — your admin has an answer in minutes, not hours."*

**Key message:** *"AI isn't a black box when you can open it. Every decision, every prompt, every cost — visible and replayable for every document."*
