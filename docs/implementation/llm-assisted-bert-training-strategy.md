# LLM-Assisted BERT Training Strategy

## Context

BERT training quality is bottlenecked by data quantity and quality, not by model architecture. With 45 samples across 18 categories (2.5 avg per category, 11 singletons), even a perfect model cannot learn. The LLM (Claude) is already classifying documents in the pipeline — but we're only using its output labels. The LLM's reasoning, language generation, and analytical capabilities can be leveraged far more aggressively to bootstrap a high-quality BERT training set.

This document outlines seven strategies, ordered by impact and implementation effort.

---

## 1. Synthetic Training Data Generation

**Problem:** Most categories have 1-4 samples. BERT needs 15-50+ per category to generalise.

**Approach:** Use the LLM to generate synthetic documents for underrepresented categories. The LLM already knows what each category contains (via the taxonomy, governance policies, and metadata schemas available through MCP tools). Ask it to generate realistic document excerpts.

**How it works:**

1. Admin selects an underrepresented category (e.g. "Audit Reports" — 1 sample)
2. System sends the LLM:
   - The category definition from the taxonomy
   - The metadata schema fields (if any)
   - The 1 existing real sample as a reference
   - The governance policy describing what belongs in this category
3. Prompt: "Generate 10 diverse, realistic text excerpts that would be classified as {category}. Vary the writing style, document type (email, report, memo, letter), length, and specificity. Each should be 200-500 words."
4. LLM returns 10 synthetic samples
5. Each is saved as a `TrainingDataSample` with `source = "LLM_SYNTHETIC"` and `verified = false`
6. Admin reviews and verifies before training

**Why this works:** The LLM understands document categories at a semantic level. It can generate a maternity leave request, a disciplinary hearing notice, and an onboarding checklist — all for "Employee Records" — because it understands the category, not because it's seen examples.

**Guardrails:**
- Synthetic samples are marked with a distinct source so they can be filtered or weighted differently
- Never generate more synthetic samples than 2x the real sample count for a category — synthetic data should supplement, not replace
- Admin must verify synthetic samples before they enter training
- Include a "diversity score" — reject synthetic samples that are too similar to each other (cosine similarity on embeddings)

**Impact:** High — directly solves the singleton category problem.

**Effort:** Medium — needs a new endpoint, prompt template, and UI button per category.

---

## 2. Intelligent Data Augmentation

**Problem:** Existing samples are single expressions of a concept. BERT overfits to specific phrasing rather than learning the underlying category.

**Approach:** Use the LLM to paraphrase existing training samples while preserving their classification-relevant content.

**How it works:**

1. For each training sample, send it to the LLM with:
   - The original text
   - The assigned category
   - Instruction: "Rewrite this document in 3 different ways. Preserve the factual content and document type but vary the tone (formal/informal), structure (bullet points/paragraphs/letter format), and vocabulary. Each version should still clearly belong to the category '{category}'."
2. LLM returns 3 variations
3. Store as augmented samples linked to the original

**Why this works:** BERT learns surface-level patterns. If all your "Contracts Management" samples start with "This agreement is entered into...", BERT learns that phrase, not the concept. Paraphrases teach it that contracts can also start with "Between the parties..." or "Service Level Agreement for..." or "WHEREAS the Client requires...".

**Key difference from synthetic generation:** Augmentation preserves the real document's facts and context. Synthetic generation creates entirely new content. Both are valuable for different reasons — augmentation adds robustness, synthetic generation adds coverage.

**Impact:** Medium-high — improves generalisation without needing new source documents.

**Effort:** Low — simpler prompt than synthetic generation, can be batch-processed.

---

## 3. Training Data Quality Audit

**Problem:** Auto-collected samples inherit the LLM's confidence as a proxy for correctness. But a sample can be high-confidence and still mislabelled (the LLM was confidently wrong), or the text can be low-quality (mostly headers, footers, boilerplate).

**Approach:** Use the LLM to audit training samples before they enter a training run.

**How it works:**

1. Before training, send each unverified sample to the LLM:
   ```
   Text: {sample.text}
   Assigned category: {sample.categoryName}

   Rate this training sample:
   1. LABEL_CORRECT: Does this text genuinely belong in this category? (yes/no/uncertain)
   2. TEXT_QUALITY: Is the text meaningful for classification? (good/poor/garbage)
   3. DISTINCTIVENESS: Does this text contain category-specific signals, or is it generic boilerplate? (distinctive/generic)
   
   Return a JSON verdict: {label_correct, text_quality, distinctiveness, reasoning}
   ```
2. Samples flagged as mislabelled, poor quality, or generic are excluded from training (or flagged for human review)
3. Store the audit result on the sample for transparency

**Why this works:** The LLM is better at judging text quality and label correctness than a confidence threshold. A document that's 90% legal boilerplate with one relevant paragraph is technically in the right category but poor training data — the LLM can spot this.

**Impact:** Medium — prevents garbage-in-garbage-out. Most valuable when auto-collection is aggressive (low confidence threshold).

**Effort:** Low-medium — batch job before training, no UI needed initially.

---

## 4. Confusion-Driven Targeted Collection

**Problem:** After training, some categories are systematically confused with each other (e.g. "Sales Playbooks" vs "Sales Training Materials"). Collecting more random samples doesn't help — you need samples that distinguish the confused categories specifically.

**Approach:** Use the LLM to analyse BERT's confusion patterns and generate targeted training data.

**How it works:**

1. After training, extract the per-class metrics (precision/recall/F1)
2. Identify confusion pairs: categories where BERT frequently predicts one when the answer is the other
3. Send to the LLM:
   ```
   BERT confuses these two categories:
   - "Sales Playbooks": {definition, examples}
   - "Sales Training Materials": {definition, examples}

   1. Explain the key distinguishing characteristics between these categories.
   2. Generate 5 examples of each that emphasise the distinguishing features.
   3. Generate 3 "borderline" examples that could go either way, with explanations of which category they belong to and why.
   ```
4. The distinguishing characteristics become classification hints
5. The targeted examples are added as training data
6. The borderline examples with explanations are the highest-value training data — they teach BERT exactly where the boundary is

**Why this works:** Standard data collection adds random samples uniformly. Confusion-driven collection adds samples exactly where the model is weakest. This is a form of active learning guided by the LLM's semantic understanding.

**Impact:** High — directly addresses the categories where BERT fails most.

**Effort:** Medium — needs confusion matrix analysis, targeted prompt, review UI.

---

## 5. Category Taxonomy Refinement

**Problem:** Some categories may be too similar to distinguish from text alone, or too broad to be a single category. The taxonomy was designed for governance, not for machine learning — what makes sense organisationally may not make sense for classification.

**Approach:** Use the LLM to analyse the taxonomy and suggest structural changes that would improve BERT's ability to classify.

**How it works:**

1. Send the LLM the full taxonomy tree, category definitions, and the training data distribution
2. Prompt:
   ```
   Given this taxonomy and training data distribution, analyse:

   1. MERGE candidates: Which categories are so similar that a classifier would struggle to distinguish them from text alone? Suggest merges with justification.
   2. SPLIT candidates: Which categories are too broad — containing documents that look very different from each other? Suggest splits.
   3. HIERARCHY suggestions: Which categories could be classified in two stages (e.g. first classify as "HR", then sub-classify as "Employee Records" vs "Recruitment")?
   4. TRAINING PRIORITY: Given limited data collection budget, rank the categories by how much incremental data would improve overall accuracy.
   ```

3. Admin reviews suggestions and restructures the taxonomy if appropriate
4. Next training run benefits from cleaner category boundaries

**Why this works:** A taxonomy with 18 fine-grained categories is harder to learn than one with 6 broad categories, each with sub-categories. The LLM can reason about semantic similarity between categories in a way that looking at the taxonomy tree alone cannot.

**Practical example:** "Supplier Payments" and "Supplier Payments (transaction)" are almost certainly confusable from text — one is a governance category, the other is a transaction type. The LLM would flag this immediately.

**Impact:** High — structural improvements compound across every future training run.

**Effort:** Low — one-time analysis, no code needed. Could be a manual prompt initially.

---

## 6. Explanation-Enriched Training (Knowledge Distillation)

**Problem:** BERT learns pattern matching on raw text. It doesn't understand *why* a document belongs to a category. This makes it brittle — a document with unusual formatting but correct content gets misclassified.

**Approach:** Use the LLM to generate classification reasoning for each training sample, then train BERT on enriched inputs.

**How it works:**

1. For each training sample, ask the LLM:
   ```
   Text: {sample.text}
   Category: {sample.categoryName}

   In 1-2 sentences, explain the key signals in this text that identify it as {category}.
   Focus on: document type indicators, domain-specific terminology, structural patterns, named entities.
   ```
2. Prepend the LLM's reasoning to the training text:
   ```
   [Classification signals: This is a supplier payment remittance advice containing 
   payment reference numbers, bank sort codes, and itemised invoice amounts]
   
   {original document text}
   ```
3. Train BERT on the enriched text
4. At inference time, either:
   - Run without the prefix (BERT still benefits from training exposure)
   - Or add a lightweight keyword extractor that generates a simpler version of the prefix

**Why this works:** This is a form of knowledge distillation. The LLM's reasoning teaches BERT what features matter. Instead of learning that "BACS" appears in payment documents by seeing 50 examples, BERT learns it from the explicit signal description in 5 examples.

**Trade-off:** Adds complexity to the training pipeline and inference. Best reserved for when more data isn't available. Start with strategies 1-4 first.

**Impact:** Medium — most valuable for small datasets where strategies 1-4 haven't generated enough data yet.

**Effort:** Medium — needs prompt engineering and modified training data preparation.

---

## 7. Automated Category Boundary Testing

**Problem:** After training, there's no systematic way to test where BERT's classification boundaries are. Manual testing is ad-hoc and misses edge cases.

**Approach:** Use the LLM to generate adversarial test cases that probe BERT's weaknesses.

**How it works:**

1. For each category pair, ask the LLM:
   ```
   Generate a document that could plausibly be classified as either "{category_a}" or "{category_b}".
   Make it genuinely ambiguous — it should contain signals for both categories.
   Then state which category it actually belongs to and why.
   ```
2. Run BERT inference on each adversarial sample
3. Compare BERT's prediction to the LLM's ground truth
4. Report:
   - Which category boundaries BERT handles well
   - Which boundaries are fragile
   - Recommended actions (more data, category merge, threshold adjustment)

**Why this works:** This is automated red-teaming for the classifier. Instead of waiting for production misclassifications, you proactively find weaknesses before deploying the model.

**Impact:** Medium — improves confidence in model quality before promotion.

**Effort:** Medium — needs test generation, inference pipeline, and reporting.

---

## Recommended Implementation Order

| Priority | Strategy | When to use | Prerequisite |
|---|---|---|---|
| **1** | Synthetic Data Generation | Now — solves the immediate singleton problem | Taxonomy + MCP tools |
| **2** | Taxonomy Refinement | Now — one-time analysis, no code needed | Admin review |
| **3** | Data Augmentation | After reaching 10+ real samples per category | Existing training data |
| **4** | Confusion-Driven Collection | After first production model with per-class metrics | Trained model + confusion matrix |
| **5** | Quality Audit | When auto-collection is enabled at scale | >100 auto-collected samples |
| **6** | Boundary Testing | Before promoting a model to production | Trained model |
| **7** | Explanation-Enriched Training | If accuracy plateaus despite sufficient data | 50+ samples per category |

---

## Cost Considerations

Each strategy uses LLM API calls. Estimated token costs per run (using Claude Sonnet for generation):

| Strategy | Calls per run | Est. tokens | Est. cost |
|---|---|---|---|
| Synthetic generation (10 categories x 10 samples) | 10 | ~50k | ~$0.15 |
| Data augmentation (50 samples x 3 variations) | 50 | ~75k | ~$0.25 |
| Quality audit (200 samples) | 200 | ~100k | ~$0.30 |
| Confusion analysis (10 category pairs) | 10 | ~30k | ~$0.10 |
| Taxonomy refinement | 1 | ~10k | ~$0.03 |

Total for a full cycle: under $1. This is negligible compared to the LLM classification costs that BERT eliminates once trained effectively.

---

## Architecture Integration

All strategies integrate with the existing pipeline:

```
                    ┌─────────────────────────┐
                    │   LLM (Claude)          │
                    │                         │
                    │  ┌───────────────────┐  │
                    │  │ Classify docs     │──┼──→ Training samples (existing)
                    │  │ Generate synthetic│──┼──→ Training samples (new)
                    │  │ Augment existing  │──┼──→ Training samples (new)
                    │  │ Audit quality     │──┼──→ Sample quality scores
                    │  │ Analyse confusion │──┼──→ Targeted collection plan
                    │  │ Refine taxonomy   │──┼──→ Admin recommendations
                    │  │ Test boundaries   │──┼──→ Model quality report
                    │  └───────────────────┘  │
                    └─────────────────────────┘
                                │
                                ▼
                    ┌─────────────────────────┐
                    │  Training Data Store    │
                    │  (MongoDB)              │
                    │                         │
                    │  Sources:               │
                    │  - AUTO_COLLECTED       │
                    │  - CORRECTION           │
                    │  - MANUAL_UPLOAD        │
                    │  - LLM_SYNTHETIC  (new) │
                    │  - LLM_AUGMENTED  (new) │
                    └─────────────┬───────────┘
                                  │
                                  ▼
                    ┌─────────────────────────┐
                    │  BERT Training          │
                    │  (distilbert)           │
                    │                         │
                    │  Weighting:             │
                    │  - CORRECTION: 3x       │
                    │  - MANUAL: 1x           │
                    │  - AUTO_COLLECTED: 1x   │
                    │  - LLM_SYNTHETIC: 0.5x  │
                    │  - LLM_AUGMENTED: 0.75x │
                    └─────────────────────────┘
```

Synthetic and augmented samples should be weighted lower than real data during training to prevent the model from learning LLM artifacts instead of real document patterns.
