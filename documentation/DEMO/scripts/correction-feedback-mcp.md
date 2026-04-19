# Correction Feedback via MCP Tools — Demo Script

**Duration: 3:00**

**Goal:** Show how the LLM actively consults past human corrections at inference time via MCP tools — turning corrections into real-time guidance, not just training data.

## Script

1. **Open with the differentiator.** *"Most AI classifiers retrain on corrections — which means your feedback only helps after a model rebuild, weeks later. We do something different. Every classification, the model asks our platform 'what did humans correct last time for documents like this?' — and adjusts in real time."*

2. **Explain the MCP server briefly.** *"MCP — Model Context Protocol — is the bridge. Our LLM has tools registered through MCP that it can call during classification. Two of them are purpose-built for the feedback loop."*

3. **Show the first tool: `get_correction_history`.** *"Before the model commits to a classification, it calls this tool with the document's category and mime type. The tool returns past corrections for similar documents — original decision, corrected decision, reason. Effectively few-shot learning at inference time."*

4. **Show the second tool: `get_org_pii_patterns`.** *"Before PII analysis, the model calls this one. It returns PII types your organisation has flagged before — maybe you have a specific internal staff ID format, or a customer reference number. Patterns learnt from your corrections, not hardcoded."*

5. **Walk a live classification.** Open a document and trigger reprocess. Show the pipeline diagnostics view (or LLM trace log). *"Here in the trace — the model called `get_correction_history`. It received three past corrections for HR documents. It used them to choose the correct category. You can see the before-and-after in the reasoning."*

6. **Show the impact over time.** *"The more corrections accumulate, the richer the tool responses. The model's job shifts from open-ended classification to pattern matching against your examples. That lets us step down from Sonnet to Haiku without losing accuracy — which cuts your LLM bill by roughly 10x."*

7. **Tie it back to records managers.** *"For your records team, this means their five seconds of correction work compounds into thousands of correct future classifications — with no retraining cycle, no data science team required."*

**Key message:** *"Your humans-in-the-loop become a live training signal. Corrections influence the very next classification — not the next model release."*
