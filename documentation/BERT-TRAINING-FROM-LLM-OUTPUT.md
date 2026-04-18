# Training ModernBERT from LLM Output — Gap Analysis & Implementation Plan

## Current State

### What Exists

| Component | Status | Notes |
|-----------|--------|-------|
| BERT classifier sidecar | Running (demo mode) | FastAPI on port 8000, 5 hardcoded demo categories, no real model |
| Pipeline accelerator node | Working | `BertClassifierService` calls `/classify`, skips LLM on hit |
| `TrainingDataSample` model | Exists | MongoDB `bert_training_data` collection |
| `BertTrainingDataCollector` | Exists but **disabled** | `bert.training.auto_collect_enabled = false` |
| Training data admin UI | Working | List, filter, verify, upload, export samples |
| `BertModelController` `/training-data` | Working | Exports classified docs as training pairs from `classification_results` |
| `BertModelController` `/label-map` | Working | Generates label map from classified categories |
| `/train` endpoint (Python) | Stub only | Returns 501 Not Implemented |
| Correction → training feedback | **Missing** | Corrections only feed LLM via MCP, not BERT |
| Model versioning | **Missing** | Single model dir, no history |
| Training trigger | **Missing** | No mechanism to propose/start training |

### Database Reality (as of now)

```
bert_training_data:          2 samples (both MANUAL_UPLOAD, unverified)
classification_results:     76 results (avg confidence 0.93, 10+ categories)
classification_corrections:  1 correction
documents:                  44 documents (all have extractedText)
```

**76 high-quality LLM classifications exist but zero have been collected as BERT training data.**

## Gap Analysis

### Gap 1: Auto-Collection is Disabled

**Config:** `bert.training.auto_collect_enabled = false`

The `BertTrainingDataCollector.tryCollect()` method is called after every LLM classification in `PipelineExecutionEngine.resumePipeline()` (line 304), but it returns immediately at line 48 because the config flag is false.

**Fix:** Turn it on. But that only helps future classifications — the 76 existing results won't be collected retroactively.

### Gap 2: No Backfill of Existing Classifications

76 documents have been classified by the LLM with high confidence (avg 0.93), across 10+ categories with extracted text available on every document. None of this was captured as training data because auto-collection was off when they were classified.

**Fix:** A one-time backfill that iterates `classification_results`, joins with `documents` for `extractedText`, and creates `TrainingDataSample` records. The `BertTrainingDataCollector.tryCollect()` method already does exactly this per-document — we need a batch version.

### Gap 3: Corrections Don't Feed BERT Training

When a records manager corrects a classification (e.g. changes category from "Supplier Payments" to "Contracts Management"), a `ClassificationCorrection` is created. This correction:
- **IS** fed to the LLM via `get_correction_history` MCP tool (in-context learning)
- **IS NOT** fed into `bert_training_data`

Corrected classifications are the highest-quality training signal — a human explicitly said "this document is category X, not Y". These should become verified training samples with the **corrected** category, not the original.

**Fix:** When a correction is saved, also create/update a `TrainingDataSample` with `source=CORRECTION`, `verified=true`, the corrected category, and the document text.

### Gap 4: No Actual Fine-Tuning Pipeline

The Python `/train` endpoint is a stub. There is no code to:
- Accept training data from the Java backend
- Fine-tune a ModernBERT/DistilBERT model
- Generate a new `label_map.json` from the taxonomy
- Save the trained model + tokenizer + ONNX export
- Report training metrics (accuracy, loss, per-category F1)

**Fix:** Implement the `/train` endpoint in the Python sidecar. It should:
1. Accept training data as JSON (text + category pairs)
2. Accept a label map (category → index mapping)
3. Fine-tune `answerdotai/ModernBERT-base` (or configurable base model)
4. Export to ONNX for production inference
5. Save model artifacts to a versioned directory
6. Return training metrics

### Gap 5: No Model Versioning

Currently one model directory (`/app/models/default`). No way to:
- Keep previous model versions
- Compare accuracy between versions
- Roll back to a previous model
- Track which training data produced which model

**Fix:** Version model directories as `/app/models/v{N}/`. Track model metadata in a new MongoDB collection or alongside the training job record.

### Gap 6: No Training Trigger or Admin Workflow

No mechanism to:
- Tell the admin "you have enough training data to train a useful model"
- Let the admin review training data distribution before triggering
- Monitor a training job in progress
- Promote a trained model to active

**Fix:** Add a training job model and controller that manages the lifecycle: READY → TRAINING → COMPLETED → PROMOTED.

## Implementation Plan

### Phase 1: Backfill Existing Classifications → Training Data

**New:** `gls-app-assembly/.../bootstrap/BertTrainingDataBackfillRunner.java`

On startup (once), if `bert_training_data` has < 10 AUTO_COLLECTED samples:
- Query all `classification_results` with confidence >= 0.8
- Join with `documents` for `extractedText`
- Skip if `sourceDocumentId` already exists in `bert_training_data` (dedup)
- Create `TrainingDataSample` with `source=AUTO_COLLECTED`, `verified=false`
- Truncate text to configured max length

Also: **enable auto-collection** by setting `bert.training.auto_collect_enabled = true` in the backfill runner (via `AppConfigService`).

This reuses the exact same field mapping as `BertTrainingDataCollector.tryCollect()` — same model, same truncation, same dedup check.

### Phase 2: Corrections → Training Data

**Modify:** The service that saves `ClassificationCorrection` records.

Find where corrections are created (the review/correction endpoint), and after saving the correction, also upsert a `TrainingDataSample`:

```
source = "CORRECTION"
categoryId = correction.correctedCategoryId
categoryName = correction.correctedCategoryName
sensitivityLabel = correction.correctedSensitivity
verified = true  (human-reviewed by definition)
confidence = 1.0 (human says this is correct)
sourceDocumentId = correction.documentId
text = document.extractedText (truncated)
```

If a sample already exists for this document (from auto-collection), update it with the corrected category and mark `verified=true`. Don't create a duplicate.

### Phase 3: Training Readiness Check

**Modify:** `BertModelController.java` — add a `/api/admin/bert/training-readiness` endpoint.

Returns:
- Total training samples (all sources)
- Verified count
- Category distribution (samples per category)
- Categories with < 5 samples (insufficient)
- Categories with >= 30 samples (ready)
- Recommended action: "Not enough data" / "Ready to train" / "Imbalanced — some categories under-represented"
- Whether corrections exist that haven't been incorporated

This is a read-only query across `bert_training_data` — no new models needed.

### Phase 4: Implement `/train` Endpoint (Python)

**Modify:** `bert-classifier/main.py` — replace the stub `/train` endpoint.

The Java backend calls this endpoint with:
```json
{
  "samples": [{"text": "...", "label": 0}, ...],
  "label_map": {"0": {"category_id": "...", "category_name": "...", "sensitivity_label": "..."}},
  "config": {
    "base_model": "answerdotai/ModernBERT-base",
    "epochs": 3,
    "batch_size": 16,
    "learning_rate": 2e-5,
    "max_length": 512,
    "val_split": 0.2,
    "model_version": "v2"
  }
}
```

Training flow:
1. Save training data to temp dir
2. Load base model + tokenizer from HuggingFace
3. Create train/val split (80/20 stratified)
4. Fine-tune with HuggingFace Trainer
5. Evaluate on validation set → per-category F1, accuracy, loss
6. Export to ONNX (via `optimum`)
7. Save model + tokenizer + label_map to `/app/models/v{N}/`
8. Return metrics + model path

This runs async — return a job ID immediately, poll `/train/{jobId}/status` for progress.

### Phase 5: Training Job Management (Java Backend)

**New model:** `gls-governance/.../models/BertTrainingJob.java`

```
Collection: bert_training_jobs
Fields:
  id, status (PENDING/TRAINING/COMPLETED/FAILED/PROMOTED),
  modelVersion, baseModel,
  trainingConfig (Map), 
  sampleCount, categoryCount, labelMap,
  metrics (accuracy, loss, per-category F1),
  startedAt, completedAt, startedBy,
  modelPath, promoted (boolean)
```

**New controller:** `gls-app-assembly/.../controllers/admin/BertTrainingJobController.java`

```
POST /api/admin/bert/training-jobs          → start training (builds data, calls /train)
GET  /api/admin/bert/training-jobs          → list jobs
GET  /api/admin/bert/training-jobs/{id}     → job detail + metrics
POST /api/admin/bert/training-jobs/{id}/promote → switch active model to this version
```

The `start training` endpoint:
1. Exports verified + high-confidence samples from `bert_training_data`
2. Generates label map from categories present in training data
3. POSTs to Python `/train` with samples + label_map + config
4. Creates `BertTrainingJob` with status=TRAINING
5. Background poll or callback updates status when complete

### Phase 6: Model Hot-Swap

**Modify:** `bert-classifier/main.py` — add `POST /models/activate` endpoint.

Accepts `{"model_dir": "/app/models/v2"}` and hot-swaps the loaded model + tokenizer + label_map without restart. The Java backend calls this after promoting a training job.

## File Summary

| Phase | Action | File |
|-------|--------|------|
| 1 | New | `gls-app-assembly/.../bootstrap/BertTrainingDataBackfillRunner.java` |
| 2 | Modify | Correction save endpoint (find where ClassificationCorrection is created) |
| 3 | Modify | `gls-app-assembly/.../controllers/admin/BertModelController.java` — add readiness endpoint |
| 4 | Modify | `bert-classifier/main.py` — implement `/train` and `/train/{jobId}/status` |
| 5 | New | `gls-governance/.../models/BertTrainingJob.java` |
| 5 | New | `gls-governance/.../repositories/BertTrainingJobRepository.java` |
| 5 | New | `gls-app-assembly/.../controllers/admin/BertTrainingJobController.java` |
| 6 | Modify | `bert-classifier/main.py` — add `/models/activate` |

## What is NOT Duplicated

- Phase 1 backfill reuses `TrainingDataSample` model and `TrainingDataSampleRepository` (existing)
- Phase 2 reuses the same model/repo for corrections → training data
- Phase 3 reuses existing `BertModelController` (adds one endpoint)
- Phase 4 reuses the existing Python sidecar container and model loading infrastructure
- Phase 5 reuses existing `BertModelController` for read endpoints, new controller only for job CRUD
- Training data export reuses the existing `/training-data` export logic pattern from `BertModelController`

## Training Data Quality Tiers

| Tier | Source | Verified | Weight | Count (current) |
|------|--------|----------|--------|-----------------|
| 1 (highest) | CORRECTION | Yes (by definition) | 3x oversample | 1 |
| 2 | MANUAL_UPLOAD + verified | Yes | 1x | 0 |
| 3 | AUTO_COLLECTED + verified | Yes | 1x | 0 |
| 4 | AUTO_COLLECTED (high conf ≥0.95) | No | 1x | ~55 (estimated from results) |
| 5 (lowest) | AUTO_COLLECTED (conf 0.8-0.95) | No | 0.5x or exclude | ~15 (estimated) |

After Phase 1 backfill, estimated training data: **~70 samples across 10+ categories**. Minimum viable for fine-tuning is ~30 samples per category, so the most-represented categories (Supplier Payments: 38, Employee Master Records: 8) can start training. Under-represented categories would continue using LLM until more data accumulates.

## Verification

1. Run Phase 1 backfill → check `bert_training_data` count jumps from 2 to ~70+
2. Enable auto-collection → classify a new document → verify sample appears
3. Correct a classification → verify training sample created/updated with corrected category
4. Hit `/api/admin/bert/training-readiness` → see category distribution
5. Trigger training job → monitor progress → see new model in `/app/models/v2/`
6. Promote model → verify BERT accelerator uses new model → demo document classified correctly
7. Check hit rate stats → BERT should start intercepting documents for trained categories
