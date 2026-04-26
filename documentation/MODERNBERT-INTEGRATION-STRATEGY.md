# ModernBERT Integration Strategy

## Why ModernBERT

ModernBERT (2024) is an updated encoder-only transformer optimised for classification, retrieval, and embedding tasks. Compared to calling an LLM (Claude, GPT, Ollama) for every document:

- **~100x cheaper** per document — runs locally on CPU or a small GPU
- **~50x faster** — millisecond inference vs multi-second LLM calls
- **Deterministic** — same input always produces the same output, no prompt sensitivity
- **Fine-tunable** — we can train it directly on our correction data, so it learns from every human override
- **8,192 token context** — long enough for most extracted document text

The goal is not to replace the LLM entirely, but to **reduce how often we need it** and **validate its answers when we do**.

---

## Where ModernBERT Fits in the Pipeline

### 1. Category Classification (Primary Use Case)

We fine-tune a ModernBERT classifier on our taxonomy categories using the documents we have already classified — both LLM-classified and human-corrected.

**How it works:**

- Train a multi-class classifier: input = extracted text, output = taxonomy category
- As corrections accumulate, we retrain periodically (nightly, or after N corrections)
- The model produces a softmax distribution — we get confidence per category for free, and it is a calibrated probability, not a self-reported guess

**Integration into the pipeline:**

```
Document text
    ↓
ModernBERT classifier
    ├─ High confidence (>0.85) + category has low correction rate
    │   → Accept classification, skip LLM entirely
    │   → Save with classifiedBy: "MODERNBERT"
    │
    ├─ Medium confidence (0.5–0.85)
    │   → Pass to LLM with ModernBERT's top-3 predictions as context
    │   → LLM confirms or overrides (cheaper prompt, fewer reasoning steps)
    │
    └─ Low confidence (<0.5)
        → Full LLM classification as today
```

**Impact:** For mature categories with enough training data, we bypass the LLM entirely. For newer categories, the LLM still handles it but gets a strong hint from ModernBERT, reducing token usage and improving accuracy.

### 2. Confidence Cross-Validation

Even when we still use the LLM, ModernBERT provides an independent confidence signal:

- If ModernBERT says "HR > Leave Records" at 0.92 and the LLM says the same at 0.88, we have high agreement — true confidence is very high
- If ModernBERT says "HR > Leave Records" at 0.91 and the LLM says "HR > General Correspondence" at 0.75, we have a disagreement — route to human review regardless of individual confidence scores
- We add an `agreementScore` field to `DocumentClassificationResult` to track this

This directly addresses the calibration problem from the confidence improvements doc — we no longer rely solely on the LLM's self-assessment.

### 3. Sensitivity Classification

A second ModernBERT head (or a separate fine-tuned model) for sensitivity level:

- Input: extracted text
- Output: PUBLIC / OFFICIAL / OFFICIAL_SENSITIVE / SECRET

This is a simpler classification task than category and should reach high accuracy quickly. Combined with the category classifier, we can handle both dimensions independently and compare against the LLM.

### 4. PII Detection (Named Entity Recognition)

ModernBERT can be fine-tuned for token-level NER to detect PII entities:

- Person names, addresses, National Insurance numbers, dates of birth, email addresses, phone numbers
- We already have PII correction data (`PII_FLAGGED`, `PII_DISMISSED`) to use as training signal
- Runs alongside the regex patterns from REGEX_SET blocks — two independent PII detection methods

**Advantages over regex alone:**
- Catches PII that does not match rigid patterns (e.g., "my mobile is oh-seven-seven..." in free text)
- Reduces false positives from regex over-matching
- Learns organisation-specific PII patterns from corrections

**Advantages over LLM PII detection:**
- Runs locally, so sensitive document text never leaves the infrastructure
- Deterministic — same document always flags the same entities
- Fast enough to run on every document without cost concerns

### 5. Document Similarity and Deduplication

ModernBERT embeddings (using the base model, not fine-tuned) give us dense vector representations of documents:

- **Near-duplicate detection** — flag documents that are >0.95 cosine similarity to an existing classified document. We can inherit the classification rather than re-running the pipeline.
- **Similar document retrieval** — when the LLM classifies a new document, we retrieve the 3 most similar already-classified documents and include them as few-shot examples. This is more targeted than the current correction history approach.
- **Cluster analysis** — identify natural groupings in the document corpus that might suggest new taxonomy categories the admin should create.

We store embeddings in Elasticsearch (dense_vector field type) alongside the existing document index.

### 6. Pre-Screening and Triage

Before any classification runs, a lightweight ModernBERT pass can:

- **Detect language** — route non-English documents to appropriate handling
- **Estimate document quality** — flag documents with very little extractable text (scanned images with poor OCR, corrupted files) before wasting LLM tokens on them
- **Identify template documents** — match against `TemplateFingerprint` entries using embeddings rather than exact matching

---

## Training Data Strategy

We already have the training data — we just need to structure it:

| Source | What It Provides | Volume |
|---|---|---|
| LLM-classified documents (high confidence, not corrected) | Positive training examples per category | Grows with every document |
| Human-approved classifications (`APPROVED_CORRECT`) | Validated positive examples — highest quality | Grows with review queue usage |
| Human-corrected classifications | The corrected label is the ground truth; the original label is a hard negative | Most valuable signal |
| PII corrections (`PII_FLAGGED`, `PII_DISMISSED`) | NER training data — true positives and false positives | Grows with PII review |

**Cold start:** We need roughly 50–100 examples per category to fine-tune effectively. Until a category reaches that threshold, it stays on LLM-only classification. The system tracks sample counts per category and automatically promotes categories to ModernBERT when they cross the threshold.

**Retraining cadence:** Nightly batch retrain, or triggered when correction count since last train exceeds a threshold (e.g., 20 new corrections). The new model is evaluated against a held-out validation set before it replaces the active model — no silent regressions.

---

## Architecture Integration

### New Pipeline Block Type

We add a `MODEL` block type to the block library:

| Field | Value |
|---|---|
| Type | `MODEL` |
| Content | Model artifact reference, training config, label mappings |
| Versioned | Yes — every retrain produces a new version |
| Feedback | Same correction loop as PROMPT blocks |

This keeps ModernBERT models under the same version control and feedback system as prompts and regex patterns.

### New Pipeline Stage

The pipeline gains a `PRE_CLASSIFY` stage before the existing `CLASSIFYING` stage:

```
UPLOADED → PROCESSING → PROCESSED → PRE_CLASSIFY → CLASSIFYING → CLASSIFIED → GOVERNANCE_APPLIED
                                         │
                                         ├─ ModernBERT high confidence → skip to CLASSIFIED
                                         └─ ModernBERT low/medium confidence → continue to CLASSIFYING (LLM)
```

### Deployment

ModernBERT runs as a lightweight Python sidecar service (FastAPI + HuggingFace Transformers) or as a Java-native inference via ONNX Runtime:

**Option A — Python sidecar (recommended for flexibility):**
- New Docker container: `gls-bert-service`
- Exposes `/classify`, `/embed`, `/ner` endpoints
- Called from `ClassificationPipeline` via HTTP before the LLM step
- Model files stored in a Docker volume, swapped on retrain

**Option B — ONNX in Java (recommended for simplicity):**
- Export fine-tuned ModernBERT to ONNX format
- Load in `gls-llm-orchestration` via `onnxruntime` Java bindings
- No additional container, but less flexibility for retraining

### Tracking and Observability

Every document records:

- `classifiedBy`: `"LLM"`, `"MODERNBERT"`, or `"MODERNBERT_LLM"` (ModernBERT suggested, LLM confirmed)
- `bertConfidence`: ModernBERT's confidence (independent of LLM confidence)
- `bertCategory`: ModernBERT's predicted category (even if LLM overrode it)
- `agreementScore`: whether ModernBERT and LLM agreed

This lets us build dashboards showing: what percentage of documents ModernBERT handles alone, where it disagrees with the LLM, and how often each is right.

---

## Cost and Performance Impact

| Metric | LLM Only (Today) | With ModernBERT |
|---|---|---|
| Classification latency | 3–8 seconds | <100ms (BERT only) / 3–8s (LLM fallback) |
| Cost per document | ~$0.01–0.03 (API tokens) | ~$0.0001 (local inference) / $0.01–0.03 (fallback) |
| Data leaves infrastructure | Yes (API call to Anthropic) | No (BERT runs locally) / Yes (LLM fallback) |
| Scales with volume | Cost scales linearly | Cost nearly flat — GPU/CPU is fixed |

As categories mature and ModernBERT handles more documents directly, the percentage hitting the LLM drops. We expect 60–80% of documents to be BERT-only within a few months of production use, with the LLM reserved for genuinely ambiguous cases and new categories.

---

## Priority and Phasing

### Phase 1 — Category Classifier
Fine-tune ModernBERT on existing classified documents. Integrate as a pre-classification step. LLM still runs but receives BERT predictions as context. Track agreement rates.

### Phase 2 — Confidence Cross-Validation and Auto-Skip
When BERT confidence is high and historical agreement rate for that category is above threshold, skip the LLM. Monitor correction rates to ensure quality holds.

### Phase 3 — Sensitivity Classifier and PII NER
Add the second classification head for sensitivity. Fine-tune a NER model for PII detection. Run alongside existing regex patterns.

### Phase 4 — Embeddings and Similarity
Index document embeddings in Elasticsearch. Enable near-duplicate detection, similar document retrieval for few-shot examples, and cluster analysis for taxonomy insights.
