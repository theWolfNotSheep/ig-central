# Classification Efficiency Proposals

> How to reduce cost, latency, and error rate in document classification by leveraging governance data more effectively.

---

## Current State: Where the Tokens Go

A single document classification currently consumes **~5,500–6,500 input tokens** and makes **8–10 MCP tool round-trips** over **30–150 seconds**.

| Component | Tokens | % of Input | Notes |
|-----------|--------|-----------|-------|
| System prompt | ~870 | 15% | Hardcoded classification workflow instructions |
| Classification taxonomy | ~2,000 | 33% | Returned by `get_classification_taxonomy` MCP tool — largest single contributor |
| Correction history | ~800 | 13% | Top 50 corrections for likely category + MIME type |
| Document text | ~1,500 | 25% | Truncated to 100KB, varies wildly by doc size |
| Policies, sensitivities, traits, PII, schemas | ~800 | 14% | Five separate MCP tool calls |
| **Total input** | **~6,000** | | |
| LLM output (classification + reasoning) | ~300 | | |
| **Estimated cost per doc** | **~$0.02** | | Claude Sonnet at $3/$15 per 1M input/output |

**Key bottlenecks:**
1. **8–10 MCP tool round-trips** add 1–3s latency each and pollute the context window, causing the model to "forget" earlier tool results on long documents
2. **Tool-calling failure rate of 20–30%** with Ollama (model produces prose instead of invoking `save_classification_result`), requiring retries that double token usage
3. **Full taxonomy injected every time** even when BERT or keyword analysis could narrow the search space to 10–20 categories
4. **No prompt caching** — the identical governance context (taxonomy, policies, sensitivities) is re-processed from scratch for every document
5. **Raw text sent to LLM** instead of structured document signals (headings, key terms, entities) that are more classification-relevant and far more compact

---

## Proposal 1: Governance Glossary and Disambiguation Layer

**Problem:** The LLM frequently confuses overlapping categories (e.g., "HR > Disciplinary" vs "HR > Performance Management", or "Finance > Invoices" vs "Finance > Purchase Orders"). Correction history captures these mistakes but doesn't prevent them proactively.

**Solution:** Build a classification glossary that gives the LLM explicit decision boundaries between confusable categories.

### What it looks like

New MongoDB collection `classification_glossary`:

```json
{
  "term": "Disciplinary Action",
  "categoryCode": "HR.ER.DA",
  "definition": "Formal employer response to employee misconduct — warnings, suspensions, dismissals",
  "distinguishedFrom": [
    {
      "categoryCode": "HR.ER.PM",
      "distinction": "Performance Management addresses capability/competence. Disciplinary addresses conduct/misconduct. If the document discusses objectives, targets, or development plans → PM. If it discusses warnings, hearings, or sanctions → Disciplinary."
    }
  ],
  "indicatorPhrases": ["misconduct", "disciplinary hearing", "written warning", "gross misconduct", "suspension"],
  "excludingPhrases": ["performance review", "objectives", "appraisal", "development plan"],
  "isoReference": "ISO 15489 Series: Employee Records, Sub-series: Disciplinary"
}
```

### How to populate it

1. **Mine correction history** — query `classification_corrections` for the most common `originalCategoryId` → `correctedCategoryId` pairs. These are your confused categories. Generate glossary entries for each pair.
2. **Use the LLM itself** — prompt Claude with your taxonomy and ask: "For each pair of sibling categories, write a disambiguation rule explaining when a document belongs to one vs the other."
3. **Curate over time** — when a new correction reveals a category confusion not covered by the glossary, auto-generate a draft entry and flag it for admin review.

### How to inject it

New MCP tool `get_glossary_for_categories(categoryIds)` — called after the LLM has a preliminary category in mind, returns disambiguation rules for that category and its siblings. ~200–400 tokens per call, only for the relevant neighbourhood.

Alternatively, pre-inject the top 10 most-confused category disambiguations into the system prompt prefix (~500 tokens) so the model has them before it starts reasoning.

### Expected impact

| Metric | Before | After |
|--------|--------|-------|
| Accuracy on ambiguous categories | ~70–80% | ~85–95% |
| Corrections per 100 documents | ~15 | ~5–8 |
| Additional tokens per request | 0 | +200–500 |
| Net effect on cost | — | Slight increase per call, but fewer retries and corrections |

---

## Proposal 2: Controlled Vocabulary Injection from Records Management Standards

**Problem:** The LLM uses ad-hoc language to reason about records management concepts. It may not distinguish "retention" from "preservation", or understand that "disposition" in records management means "what happens when retention expires" rather than its common English meaning.

**Solution:** Inject a compact controlled vocabulary derived from ISO 15489, Dublin Core, and ISAD(G) into the classification prompt.

### What to inject

**ISO 15489 / ISO 23081 terms (~150 tokens):**
```
Records management vocabulary:
- Record: information created/received in business activity, maintained as evidence
- Retention: period a record must be kept before disposition
- Disposition: action taken when retention expires (destroy, transfer, archive, review)
- Vital record: essential for business continuity, cannot be recreated
- Appraisal: evaluating records to determine retention and disposition
- Classification: systematic identification and arrangement by category
- Sensitivity: level of access restriction (PUBLIC → INTERNAL → CONFIDENTIAL → RESTRICTED)
```

**Dublin Core mapping for metadata extraction (~100 tokens):**
```
When extracting metadata, use Dublin Core semantics:
- dc:creator = person/org who authored the document (not who uploaded it)
- dc:date = primary document date (creation/signing, not upload date)
- dc:subject = topic (maps to taxonomy category)
- dc:type = document form (contract, letter, report, form, email)
- dc:coverage = jurisdictional scope (England and Wales, Scotland, UK-wide)
- dc:rights = access restrictions or copyright
```

**ISAD(G) level definitions (~80 tokens):**
```
You are classifying at these taxonomy levels:
- Function: broad area of business activity (e.g., Human Resources, Finance)
- Activity: specific process within a function (e.g., Recruitment, Payroll)
- Transaction: individual document type within an activity (e.g., Job Application, Payslip)
```

### Where to add it

Append to the system prompt prefix (static, cacheable). Total addition: ~330 tokens. This is a one-time cost amortised across all classifications.

### Expected impact

- **Accuracy:** 5–10% improvement on records management-specific decisions (retention triggers, disposition actions, sensitivity escalation)
- **Metadata extraction quality:** More consistent field naming aligned with Dublin Core
- **Enterprise credibility:** Classification results use ISO-standard terminology — valuable for compliance audits
- **Token cost:** +330 tokens per request (negligible)

---

## Proposal 3: Taxonomy Compression and Two-Pass Classification

**Problem:** The full taxonomy (~2,000 tokens) is sent for every classification. Most documents clearly belong to one top-level function (HR, Finance, Legal, etc.), and only the subcategories within that function are relevant.

**Solution:** Compress the taxonomy representation and optionally use a two-pass approach where the first pass identifies the function and the second pass classifies within it.

### Option A: Indented tree format (quick win)

Replace the current flat/JSON taxonomy representation with an indented tree using classification codes:

**Current (~2,000 tokens):**
```json
[
  {"id": "cat_001", "name": "Human Resources", "code": "HR", "description": "All HR-related records...", "children": [
    {"id": "cat_002", "name": "Employee Records", "code": "HR.ER", "description": "Individual employee files...", "children": [
      {"id": "cat_003", "name": "Leave Requests", "code": "HR.ER.LR", "description": "Annual leave, maternity...", "retentionPeriod": "6 years", ...}
    ]}
  ]}
]
```

**Compressed (~600 tokens):**
```
Classification Taxonomy:
HR Human Resources
  HR.ER Employee Records — individual employee files
    HR.ER.LR Leave Requests — annual, maternity, paternity, sick leave [6yr, INTERNAL]
    HR.ER.CT Contracts — employment contracts and amendments [termination+6yr, CONFIDENTIAL]
    HR.ER.DA Disciplinary — misconduct proceedings, warnings, hearings [termination+6yr, CONFIDENTIAL]
    HR.ER.PM Performance — reviews, objectives, development plans [6yr, INTERNAL]
  HR.REC Recruitment — job postings, applications, interviews
    HR.REC.AP Applications — CVs, cover letters, application forms [1yr post-decision, INTERNAL]
    ...
FIN Finance
  FIN.ACC Accounts — ledgers, journals, reconciliations
    ...
```

**Token savings: 60–70%** with identical information density.

### Option B: BERT-guided subtree injection (higher impact)

When BERT is operational, use it to identify the top-level function first, then inject only that subtree:

```
Step 1: BERT predicts top-3 functions:
  HR (0.82), Legal (0.11), Admin (0.04)

Step 2: Inject only the HR subtree (~300 tokens) + one-line summaries of other functions (~100 tokens):
  
  Primary candidate — HR Human Resources:
    HR.ER Employee Records
      HR.ER.LR Leave Requests — annual, maternity, paternity [6yr, INTERNAL]
      HR.ER.CT Contracts — employment contracts [term+6yr, CONFIDENTIAL]
      ...
  
  Other functions (if none of the above fits):
    FIN Finance — accounts, invoices, payroll, budgets
    LEG Legal — contracts, litigation, regulatory, compliance
    ADM Administration — premises, IT, procurement, general
    ...
```

**Token savings: 80–90%** compared to full taxonomy injection.

### Expected impact

| Approach | Taxonomy tokens | Savings | Effort |
|----------|----------------|---------|--------|
| Current (full JSON) | ~2,000 | — | — |
| Option A (indented tree) | ~600 | 70% | 2 hours (change `ClassificationTaxonomyTool` output format) |
| Option B (BERT-guided) | ~400 | 80% | Requires BERT deployment (weeks) |

---

## Proposal 4: Anthropic Prompt Caching

**Problem:** Every classification request re-processes the identical governance context (system prompt, taxonomy, policies, sensitivities, controlled vocabularies, glossary). This is ~3,000–4,000 tokens of static content re-tokenised for every document.

**Solution:** Use Anthropic's prompt caching to cache the static prefix. Cached tokens are billed at **10% of normal input cost** and process **~2x faster**.

### How to structure the prompt

```
┌─────────────────────────────────────────────────────────┐
│ CACHED PREFIX (~3,500 tokens)                    cache_control: ephemeral
│                                                         │
│ System prompt: classification instructions              │
│ Controlled vocabulary (ISO 15489, Dublin Core)          │
│ Taxonomy (compressed indented tree)                     │
│ Sensitivity definitions                                 │
│ Governance policies                                     │
│ Glossary: top 20 disambiguation rules                   │
│ Standard few-shot examples (3–5 representative docs)    │
│ Trait definitions                                       │
│ PII type patterns                                       │
├─────────────────────────────────────────────────────────┤
│ UNCACHED SUFFIX (~1,500–3,000 tokens per doc)           │
│                                                         │
│ Document-specific:                                      │
│   BERT top-3 predictions (if available)                 │
│   Correction history for predicted category             │
│   Dynamic few-shot examples (by similarity)             │
│   Structured document signals (see Proposal 6)          │
│   Document text (truncated)                             │
└─────────────────────────────────────────────────────────┘
```

### Implementation

1. **Pre-compute the cached prefix** in `ClassificationPromptBuilder` — fetch all static governance data and assemble into a single string. Store in memory. Regenerate when taxonomy/policies change.
2. **Eliminate most MCP tool calls** — pre-inject taxonomy, sensitivities, policies, traits, PII types into the prefix. The LLM only needs to call `get_correction_history` (dynamic), `get_metadata_schemas` (depends on chosen category), and `save_classification_result`.
3. **Reduce tool calls from 8–10 to 2–3** — eliminates the tool-calling failure problem and reduces latency by 40–60%.
4. **Set `cache_control: {"type": "ephemeral"}` on the last block of the prefix** via the Anthropic SDK.

### Requirements

- Cached prefix must be **at least 1,024 tokens** (easily met)
- Prefix must be **byte-for-byte identical** across requests (no timestamps, no random ordering)
- Cache TTL is **5 minutes**, refreshed on each hit — sustained classification workloads keep it warm

### Expected impact

| Metric | Before | After |
|--------|--------|-------|
| Static context tokens billed | 3,500 × $3/1M = $0.0105/doc | 3,500 × $0.3/1M = $0.00105/doc |
| TTFT (time to first token) | ~2–3s | ~1–1.5s |
| MCP tool calls | 8–10 | 2–3 |
| Tool-calling failure rate | 20–30% (Ollama) | ~0% (only 1 tool call needed) |
| **Cost per doc (static portion)** | **$0.0105** | **$0.00105** |
| **Savings at 1K docs/day** | — | **~$9.50/day on static tokens alone** |

---

## Proposal 5: Cascading Classification (Tiered Model Strategy)

**Problem:** Every document goes through full Claude Sonnet classification (~$0.02/doc, 30–150s). Most documents are straightforward and don't need expensive reasoning.

**Solution:** Route documents through progressively expensive classification tiers, where 60–80% are handled cheaply.

### The cascade

```
Document arrives
      │
      ▼
┌─ Tier 0: Rules Engine ──────────────────────────────────┐
│ Filename patterns, MIME type + folder path,              │
│ sender domain (emails), Google Drive folder path         │
│ Cost: $0 | Latency: <1ms | Handles: ~20–30%             │
└──────────────────────────────────────────────────────────┘
      │ no match
      ▼
┌─ Tier 1: BERT + Similarity Cache ────────────────────────┐
│ ModernBERT classifier on extracted text                   │
│ + n-gram similarity check against classified corpus       │
│ If confidence > 0.92 AND category has <5% correction rate │
│   → accept directly                                      │
│ Cost: ~$0.0001 | Latency: <100ms | Handles: ~40–50%      │
└──────────────────────────────────────────────────────────┘
      │ low confidence
      ▼
┌─ Tier 2: Claude Haiku with narrowed taxonomy ────────────┐
│ BERT top-5 categories injected as guidance                │
│ Compressed taxonomy (only relevant subtree)               │
│ Prompt-cached governance context                          │
│ If confidence HIGH or CERTAIN → accept                    │
│ Cost: ~$0.002 | Latency: ~3–5s | Handles: ~15–20%        │
└──────────────────────────────────────────────────────────┘
      │ ambiguous
      ▼
┌─ Tier 3: Claude Sonnet with full context ────────────────┐
│ Full taxonomy + correction history + glossary             │
│ Few-shot examples from similar docs                       │
│ Metadata extraction + trait detection                     │
│ Cost: ~$0.02 | Latency: ~15–30s | Handles: ~5–10%        │
└──────────────────────────────────────────────────────────┘
      │ low confidence
      ▼
  Human review queue
```

### Cost projection at scale

| Volume | Current (all Sonnet) | With cascade |
|--------|---------------------|--------------|
| 100 docs/day | $2.00/day | $0.30/day |
| 1,000 docs/day | $20/day | $2.50/day |
| 10,000 docs/day | $200/day | $20/day |
| 100,000 docs/day | $2,000/day | $150/day |

### Implementation path

This maps directly to the existing pipeline block architecture:

1. **Tier 0** → Existing `ROUTER` block type with filename/MIME pattern matching
2. **Tier 1** → Existing `BERT_CLASSIFIER` block type + `SimilarityCacheService`
3. **Tier 2** → New `PROMPT` block configured with `model: haiku`, `injectTaxonomy: true`, BERT guidance in user prompt
4. **Tier 3** → Existing `PROMPT` block with full MCP tool access

Admins configure the cascade in the visual pipeline editor by wiring nodes with condition edges based on confidence thresholds.

---

## Proposal 6: Structured Document Pre-Processing

**Problem:** Raw document text is sent to the LLM (~1,500–5,000 tokens). Most of that text is body content with low classification signal. The title, headings, and key terms alone are often sufficient to classify.

**Solution:** Extract structured signals from the document before classification and send those instead of (or alongside truncated) raw text.

### What to extract

```json
{
  "filename": "Employment_Contract_JSmith_2026.pdf",
  "mimeType": "application/pdf",
  "pageCount": 8,
  "wordCount": 3200,
  "title": "Contract of Employment",
  "headings": ["Terms of Employment", "Job Title and Duties", "Salary and Benefits", "Notice Period", "Confidentiality"],
  "firstParagraph": "This contract is made between Acme Corp Ltd and John Smith...",
  "lastParagraph": "Signed by both parties on 1 April 2026.",
  "keyTerms": ["employment", "salary", "notice period", "probation", "annual leave", "pension", "contract"],
  "detectedDates": ["2026-04-01", "2026-10-01"],
  "detectedEntities": {"PERSON": ["John Smith"], "ORG": ["Acme Corp Ltd"]},
  "sourceFolder": "HR/Contracts",
  "emailHeaders": null,
  "preDetectedPii": [{"type": "NI_NUMBER", "confidence": 0.95}]
}
```

### How to build it

1. **Tika already extracts structure** — configure it to return XHTML output, then parse headings (`<h1>`–`<h6>`), title metadata, and paragraph boundaries.
2. **TF-IDF key terms** — use Apache Lucene's analyzers (already a transitive dependency via Elasticsearch) to compute the 20 most distinctive terms against your classified corpus.
3. **Entity detection** — your existing PII scanner already identifies entities. Extend it to extract non-PII entities (ORG, DATE, LOCATION) for classification context.
4. **New pipeline node** — `DOCUMENT_SUMMARISER` node type that runs after `textExtraction` and produces the structured JSON above. The LLM receives this instead of raw text.

### Token comparison

| Input | Tokens | Classification signal |
|-------|--------|---------------------|
| Raw text (first 3,000 chars) | ~800 | Medium — lots of noise |
| Raw text (full doc, truncated) | ~1,500–5,000 | High but inefficient |
| Structured signals (above) | ~200–400 | High — concentrated signal |
| Structured signals + first 1,000 chars | ~500–700 | Very high |

### Expected impact

- **Token savings:** 50–80% reduction in document text tokens
- **Accuracy:** Maintained or improved — structure and key terms are more classification-relevant than body text
- **PII exposure reduction:** Sending structured metadata instead of full text minimises PII flowing through the LLM API
- **Latency:** Pre-processing adds ~100ms but LLM processing is faster with fewer tokens

---

## Proposal 7: Dynamic Few-Shot Example Selection

**Problem:** The LLM classifies from scratch every time, with no examples of correctly classified documents. Correction history shows mistakes but not successes.

**Solution:** Maintain a curated example store and dynamically select the most relevant examples for each document.

### Example store

New MongoDB collection `classification_examples`:

```json
{
  "categoryCode": "HR.ER.LR",
  "categoryName": "Leave Requests",
  "mimeType": "application/pdf",
  "snippet": "Dear HR, I am writing to request maternity leave commencing 1 June 2026...",
  "classificationResult": {
    "sensitivity": "INTERNAL",
    "traits": ["INBOUND", "FINAL"],
    "metadata": {"employee_name": "Jane Smith", "leave_type": "maternity"}
  },
  "source": "HUMAN_VERIFIED",
  "embedding": [0.023, -0.041, ...],
  "createdAt": "2026-04-20T..."
}
```

### Selection strategy

1. **Filter** by MIME type (PDF examples for PDFs, email examples for emails)
2. **Retrieve** top-20 candidates by embedding similarity to the current document's first 500 characters
3. **Diversify** — select 5 examples covering at least 3 different categories (prevents bias toward one category)
4. **Prioritise corrections** — if a candidate is from `ClassificationCorrection`, rank it higher (it represents a hard case the model should learn from)

### How to populate

- **Auto-collect from high-confidence classifications** — when a document is classified with confidence > 0.9 and the category has a low correction rate, add to the example store
- **Promote corrections** — every human correction creates a high-value example
- **Cap at 10 examples per category per MIME type** — enough for selection diversity without bloating the store
- **Embed using Ollama** — `nomic-embed-text` runs locally, no API cost

### Prompt format

```
Reference examples of classified documents:

[PDF → HR.ER.LR Leave Requests, INTERNAL]
"Dear HR, I am writing to request maternity leave commencing..."
Extracted: {employee_name: "Jane Smith", leave_type: "maternity", start_date: "2026-06-01"}

[PDF → HR.ER.CT Contracts, CONFIDENTIAL]  
"This contract is made between Acme Corp and..."
Extracted: {employee_name: "J. Brown", contract_type: "permanent", start_date: "2026-04-01"}

[EMAIL → HR.ER.LR Leave Requests, INTERNAL]
"Subject: Annual leave request — 2 weeks from 15 July..."
Extracted: {employee_name: "Tom Wilson", leave_type: "annual", start_date: "2026-07-15"}
```

~500–800 tokens for 5 examples. Added to the uncached (document-specific) portion of the prompt.

### Expected impact

- **Accuracy:** 10–20% improvement on ambiguous categories, 5–10% overall
- **Metadata extraction quality:** Examples show the LLM exactly what format to use for extracted fields
- **Consistency:** Classification decisions become more consistent across similar documents

---

## Proposal 8: Calibrated Confidence Scoring

**Problem:** The LLM outputs a single confidence number (0.0–1.0) that is not calibrated. A score of 0.85 doesn't reliably mean 85% accuracy. This causes either too many documents in the review queue (threshold too high) or too many misclassifications auto-accepted (threshold too low).

**Solution:** Replace numeric confidence with structured confidence buckets and require the model to justify its uncertainty.

### Prompt change

Replace:
```
Rate your confidence from 0.0 to 1.0
```

With:
```
Rate your confidence using these criteria:

CERTAIN (map to 0.97): Document clearly and unambiguously belongs to this category. 
  Multiple strong indicators. No plausible alternative. You would bet on this classification.

HIGH (map to 0.85): Strong indicators for this category. One or two signals might 
  suggest an alternative, but the primary classification is clear.

MODERATE (map to 0.65): Reasonable indicators, but another category is plausible. 
  Some ambiguity in the document's purpose or content.

LOW (map to 0.40): Weak indicators. Multiple categories seem equally plausible.

UNCERTAIN (map to 0.20): Cannot determine category reliably. Document may be 
  outside taxonomy scope.

Also state:
- Runner-up: What is the second most likely category and why did you not choose it?
- Key evidence: What specific phrases or structural elements drove your decision?
```

### Post-hoc calibration

Use your correction data to calibrate each bucket:
- Query: "When the model said CERTAIN for category X, what % was actually correct?"
- If CERTAIN is only 80% accurate for category "Legal > Contracts", lower its mapped value for that category
- Store calibration offsets per category in `app_config`

### Expected impact

- **Review queue efficiency:** 30–50% reduction in unnecessary reviews
- **Token cost:** +50–100 tokens per classification (for runner-up and evidence)
- **Operational insight:** You can track per-category calibration over time and identify categories that need glossary disambiguation or more training data

---

## Implementation Priority

| # | Proposal | Impact | Effort | Dependencies | Do When |
|---|----------|--------|--------|--------------|---------|
| 1 | **Taxonomy compression** (Option A: indented tree) | High — 70% taxonomy token reduction | 2–4 hours | None | Now |
| 2 | **Prompt caching** | Very high — 90% cost reduction on static context | 1–2 days | Proposal 1 (need stable prefix) | Now |
| 3 | **Pre-inject MCP context** (eliminate tool calls) | Very high — 40–60% latency reduction, ~0% tool failure | 1–2 days | Proposal 2 (structure prompt for caching) | Now |
| 4 | **Controlled vocabulary injection** | Medium — 5–10% accuracy, standards compliance | 4 hours | Proposal 3 (add to cached prefix) | Now |
| 5 | **Calibrated confidence** | Medium — 30–50% review queue reduction | 2 hours | None (prompt change only) | Now |
| 6 | **Document pre-processing** | High — 50–80% text token reduction | 1–2 days | Extend Tika extraction config | Next sprint |
| 7 | **Governance glossary** | Medium — 5–15% accuracy on confused categories | 2–3 days | Mine correction data, build collection | Next sprint |
| 8 | **Few-shot example store** | High — 10–20% accuracy on hard cases | 3–5 days | Needs embedding model (Ollama) | Next sprint |
| 9 | **Cascading classification** | Very high — 70–85% cost reduction | 1–2 weeks | Needs BERT with sufficient training data | When BERT ready |

### Combined impact (all proposals implemented)

| Metric | Current | Projected |
|--------|---------|-----------|
| Cost per document | ~$0.02 | ~$0.001–0.003 (weighted across tiers) |
| Average classification latency | 30–150s | <1s (80% of docs), 5–15s (15%), 15–30s (5%) |
| Tool-calling failure rate | 20–30% | ~0% |
| Input tokens per LLM call | ~6,000 | ~1,500–2,500 |
| Accuracy on ambiguous categories | ~70–80% | ~90–95% |
| Documents requiring human review | ~30% | ~10–15% |
| Cost at 10K docs/day | ~$200 | ~$15–25 |

---

## Appendix: Standards References

| Standard | What it provides | Machine-readable source |
|----------|-----------------|------------------------|
| ISO 15489 | Records management terminology, classification principles | Concepts only (not published as data) |
| ISO 23081 | Metadata element set for records | Schema definitions |
| Dublin Core (DCMI) | 15 core metadata elements | RDF/JSON-LD at dublincore.org |
| ISAD(G) | Archival description levels (Fonds → Item) | Text standard, implementable as JSON |
| SKOS | Taxonomy/thesaurus representation (`broader`, `narrower`, `related`) | W3C RDF vocabulary |
| MoReq2010 | EDRMS classification scheme model | XML schema |
| PRONOM | File format identification registry | XML at nationalarchives.gov.uk/PRONOM |
| LCSH | Library of Congress Subject Headings | SKOS/JSON at id.loc.gov |
| UK TNA | Government records retention schedules | CSV/XML at nationalarchives.gov.uk |
