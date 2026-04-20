# BERT Training Pipeline — Improvements (April 2026)

## Summary

A series of improvements to the BERT training pipeline addressing OOM crashes, poor metrics visibility, data quality, and operational gaps. These changes transform BERT training from a fragile manual process into a resilient, self-monitoring system.

---

## 1. OOM-Safe Training Defaults

**Problem:** Training ModernBERT-base (395M params) with `batch_size=16` and `max_length=512` required ~4GB additional memory. With the Docker VM at 7.65GB shared across 25+ containers, training consistently caused OOM kills (exit code -9), which cascaded into 502 errors across the platform.

**Changes:**

| Parameter | Before | After | Memory impact |
|---|---|---|---|
| `base_model` | ModernBERT-base (395M) | distilbert-base-uncased (66M) | ~6x smaller |
| `batch_size` | 16 | 4 | ~4x less peak memory |
| `max_length` | 512 | 256 | ~2x less token memory |
| `gradient_accumulation_steps` | (none) | 4 | Effective batch=16, sequential memory |
| `gradient_checkpointing` | (none) | enabled | ~40% less activation memory |
| `dataloader_pin_memory` | true (default) | false | Avoids unnecessary CPU memory allocation |
| `save_total_limit` | unlimited | 2 | Prevents disk fill from checkpoints |
| Container memory limit | unlimited | 4GB | Prevents OOM from starving other services |

**Effective batch size is unchanged** (4 x 4 accumulation = 16), so training quality is preserved while peak memory drops ~8-10x.

**Files:** `train_worker.py`, `BertTrainingJobController.java`, `docker-compose.yml`

---

## 2. Per-Class Metrics & Data Quality Warnings

**Problem:** Training results showed only overall accuracy/F1/loss — three numbers that don't tell you *why* the model is underperforming or *which categories* need more data.

**Changes:**

### Per-Class Breakdown
After training, `sklearn.classification_report` generates precision/recall/F1/support per category. This is stored in `metrics.per_class` on the training job and displayed as an expandable table in the UI.

Categories with only 1 validation sample are highlighted in red — the model cannot learn these reliably.

### Colour-Coded Metrics
Overall metrics are now colour-coded in the UI:
- Green: >70% (good)
- Amber: 50-70% (needs work)
- Red: <50% (poor)

### Automatic Warnings
The training worker generates actionable warnings based on data quality:
- Low total sample count (<50)
- Severe class imbalance (largest category >5x smallest)
- Singleton categories (only 1 sample)

These appear as an amber banner in the training history card.

**Files:** `train_worker.py`, `models/page.tsx`

---

## 3. Minimum Accuracy Gate on Promotion

**Problem:** An admin could promote a model with 30% accuracy to production. The pipeline would then make bad classifications with high confidence, eroding user trust.

**Change:** The promote endpoint now checks accuracy before allowing promotion:

```
if accuracy < 50%:
    return 400 "Model accuracy 40.0% is below the minimum 50% required
    for promotion. Add more training data and retrain."
```

The 50% threshold is deliberately low — it's a safety floor, not a quality bar. Even with few categories, a model below 50% is doing worse than informed guessing and should not be deployed.

**File:** `BertTrainingJobController.java`

---

## 4. Incremental Learning (Fine-Tune from Promoted Model)

**Problem:** Every training run started from the base `distilbert-base-uncased` model, discarding all prior learning. This meant:
- Each run needed more epochs to converge
- Knowledge from previous training was lost
- More data was needed to reach the same accuracy

**Change:** When a promoted model exists, new training runs default to fine-tuning from it:

```java
var promoted = jobRepo.findByPromotedTrue();
if (promoted.isPresent() && promoted.get().getModelPath() != null) {
    defaultBaseModel = promoted.get().getModelPath();
}
```

The admin can still override this by specifying a `base_model` in the training config (e.g. to reset to base after a taxonomy restructure).

**File:** `BertTrainingJobController.java`

---

## 5. Epoch Progress Reporting

**Problem:** Training showed 0% then jumped to COMPLETED or FAILED. For a multi-epoch training run that takes several minutes, there was no visibility into progress.

**Change:** A `ProgressCallback` in the training worker writes intermediate results to the result file after each epoch:

```python
class ProgressCallback(TrainerCallback):
    def on_epoch_end(self, targs, state, control, **kwargs):
        # Write progress to result file
        {"status": "TRAINING", "progress": 66, "current_epoch": 2, "total_epochs": 3}
```

The monitor thread in `main.py` polls this file every 3 seconds and updates the in-memory job status. The UI shows `TRAINING 2/3` instead of just `TRAINING`.

**Files:** `train_worker.py`, `main.py`, `models/page.tsx`

---

## 6. Training Data Quality Filters

**Problem:** Duplicate documents, very short texts, and identical content could all enter the training data, inflating sample counts without adding signal.

**Changes:**

| Filter | What it catches |
|---|---|
| **Duplicate text** | Identical text content already in training data (new `existsByText` query) |
| **Short text** | Documents with <50 characters of extracted text — too little signal for classification |
| **Document ID dedupe** | Same document collected twice (existing, strengthened) |
| **Corrected-only mode** | Optional: only collect from human-corrected classifications |

**Files:** `BertTrainingDataCollector.java`, `TrainingDataSampleRepository.java`

---

## 7. Retraining Advisor

**Problem:** Training was entirely manual. The system collected corrections and new samples but never told the admin it was time to retrain.

**Change:** `BertRetrainingAdvisor` is a scheduled service that runs daily at 06:00 and evaluates whether retraining is warranted.

### Evaluation Criteria

| Condition | Threshold | Rationale |
|---|---|---|
| New samples since last training | >= 20 | Enough new data to improve the model |
| New human corrections | >= 10 | Corrections are the highest-value signal |
| Never trained + data available | >= 20 samples | First model should be trained ASAP |
| Model staleness + new data | > 14 days + >= 5 samples | Old models drift as document patterns change |

### UI Integration

When `recommendation == "RETRAIN_RECOMMENDED"`, a blue banner appears at the top of the `/ai/models` page:

> **Retraining recommended**
> - 32 new samples since last training
> - 12 new human corrections -- high-value training signal

### API

```
GET /api/admin/bert/retraining-advice
```

**Files:** New `BertRetrainingAdvisor.java`, `BertModelController.java`, `models/page.tsx`

---

## 8. Proxy Error Handling

**Problem:** The Next.js proxy route at `web/src/app/api/proxy/[...path]/route.ts` had no error handling. When the backend was temporarily unavailable (e.g. during container restarts), the `fetch()` threw an unhandled exception, causing Next.js to return an opaque 500 error.

**Change:** Added try/catch around the upstream fetch:

```typescript
try {
    const upstream = await fetch(upstreamUrl, { ... });
    return buildDownstreamResponse(upstream);
} catch (err) {
    return Response.json(
        { error: "Backend unavailable", detail: message },
        { status: 502 },
    );
}
```

Now returns a clean 502 with error details instead of crashing.

**File:** `web/src/app/api/proxy/[...path]/route.ts`

---

## Remaining Gaps (Future Work)

These were identified during the review but not yet implemented:

| Gap | Impact | Effort |
|---|---|---|
| **Confidence calibration** — BERT confidence scores are often poorly calibrated. Temperature scaling after training would make the 0.85 threshold meaningful. | High | Medium |
| **Drift detection** — Track BERT classifications that get overridden in review queue. Alert when override rate exceeds 20%. | Medium | Medium |
| **Train/val data leakage** — Group samples by `sourceDocumentId` during train/val split to prevent the same document appearing in both sets. | Medium | Low |
| **Stable label registry** — Persistent category → index mapping across training runs. Currently, indices are reassigned each run, breaking incremental learning when categories change. | Medium | Medium |
| **Shadow mode** — Run new model alongside current one and compare results before promoting. | Medium | High |
| **Multi-tenant training** — Scope training data and models per tenant. | Low (for now) | High |
