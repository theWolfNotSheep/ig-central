# ModernBERT Microservice — Implementation Requirements

## Vision

We are building a self-teaching classification system where the LLM does the initial heavy lifting, humans correct mistakes, and those corrections automatically train a local ModernBERT model that progressively handles more documents without the LLM. Every step — training, promotion, rollback, threshold adjustment — is visible to administrators and requires human signoff before it affects production classification.

The system gets smarter with every document it processes. The LLM teaches ModernBERT through its classifications. Humans teach both through their corrections. Over time, the balance shifts: ModernBERT handles the routine work, the LLM handles the edge cases, and humans handle the truly ambiguous.

---

## Principles

1. **Human-in-the-loop at every gate.** ModernBERT never enters production classification without an admin publishing a trained model version. Training runs are proposed, not automatic.
2. **Full audit trail.** Every inference, every training run, every promotion decision is logged and traceable. We treat model decisions with the same rigour as LLM decisions.
3. **Block architecture alignment.** ModernBERT models are managed as a new block type (`MODEL`), inheriting the same versioning, feedback, comparison, and rollback mechanics as prompts and regex patterns.
4. **Graceful degradation.** If the BERT service is down or a category has no trained model, the pipeline falls through to the LLM exactly as it does today. ModernBERT is an accelerator, not a dependency.
5. **Observable self-improvement.** Admins can see: which categories BERT handles, how accurate it is, when retraining is recommended, and what would change if a new model version were promoted.

---

## 1. New Docker Service: `bert-service`

### 1.1 Service Definition

A new Python microservice added to `docker-compose.yml`:

| Property | Value |
|---|---|
| Container name | `bert-service` |
| Port | `8085` |
| Framework | FastAPI |
| ML stack | HuggingFace Transformers, PyTorch, ModernBERT |
| Model storage | Docker volume (`bert-models`) mounted at `/models` |
| Health check | `GET /health` |

### 1.2 API Endpoints

| Endpoint | Method | Purpose |
|---|---|---|
| `/health` | GET | Readiness check — reports loaded model versions |
| `/classify` | POST | Run category + sensitivity classification on document text |
| `/embed` | POST | Generate document embedding vector |
| `/ner` | POST | Run PII named entity recognition |
| `/models` | GET | List all trained model artifacts with metadata |
| `/models/{id}/metrics` | GET | Validation metrics for a specific model version |
| `/train` | POST | Trigger a training run (called by the API service) |
| `/train/{jobId}/status` | GET | Poll training job progress |
| `/evaluate` | POST | Evaluate a model version against a validation set without promoting it |

### 1.3 `/classify` Request and Response

**Request:**
```json
{
  "documentId": "abc123",
  "text": "extracted document text...",
  "mimeType": "application/pdf",
  "modelVersion": "category-v7"
}
```

**Response:**
```json
{
  "documentId": "abc123",
  "category": {
    "predicted": "HR > Leave Records",
    "categoryId": "cat_hr_leave",
    "confidence": 0.91,
    "topN": [
      {"categoryId": "cat_hr_leave", "name": "HR > Leave Records", "confidence": 0.91},
      {"categoryId": "cat_hr_general", "name": "HR > General Correspondence", "confidence": 0.05},
      {"categoryId": "cat_finance_payroll", "name": "Finance > Payroll", "confidence": 0.02}
    ]
  },
  "sensitivity": {
    "predicted": "OFFICIAL",
    "confidence": 0.84,
    "distribution": {
      "PUBLIC": 0.04,
      "OFFICIAL": 0.84,
      "OFFICIAL_SENSITIVE": 0.11,
      "SECRET": 0.01
    }
  },
  "modelVersion": "category-v7",
  "inferenceMs": 47
}
```

The `topN` predictions and full sensitivity distribution give the admin and the LLM rich context about what the model considered, not just a single answer.

### 1.4 Infrastructure

- **nginx** gains a `/bert-service/` proxy location
- **API service** calls BERT service via internal Docker network (`http://bert-service:8085`)
- **Model volume** persists trained model artifacts across container restarts
- **Resource limits:** CPU-only inference by default (ModernBERT is fast enough on CPU). GPU passthrough configurable via `docker-compose.override.yml` for organisations with available hardware.

---

## 2. New Block Type: `MODEL`

### 2.1 Block Structure

ModernBERT models are managed as pipeline blocks, extending the existing `PipelineBlock` model:

| Field | Type | Purpose |
|---|---|---|
| `type` | String | `"MODEL"` |
| `modelType` | String | `"CATEGORY_CLASSIFIER"`, `"SENSITIVITY_CLASSIFIER"`, `"PII_NER"` |
| `activeVersion` | Integer | Currently promoted model version |
| `versions[]` | List | Immutable version history (same as other block types) |
| `versions[].content` | Object | Training config, label mappings, artifact reference |
| `versions[].trainingJobId` | String | Reference to the training run that produced this version |
| `versions[].metrics` | Object | Accuracy, F1, confusion matrix from validation |
| `versions[].trainedOn` | Integer | Number of training examples used |
| `versions[].trainedAt` | Instant | When training completed |
| `draftContent` | Object | Proposed training configuration (before training starts) |
| `documentsProcessed` | Long | Inference count for monitoring |
| `correctionsReceived` | Long | Corrections against BERT decisions |
| `feedbackCount` | Long | Total feedback entries |

### 2.2 Version Content Schema

Each published version stores:

```json
{
  "artifactPath": "/models/category-classifier/v7/",
  "baseModel": "answerdotai/ModernBERT-base",
  "trainingConfig": {
    "epochs": 5,
    "learningRate": 2e-5,
    "batchSize": 16,
    "maxLength": 4096,
    "warmupSteps": 100
  },
  "labelMapping": {
    "0": {"categoryId": "cat_hr_leave", "name": "HR > Leave Records"},
    "1": {"categoryId": "cat_hr_general", "name": "HR > General Correspondence"}
  },
  "validationMetrics": {
    "accuracy": 0.89,
    "weightedF1": 0.87,
    "perCategory": {
      "cat_hr_leave": {"precision": 0.92, "recall": 0.88, "f1": 0.90, "support": 134},
      "cat_hr_general": {"precision": 0.81, "recall": 0.85, "f1": 0.83, "support": 67}
    },
    "confusionMatrix": "...base64 encoded image or structured data..."
  },
  "trainingDataSnapshot": {
    "totalExamples": 1247,
    "perCategory": {"cat_hr_leave": 134, "cat_hr_general": 67},
    "correctionExamples": 89,
    "llmExamples": 1158,
    "splitRatio": "80/20 train/validation"
  }
}
```

### 2.3 Admin UI: Block Library Integration

The existing Block Library page (`/blocks`) gains a `MODEL` type filter. The detail panel for MODEL blocks shows:

- **Training tab** — current training data stats, per-category sample counts, "Propose Training Run" button
- **Metrics tab** — accuracy, F1, confusion matrix, per-category performance, comparison between versions
- **Versions tab** — same timeline as other blocks, but each version shows training metrics alongside content
- **Feedback tab** — corrections against BERT decisions, grouped by category
- **Promotion tab** — side-by-side comparison of current active version vs candidate, with "Promote to Active" button requiring admin signoff

---

## 3. Training Pipeline

### 3.1 Training Data Assembly

Training data is assembled from MongoDB by the API service and sent to the BERT service:

| Source | Label Used | Quality Tier |
|---|---|---|
| Human-corrected documents (`ClassificationCorrection` with `CATEGORY_CHANGED`, `SENSITIVITY_CHANGED`, `BOTH_CHANGED`) | The **corrected** value | Highest — human ground truth |
| Human-approved documents (`APPROVED_CORRECT` corrections) | The **original** LLM value | High — human-validated |
| LLM-classified documents with confidence >= 0.85 and no correction | The **LLM** value | Medium — unvalidated but high confidence |
| LLM-classified documents with confidence < 0.85 and no correction | **Excluded** | Too uncertain to train on |

Each training example:
```json
{
  "text": "extracted document text (truncated to maxLength)",
  "categoryId": "cat_hr_leave",
  "sensitivityLabel": "OFFICIAL",
  "source": "HUMAN_CORRECTION",
  "documentId": "doc_abc123",
  "correctionId": "corr_xyz789"
}
```

### 3.2 Training Data Quality Rules

- **Minimum 30 examples per category** to include that category in training. Categories below threshold remain LLM-only.
- **Correction examples are oversampled 3x** — human corrections are the most valuable signal and should carry more weight than bulk LLM-labelled data.
- **80/20 train/validation split**, stratified by category to ensure every category appears in both sets.
- **Deduplication by `sha256Hash`** — if the same document was re-processed, only the most recent label is used.
- **Text truncation** — documents longer than the model's max length (8,192 tokens) are truncated with a preference for the first and last sections (where classification-relevant content typically appears).

### 3.3 Training Job Lifecycle

```
PROPOSED → APPROVED → ASSEMBLING_DATA → TRAINING → VALIDATING → COMPLETED → PROMOTED
    │          │                              │           │                       │
    ↓          ↓                              ↓           ↓                       ↓
 CANCELLED  CANCELLED                    TRAIN_FAILED  VAL_FAILED          (admin signoff)
```

1. **PROPOSED** — System or admin proposes a training run. The proposal includes: which categories are eligible, how many examples per category, what changed since the last training run. Stored as a `TrainingJob` document in MongoDB.
2. **APPROVED** — Admin reviews the proposal and approves. This is a gate — no training runs without human signoff.
3. **ASSEMBLING_DATA** — API service queries MongoDB for training examples, applies quality rules, sends to BERT service.
4. **TRAINING** — BERT service fine-tunes ModernBERT. Progress is reported via SSE or polling (`/train/{jobId}/status`). Estimated duration shown to admin.
5. **VALIDATING** — BERT service runs the trained model against the held-out validation set. Produces accuracy, F1, per-category metrics, confusion matrix.
6. **COMPLETED** — Training finished. Results are stored on the MODEL block as a new unpublished version. Admin can review metrics, compare against the active version, and run additional evaluation.
7. **PROMOTED** — Admin publishes the new version as active. The BERT service hot-loads the new model. A second gate — no model enters production without human signoff.

### 3.4 Training Job Record

```json
{
  "id": "train_abc123",
  "blockId": "block_cat_classifier",
  "status": "COMPLETED",
  "proposedBy": "SYSTEM",
  "proposedAt": "2026-04-08T10:00:00Z",
  "proposalReason": "32 new corrections since last training (v6). Categories affected: HR > Leave Records (12), Finance > Invoices (8), Legal > Contracts (7), Other (5).",
  "approvedBy": "admin@governanceledstore.co.uk",
  "approvedAt": "2026-04-08T10:15:00Z",
  "trainingConfig": { "...same as block version content..." },
  "dataStats": {
    "totalExamples": 1247,
    "correctionExamples": 89,
    "categoriesIncluded": 18,
    "categoriesExcluded": 4,
    "excludedReasons": {"Legal > GDPR Requests": "only 12 examples (minimum 30)"}
  },
  "metrics": { "...validation metrics..." },
  "previousVersion": 6,
  "producedVersion": 7,
  "completedAt": "2026-04-08T10:45:00Z",
  "promotedBy": null,
  "promotedAt": null
}
```

### 3.5 Retraining Triggers

The system proposes (but never auto-executes) a retraining run when:

| Trigger | Condition | Shown to Admin As |
|---|---|---|
| Correction threshold | N new corrections since last training (configurable, default 20) | "32 new corrections since v6 — categories affected: ..." |
| Accuracy drift | BERT correction rate for a category exceeds threshold (default 15%) over a rolling window | "HR > Leave Records: BERT was corrected 18% of the time over the last 50 documents" |
| New category eligible | A category that was previously below the training minimum now has enough examples | "Legal > GDPR Requests now has 35 examples (threshold: 30) — eligible for BERT" |
| Scheduled | Configurable interval (e.g. weekly) regardless of corrections | "Weekly retraining check — 47 new labelled documents since v6" |

Each trigger creates a training proposal visible in the admin UI. The admin can approve, dismiss, or schedule for later.

---

## 4. Pipeline Integration

### 4.1 New Pipeline Stage: `PRE_CLASSIFY`

We add a `PRE_CLASSIFY` step between `PROCESSED` and `CLASSIFYING`:

```
UPLOADED → PROCESSING → PROCESSED → PRE_CLASSIFYING → CLASSIFYING → CLASSIFIED → GOVERNANCE_APPLIED
                                          │
                                          ├─ BERT confident → skip to CLASSIFIED
                                          └─ BERT uncertain → continue to CLASSIFYING (LLM)
```

### 4.2 New RabbitMQ Queue

| Queue | Routing Key | Consumer |
|---|---|---|
| `gls.documents.processed` | `document.processed` | **Changed:** now consumed by the API service (or a new lightweight router), not directly by `llm-worker` |
| `gls.documents.bert.classify` | `document.bert.classify` | New consumer in API service — calls BERT service |
| `gls.documents.llm.classify` | `document.llm.classify` | Existing `llm-worker` — renamed routing key for clarity |
| `gls.documents.classified` | `document.classified` | Unchanged — `governance-enforcer` consumes |

**Flow:**

1. `document.processed` event arrives
2. Router checks: is there an active MODEL block for the resolved category (or a general classifier)? Is the BERT service healthy?
   - **Yes** → publish to `gls.documents.bert.classify`
   - **No** → publish to `gls.documents.llm.classify` (existing LLM path)
3. BERT consumer calls `bert-service:8085/classify`
4. Based on response:
   - **BERT confident** (category confidence >= auto-accept threshold AND category has low correction rate) → save classification result with `classifiedBy: "MODERNBERT"`, set status `CLASSIFIED`, publish `document.classified`
   - **BERT medium confidence** → publish to `gls.documents.llm.classify` with BERT predictions attached as context for the LLM
   - **BERT low confidence or error** → publish to `gls.documents.llm.classify` as normal

### 4.3 New Pipeline Block Step Type

The `PipelineStep` gains a new type:

| Step Type | Value |
|---|---|
| `MODEL_CLASSIFY` | Run ModernBERT classification using a referenced MODEL block |

This means pipelines can be configured to include or exclude the BERT step per-pipeline. Admins can build pipelines that skip BERT entirely for categories where it is not yet trained, or pipelines that use BERT-only for mature categories.

### 4.4 Threshold Configuration

Two thresholds govern the BERT → LLM handoff, configurable per category or globally:

| Threshold | Default | Purpose |
|---|---|---|
| `bert.auto_accept_threshold` | 0.90 | BERT confidence above this AND category correction rate below `bert.max_correction_rate` → accept without LLM |
| `bert.llm_hint_threshold` | 0.50 | BERT confidence between this and auto-accept → pass predictions to LLM as context |
| `bert.max_correction_rate` | 0.10 | Maximum historical correction rate for a category to be eligible for BERT auto-accept |

Below `llm_hint_threshold`, BERT predictions are discarded and the LLM classifies from scratch.

These thresholds live in the ROUTER block configuration, extending the existing review threshold.

---

## 5. LLM-to-BERT Feedback Loop

### 5.1 How the LLM Teaches BERT

Every LLM classification is a potential BERT training example:

```
Document processed
    → BERT classifies (or can't — new category)
    → LLM classifies (confirms BERT, overrides BERT, or classifies from scratch)
    → Human reviews (approves, corrects, or skips)
    → Final label stored
    → Next training run includes this labelled example
    → BERT gets better at this category
    → Fewer documents need LLM
```

The key insight: **the LLM is the teacher, the human is the examiner, and BERT is the student.** The LLM generates bulk training data. The human provides corrections that outweigh the LLM's labels. BERT learns from both, with corrections weighted higher.

### 5.2 Disagreement Tracking

When BERT and the LLM both classify the same document, we record the disagreement:

```json
{
  "documentId": "doc_abc123",
  "bertCategory": "HR > Leave Records",
  "bertConfidence": 0.78,
  "llmCategory": "HR > General Correspondence",
  "llmConfidence": 0.82,
  "finalCategory": "HR > General Correspondence",
  "resolvedBy": "LLM",
  "humanOverride": null,
  "timestamp": "2026-04-08T14:30:00Z"
}
```

This powers:
- **Disagreement dashboards** — which categories do BERT and the LLM fight over? These are the categories that need more training data or clearer taxonomy definitions.
- **BERT accuracy vs LLM** — when they disagree and a human reviews, who was right more often?
- **Confidence calibration** — is BERT's 0.78 more trustworthy than the LLM's 0.82 for this category?

### 5.3 Correction Attribution

When a human corrects a classification, the correction now records which system made the decision:

| Field | Purpose |
|---|---|
| `classifiedBy` | `"MODERNBERT"`, `"LLM"`, or `"MODERNBERT_LLM"` |
| `bertPrediction` | What BERT predicted (even if LLM overrode it) |
| `bertConfidence` | BERT's confidence at the time |

This lets us calculate separate accuracy metrics for BERT-only classifications vs LLM classifications vs BERT-assisted LLM classifications. We need to know: when BERT handles a document alone, is the correction rate acceptable?

---

## 6. Audit and Observability

### 6.1 BERT Inference Logging

Every BERT inference is logged using the existing `AiUsageLog` pattern:

| Field | Value |
|---|---|
| `usageType` | `"BERT_CLASSIFY"`, `"BERT_EMBED"`, `"BERT_NER"` |
| `provider` | `"modernbert"` |
| `model` | `"category-classifier-v7"` |
| `promptBlockId` | MODEL block ID |
| `promptBlockVersion` | Model version used |
| `inputTokens` | Approximate token count of input text |
| `outputTokens` | N/A (classification, not generation) |
| `durationMs` | Inference time |
| `estimatedCost` | `0.0` (local inference) |
| `result` | Full classification response (topN, distribution) |
| `status` | `SUCCESS`, `FAILED`, `DEFERRED_TO_LLM` |
| `outcome` | Set later: `ACCEPTED`, `OVERRIDDEN`, `REJECTED` |

### 6.2 Training Run Audit

Every training run produces audit events:

| Event | Details |
|---|---|
| `TRAINING_PROPOSED` | Trigger reason, affected categories, proposed config |
| `TRAINING_APPROVED` | Who approved, when |
| `TRAINING_STARTED` | Data stats, config snapshot |
| `TRAINING_COMPLETED` | Validation metrics, comparison to previous version |
| `TRAINING_FAILED` | Error details, stack trace |
| `MODEL_PROMOTED` | Who promoted, version number, previous version metrics vs new |
| `MODEL_ROLLED_BACK` | Who rolled back, from version, to version, reason |

### 6.3 Admin Dashboard: BERT Performance

A new section within the existing Monitoring page (`/monitoring`) or a dedicated page:

**Overview cards:**
- Documents classified by BERT (total and %)
- Documents deferred to LLM (total and %)
- BERT correction rate (rolling 7-day)
- LLM cost savings (estimated from documents BERT handled)
- Active model versions and their ages

**Per-category table:**

| Category | BERT Eligible | Sample Count | BERT Accuracy | Correction Rate | Auto-Accept | Last Trained |
|---|---|---|---|---|---|---|
| HR > Leave Records | Yes | 134 | 91% | 6% | Yes | 2 days ago |
| Finance > Invoices | Yes | 89 | 85% | 12% | No (rate too high) | 2 days ago |
| Legal > GDPR Requests | No | 12 | — | — | — | — |

**Disagreement log:** Filterable list of documents where BERT and LLM disagreed, showing who was ultimately right (per human review).

**Training history:** Timeline of training runs with metrics comparison between versions.

### 6.4 Category Readiness Indicators

Each taxonomy category shows its BERT readiness status in the Governance admin:

| Status | Meaning | Shown As |
|---|---|---|
| `NOT_ELIGIBLE` | Below minimum training examples | Grey badge, "12/30 examples" |
| `ELIGIBLE` | Enough examples, no trained model yet | Amber badge, "Ready to train" |
| `TRAINED` | Model trained but not promoted | Blue badge, "Awaiting promotion" |
| `ACTIVE` | Model promoted, BERT classifying | Green badge, with accuracy % |
| `ACTIVE_SUPERVISED` | BERT classifying but correction rate is above auto-accept threshold | Yellow badge, "BERT + LLM verification" |
| `DEGRADED` | Correction rate spiked above alert threshold | Red badge, "Review recommended" |

---

## 7. Self-Teaching Cycle

The complete self-teaching loop, with human gates at each decision point:

```
┌─────────────────────────────────────────────────────────────────┐
│                                                                 │
│   1. Documents are uploaded                                     │
│      ↓                                                          │
│   2. Text extracted (doc-processor)                             │
│      ↓                                                          │
│   3. BERT classifies (if model exists for this category)        │
│      ├─ Confident → Accept                                      │
│      └─ Uncertain → Defer to LLM                                │
│      ↓                                                          │
│   4. LLM classifies (if BERT deferred or no model)              │
│      ↓                                                          │
│   5. Low confidence → Human review queue                        │
│      ↓                                                          │
│   6. Human approves or corrects                                 │
│      ↓                                                          │
│  ★7. Correction stored as training data                         │
│      ↓                                                          │
│  ★8. System detects enough new corrections → proposes retrain   │
│      ↓                                                          │
│  ★9. Admin reviews proposal → approves training run      [GATE] │
│      ↓                                                          │
│  ★10. BERT service trains new model version                     │
│      ↓                                                          │
│  ★11. Admin reviews metrics → promotes new version       [GATE] │
│      ↓                                                          │
│  ★12. BERT handles more documents in step 3                     │
│      ↓                                                          │
│      (loop continues — system improves with every cycle)        │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘

★ = new capability added by this work
[GATE] = requires human signoff
```

### 7.1 Cold Start

When the system is first deployed, there is no BERT model. The pipeline works exactly as it does today:

1. All documents go to the LLM
2. Low-confidence documents go to human review
3. Corrections accumulate
4. After enough labelled documents exist (30+ per category), the system proposes the first training run
5. Admin approves → first BERT model trained
6. Admin reviews metrics → promotes model
7. BERT starts handling confident classifications for eligible categories
8. The LLM workload begins to decrease

### 7.2 Steady State

Once BERT is trained and active for most categories:

- 60–80% of documents are classified by BERT alone (estimate based on typical category distributions)
- 15–30% are classified by the LLM (BERT uncertain or new categories)
- 5–10% go to human review (low confidence from either system)
- Retraining proposals appear periodically as corrections accumulate
- Category readiness indicators show the health of each category's model
- Cost per document drops significantly as LLM calls decrease

### 7.3 Degradation Handling

If BERT performance degrades for a category (correction rate spikes):

1. System detects the spike via rolling accuracy window
2. Category status changes to `DEGRADED`
3. Auto-accept is disabled for that category — all documents go through LLM verification
4. Admin is notified via the monitoring dashboard
5. System proposes a retraining run with the new corrections included
6. Admin approves → retrain → promote → category returns to `ACTIVE`

If the BERT service itself goes down:

1. Health check fails
2. All documents route directly to the LLM queue
3. Pipeline operates exactly as it does today — no degradation in quality, only in speed/cost
4. Admin sees BERT service status in monitoring dashboard
5. When BERT service recovers, routing resumes automatically

---

## 8. Data Privacy Considerations

| Concern | How We Address It |
|---|---|
| Document text used for training | Training data is assembled from text already stored in MongoDB — no new data collection. Admins control which documents are eligible via quality tier rules. |
| Model artifacts contain learned patterns | Models are stored locally in Docker volumes, never sent externally. No document text is recoverable from a trained model. |
| PII in training data | Training uses extracted text that has already been through PII scanning. We do not strip PII from training data (the model needs to learn to classify documents that contain PII), but the model itself does not memorise or reproduce document content. |
| Audit of what was trained on | Each training run records the document IDs and correction IDs used. Fully traceable. |
| Right to erasure | If a document is disposed or a deletion is requested, we flag the affected training examples. Next retraining excludes them. |

---

## 9. DocumentModel and ClassificationResult Changes

### 9.1 New Fields on DocumentModel

| Field | Type | Purpose |
|---|---|---|
| `classifiedBy` | String | `"LLM"`, `"MODERNBERT"`, `"MODERNBERT_LLM"` |
| `bertPrediction` | Object | BERT's prediction (category, confidence, topN) — stored even when LLM overrides |

### 9.2 New Fields on DocumentClassificationResult

| Field | Type | Purpose |
|---|---|---|
| `classifiedBy` | String | Which system produced the final classification |
| `bertCategoryId` | String | BERT's predicted category (for disagreement tracking) |
| `bertConfidence` | Double | BERT's confidence score |
| `bertTopN` | List | BERT's top-N predictions with confidences |
| `agreementScore` | Double | Agreement between BERT and LLM (1.0 = same answer, 0.0 = completely different) |

### 9.3 New Fields on ClassificationCorrection

| Field | Type | Purpose |
|---|---|---|
| `classifiedBy` | String | Which system made the decision that was corrected |
| `bertPrediction` | String | What BERT predicted (for correction attribution analysis) |
| `bertConfidence` | Double | BERT's confidence when the correction was made |

---

## 10. New MongoDB Collections

| Collection | Purpose |
|---|---|
| `training_jobs` | Training run proposals, approvals, configs, metrics, status |
| `bert_inference_logs` | BERT-specific inference records (supplements `ai_usage_logs`) |
| `bert_disagreements` | Records where BERT and LLM classified differently |
| `category_readiness` | Per-category training data counts, correction rates, BERT eligibility status |

---

## 11. Configuration

All BERT-related configuration lives in `app_config` (runtime-editable) or `application.yaml` (infrastructure):

### 11.1 Runtime Config (app_config)

```json
{
  "bert.enabled": true,
  "bert.auto_accept_threshold": 0.90,
  "bert.llm_hint_threshold": 0.50,
  "bert.max_correction_rate": 0.10,
  "bert.min_training_examples": 30,
  "bert.retrain_correction_threshold": 20,
  "bert.retrain_schedule": "WEEKLY",
  "bert.correction_oversample_factor": 3,
  "bert.degradation_alert_rate": 0.20,
  "bert.rolling_accuracy_window": 50
}
```

### 11.2 Infrastructure Config (application.yaml)

```yaml
bert:
  service:
    url: http://bert-service:8085
    timeout-ms: 5000
    retry-attempts: 1
  training:
    max-text-length: 8192
    validation-split: 0.2
    base-model: answerdotai/ModernBERT-base
```

---

## 12. Phased Delivery

### Phase 1 — Foundation
- Build `bert-service` Docker container with `/health`, `/classify`, `/train`, `/evaluate` endpoints
- Add `MODEL` block type to `PipelineBlock`
- Add `training_jobs` collection and admin endpoints for proposing, approving, and monitoring training runs
- Implement training data assembly from existing classified documents and corrections
- First training run on accumulated data, manually triggered by admin

### Phase 2 — Pipeline Integration
- Add `PRE_CLASSIFY` stage and RabbitMQ routing
- Implement BERT → LLM handoff logic with configurable thresholds
- Add `classifiedBy`, `bertPrediction`, `bertConfidence` fields to documents and classification results
- Log BERT inferences in `AiUsageLog`
- BERT predictions passed as LLM context when confidence is medium

### Phase 3 — Self-Teaching Loop
- Implement automatic retraining proposals (correction threshold, accuracy drift, new category eligibility)
- Training proposal review and approval UI in admin
- Model version comparison and promotion UI
- Disagreement tracking and dashboard
- Category readiness indicators in governance admin

### Phase 4 — Sensitivity Classifier and PII NER
- Second ModernBERT head for sensitivity classification
- Fine-tuned NER model for PII detection alongside regex patterns
- PII correction feedback (`PII_FLAGGED`, `PII_DISMISSED`) feeds NER training

### Phase 5 — Embeddings and Similarity
- `/embed` endpoint for document vector generation
- Elasticsearch `dense_vector` indexing
- Near-duplicate detection at upload time
- Similar document retrieval for LLM few-shot examples
