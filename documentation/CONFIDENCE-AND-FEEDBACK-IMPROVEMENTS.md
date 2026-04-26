# Confidence Scoring & Feedback Loop Improvements

## Current State

Today, the classification pipeline produces:

- A **single scalar confidence score** (0.0–1.0) from the LLM, stored on `DocumentClassificationResult.confidence`
- A **free-form `reasoning` string** with no enforced structure
- **Binary review routing** — anything below the 0.7 threshold goes to the review queue
- **Correction feedback** via `CorrectionHistoryTool`, returned to the LLM as formatted text summaries before the next classification

This works, but we are trusting a single self-reported number with no way to validate it, no breakdown of what the LLM is unsure about, and no mechanism to detect when confidence is systematically inflated.

---

## Backing Up the Confidence Score

### 1. Multi-Dimensional Confidence Breakdown

Instead of one number, we ask the LLM to return confidence per decision axis:

| Dimension | What It Measures |
|---|---|
| `categoryConfidence` | How sure the model is about the taxonomy category |
| `sensitivityConfidence` | How sure the model is about the sensitivity level |
| `piiConfidence` | How sure the model is that it caught all PII without over-flagging |
| `metadataConfidence` | How sure the model is about extracted metadata field values |

The review threshold can then route on the **minimum dimension** — for example, "confident about category but unsure about sensitivity" still triggers review, and the reviewer sees exactly **what** to focus on rather than reviewing the entire classification blind.

We add these fields to `DocumentClassificationResult` and the `save_classification_result` MCP tool.

### 2. Structured Reasoning with Evidence and Counter-Evidence

Instead of free-form `reasoning`, we ask the LLM to return a structured object:

```json
{
  "supporting_signals": [
    "Letterhead matches HR template",
    "Contains employee name and leave dates"
  ],
  "contradicting_signals": [
    "No explicit confidentiality marking",
    "Could be a draft rather than a final document"
  ],
  "correction_influence": "2 past corrections for this category suggest sensitivity should be OFFICIAL, not PUBLIC",
  "alternative_categories": [
    {
      "category": "HR > General Correspondence",
      "why_rejected": "Specific leave dates make this a leave record, not general correspondence"
    }
  ]
}
```

This lets reviewers — and future LLM calls — understand **why** the confidence is what it is and which signals were ambiguous. It also makes the review queue far more useful: a reviewer can immediately see the trade-offs the model considered rather than starting from scratch.

### 3. Calibration Tracking — Is the Confidence Actually Accurate?

We already store `originalConfidence` on `ClassificationCorrection`. We can build a calibration view on top of this:

- **When the LLM says 0.9, how often is it actually right?** We group corrections by confidence bucket (0.8–0.9, 0.9–1.0, etc.) and calculate the actual accuracy rate per bucket.
- If the LLM says 0.95 but gets overridden 30% of the time for a certain category, we know confidence is inflated for that category.
- This feeds into **per-category threshold adjustment** — some categories might need a 0.85 review threshold, others might be safe at 0.6.

### 4. Confidence-Over-Time Tracking Per Category

We introduce a new collection or summary fields to track classification performance over time:

```json
{
  "categoryId": "...",
  "mimeType": "application/pdf",
  "avgConfidence": 0.82,
  "correctionRate": 0.15,
  "sampleSize": 47,
  "lastUpdated": "2026-04-08"
}
```

This lets us answer: "Are we getting better at classifying HR documents?" It also powers the **model step-down decision** — when `correctionRate` drops below a threshold for a category, that category is safe for a cheaper model (e.g., moving from Sonnet to Haiku).

---

## Improving the Feedback Loop

### 5. Weighted Correction Influence

Currently `getCorrectionsSummaryForLlm` returns the last 10 corrections as flat text with no weighting. We can improve this:

- **Weight by recency** — recent corrections matter more than old ones
- **Weight by similarity** — corrections on documents with similar MIME type, size, and keywords should rank higher
- **Include the confidence the LLM had when it was wrong** — telling the model "You said 0.92 confidence on this one and the human changed the category" is more instructive than just "category was changed"

### 6. Negative Feedback on Approvals

`APPROVED_CORRECT` corrections are currently just a positive signal. But they are also useful as **anchors** — we can tell the LLM "here are 5 documents you got right in this category, here is what they looked like." This grounds the few-shot examples with both correct and incorrect cases, giving the model a balanced picture rather than only showing it past mistakes.

### 7. Feedback-to-Prompt Automation

Today, feedback accumulates and admins manually trigger "Improve with AI" on blocks. We can automate a **confidence-triggered prompt revision**: when calibration data shows a category's correction rate exceeds a threshold, we automatically flag the PROMPT block for that category and generate a draft revision incorporating recent corrections. The admin still approves and publishes, but the system surfaces the problem and proposes the fix.

---

## Priority

The biggest impact for the least effort:

1. **Multi-dimensional confidence (Section 1)** and **structured reasoning (Section 2)** — these are purely prompt and schema changes, require no ML infrastructure, and immediately make the review queue more useful and the feedback loop more targeted.
2. **Calibration tracking (Section 3)** — straightforward aggregation over existing data, enables per-category threshold tuning.
3. **Weighted correction influence (Section 5)** — improves the quality of few-shot examples the LLM receives without changing the architecture.
4. **Confidence-over-time tracking (Section 4)** and **feedback-to-prompt automation (Section 7)** — longer-term improvements that build on the foundations above.
