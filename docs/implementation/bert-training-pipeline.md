# BERT Training Pipeline — Architecture & Operations Guide

## Overview

The BERT training pipeline enables IG Central to learn from its own classification history. Documents classified by the LLM become training data for a lightweight BERT model that can short-circuit the LLM for common document types — reducing cost, latency, and dependency on external AI providers.

```
Document → Pipeline → BERT Accelerator (fast, cheap)
                          ├─ Hit (confidence > 0.85) → skip LLM, use BERT result
                          └─ Miss → fall through to LLM (Claude) → classify
                                          ↓
                              Training data auto-collected
                                          ↓
                              Corrections refine training data
                                          ↓
                              Admin retrains BERT → promotes model
                                          ↓
                              BERT gets smarter → more hits → less LLM spend
```

---

## Training Data Sources

Training samples flow into the system from four sources, each with different trust levels:

| Source | Trust | Verified | How it enters |
|---|---|---|---|
| **CORRECTION** | Highest | Yes | Human overrides a classification in the review queue. Oversampled 3x during training. |
| **MANUAL_UPLOAD** | High | Yes | Admin uploads documents or pastes text via `/ai/models` page. |
| **AUTO_COLLECTED** | Medium | No | Pipeline auto-collects from high-confidence LLM classifications. |
| **BULK_IMPORT** | Medium | No | Batch import via admin API. |

### Auto-Collection Rules

Auto-collection is controlled by runtime config (Settings UI):

| Setting | Default | What it controls |
|---|---|---|
| `bert.training.auto_collect_enabled` | `false` | Master switch — enabled automatically after first backfill |
| `bert.training.auto_collect_min_confidence` | `0.8` | Only collect when LLM confidence exceeds this |
| `bert.training.auto_collect_corrected_only` | `false` | If true, only corrections are collected (highest quality) |
| `bert.training.auto_collect_categories` | `[]` (all) | Optional category whitelist |
| `bert.training.max_text_length` | `2000` | Truncate text to this length before storing |

### Data Quality Filters

Before a sample is saved, the collector checks:

1. **Deduplication by document ID** — same document won't be collected twice
2. **Duplicate text detection** — identical text content is rejected
3. **Minimum text length** — texts shorter than 50 characters are skipped
4. **Confidence threshold** — below `auto_collect_min_confidence` is skipped

### Startup Backfill

On first boot (or when `<10` auto-collected samples exist), `BertTrainingDataBackfillRunner` scans all existing `DocumentClassificationResult` records and creates training samples from high-confidence classifications. This bootstraps the training pipeline without manual data entry.

---

## Training Execution

### Triggering Training

Training is initiated from the `/ai/models` page:

1. Admin clicks **Train BERT** (optionally selecting specific samples)
2. `POST /api/admin/bert/training-jobs` is called
3. Controller validates minimum 10 usable samples
4. Builds label map (category → index mapping)
5. Oversamples CORRECTION-source samples 3x
6. Sends payload to the Python BERT classifier sidecar
7. Returns immediately — training runs asynchronously

### Incremental Learning

When a promoted model exists, new training runs fine-tune from it rather than starting from the base model. This is faster (fewer epochs needed) and preserves prior learning. The `base_model` defaults to the promoted model's path, falling back to `distilbert-base-uncased` for first-time training.

### Training Configuration

| Parameter | Default | Notes |
|---|---|---|
| `base_model` | `distilbert-base-uncased` (or promoted model) | 66M params, fits in 2GB container |
| `epochs` | `3` | Usually sufficient for fine-tuning |
| `batch_size` | `4` | Memory-safe for CPU/constrained Docker environments |
| `gradient_accumulation_steps` | `4` | Effective batch size = 16 without the memory cost |
| `learning_rate` | `2e-5` | Standard BERT fine-tuning rate |
| `max_length` | `256` | Token limit — halved from 512 to save memory |
| `val_split` | `0.2` | 80/20 train/validation split |

### Memory Optimisations

The training worker applies several memory-saving techniques:

- **Gradient checkpointing** — trades ~20% slower training for ~40% less memory
- **`dataloader_pin_memory=False`** — avoids unnecessary memory allocation on CPU
- **`save_total_limit=2`** — keeps only the 2 best checkpoints, preventing disk fill
- **Subprocess isolation** — training runs in a separate process so Uvicorn stays responsive

### Progress Reporting

Training progress is reported per-epoch:

1. The `ProgressCallback` writes intermediate results to `/tmp/train_{job_id}_result.json` after each epoch
2. The monitor thread in `main.py` polls this file every 3 seconds
3. The Spring Boot controller polls `/train/{job_id}/status` every 5 seconds
4. The UI polls `/api/admin/bert/training-jobs` every 5 seconds
5. Status shows as `TRAINING 2/3` with current loss

---

## Training Metrics

### Overall Metrics

| Metric | What it means | Good | Needs work | Poor |
|---|---|---|---|---|
| **Accuracy** | % of correct predictions on validation set | >70% | 50-70% | <50% |
| **F1 (weighted)** | Precision + recall balanced across classes | >70% | 50-70% | <50% |
| **Loss** | Cross-entropy loss — lower = better converged | <0.5 | 0.5-1.5 | >1.5 |

### Per-Class Metrics

After training, a detailed per-class breakdown is generated:

| Column | What it shows |
|---|---|
| **Precision** | When the model predicts this category, how often is it right? |
| **Recall** | Of all documents in this category, how many did the model find? |
| **F1** | Harmonic mean of precision and recall |
| **Support** | Number of validation samples in this category |

Categories with only 1 sample are highlighted in red — the model cannot learn these reliably.

### Data Quality Warnings

The training worker automatically generates warnings:

- **Low sample count** — "Very low sample count (20). Aim for 50-100+ samples per category."
- **Class imbalance** — "Severe class imbalance: largest category has 11x vs smallest 1."
- **Singleton categories** — "6 categories have only 1 sample — model cannot learn these reliably."

---

## Model Promotion

### Accuracy Gate

Models below 50% accuracy cannot be promoted. The promote endpoint returns an error:

```
"Model accuracy 40.0% is below the minimum 50% required for promotion.
Add more training data and retrain."
```

### Promotion Flow

1. Admin clicks **Promote** on a completed training job
2. `POST /api/admin/bert/training-jobs/{id}/promote`
3. Accuracy gate check (>50% required)
4. Calls Python sidecar `POST /models/activate` with the model directory
5. Sidecar hot-swaps: loads new tokenizer, model (ONNX or HuggingFace), and label map
6. Previous promoted model is demoted to COMPLETED status
7. New model is marked PROMOTED — pipeline accelerator now uses it

### Hot-Swap (Zero Downtime)

Model promotion does not require a container restart. The Python sidecar:
1. Loads the new tokenizer from the model directory
2. Tries ONNX Runtime first (fastest inference)
3. Falls back to HuggingFace transformers pipeline
4. Updates the global model state atomically

---

## Pipeline Integration

### BERT as Accelerator

BERT is registered as an `AcceleratorHandler` in the pipeline execution engine. When a pipeline includes a `bertClassifier` node:

1. Document text is truncated and sent to `POST /classify` on the sidecar
2. If confidence >= threshold (default 0.85), the result is used directly
3. The LLM node is **skipped** — saving cost and latency
4. If confidence is below threshold, the document proceeds to the LLM

### Statistics

The `/api/admin/bert/stats` endpoint tracks:

- **Total classified** — documents processed by any method
- **BERT hits** — documents where BERT was confident enough to skip the LLM
- **LLM classified** — documents that fell through to Claude
- **BERT hit rate** — percentage of classifications handled by BERT
- **Avg BERT confidence** — mean confidence of BERT classifications

---

## Retraining Advisor

A scheduled service (`BertRetrainingAdvisor`) runs daily at 06:00 and evaluates whether retraining would improve the model.

### Triggers

| Condition | Threshold |
|---|---|
| New samples since last training | >= 20 |
| New human corrections | >= 10 |
| Never trained but data available | >= 20 samples |
| Model staleness | > 14 days since last training (+ >= 5 new samples) |

### UI Banner

When retraining is recommended, a blue banner appears at the top of the `/ai/models` page with specific reasons:

> **Retraining recommended**
> - 32 new samples since last training
> - 12 new human corrections — high-value training signal

### API

```
GET /api/admin/bert/retraining-advice
```

Returns:
```json
{
  "recommendation": "RETRAIN_RECOMMENDED",
  "reasons": ["32 new samples since last training"],
  "totalSamples": 150,
  "corrections": 25,
  "newSamplesSinceLastTraining": 32,
  "lastTrainedAt": "2026-04-18T13:00:00Z",
  "checkedAt": "2026-04-19T06:00:00Z"
}
```

---

## Feedback Loop

### How Corrections Improve BERT

```
User corrects classification in review queue
    ↓
ClassificationCorrection saved to MongoDB
    ↓
BertTrainingDataCollector.collectCorrection() creates/updates TrainingDataSample
    source = "CORRECTION", verified = true, confidence = 1.0
    ↓
Next training run: corrections oversampled 3x
    ↓
Model learns from human judgement
```

### Trainable Correction Types

| Type | When | Effect |
|---|---|---|
| `APPROVED_CORRECT` | User confirms LLM was right | Positive reinforcement signal |
| `CATEGORY_CHANGED` | User assigns different category | Teaches correct category |
| `SENSITIVITY_CHANGED` | User changes sensitivity level | Teaches correct sensitivity |
| `BOTH_CHANGED` | User changes category + sensitivity | Teaches both |
| `PII_FLAGGED` | User reports missed PII | **Not collected** — feeds LLM via MCP instead |

---

## Infrastructure

### Container Configuration

```yaml
bert-classifier:
  memory: 4g                    # Limit to prevent OOM starving other services
  BERT_MODEL_DIR: /app/models/default
  BERT_LABEL_MAP: /app/models/default/label_map.json
  BERT_USE_ONNX: "true"
  BERT_MAX_LENGTH: "256"
  volumes:
    - ./data/bert-models:/app/models   # Persisted across restarts
```

### Service Communication

```
Spring Boot (api:8080)
    ↓ HTTP/1.1 (Uvicorn doesn't support HTTP/2 upgrade)
Python FastAPI (bert-classifier:8000)
    ├── /health          — health check
    ├── /classify        — inference
    ├── /train           — start training (subprocess)
    ├── /train/{id}/status — poll progress
    ├── /models          — list available models
    └── /models/activate — hot-swap model
```

### Key Files

| File | Purpose |
|---|---|
| `bert-classifier/main.py` | FastAPI service: inference + training orchestration |
| `bert-classifier/train_worker.py` | Subprocess: actual fine-tuning logic |
| `BertTrainingJobController.java` | Start training, list jobs, promote models |
| `BertModelController.java` | Service status, stats, readiness, retraining advice |
| `BertTrainingDataCollector.java` | Auto-collection + correction collection |
| `BertRetrainingAdvisor.java` | Daily retraining check |
| `TrainingDataController.java` | Manual sample management |
| `BertClassifierService.java` | Pipeline accelerator integration |
| `BertTrainingDataBackfillRunner.java` | Startup backfill from existing classifications |

---

## Troubleshooting

### Training fails with exit code -9

**Cause:** OOM kill. The Docker container ran out of memory during training.

**Fix:** Reduce `batch_size` (default 4), reduce `max_length` (default 256), or increase container memory limit in `docker-compose.yml`.

### Training completes but accuracy is very low (<50%)

**Cause:** Insufficient or imbalanced training data.

**Check:**
- Do you have at least 50+ samples total?
- Does every category have at least 5 samples?
- Check the per-class breakdown — are singleton categories dragging down the average?

**Fix:** Add more training data to underrepresented categories. Use manual upload or wait for more auto-collected samples.

### BERT is in demo mode after promotion

**Cause:** The model directory doesn't contain the expected files, or the hot-swap failed.

**Check:** `docker exec gls-bert-classifier ls /app/models/v1/` — should contain `config.json`, `tokenizer_config.json`, `label_map.json`, and either `model.onnx` or `model.safetensors`.

### 502 on training jobs endpoint

**Cause:** Container restart during training caused cascading failures. See `documentation/DRI/TROUBLESHOOT-BERT-502.md`.

**Fix:** Clear zombie jobs in MongoDB, rebuild containers.
