# `gls-bert-trainer`

Phase 1.4 BERT trainer for the ig-central cascade. Fine-tunes
ModernBERT on org-specific training samples (collected at
classification time by `BertTrainingDataCollector` in
`gls-app-assembly`) and publishes the resulting ONNX artefact to
MinIO under a versioned key.

The companion `gls-bert-inference` JVM service then loads the ONNX
artefact at startup or via `POST /v1/models/reload`. Per CSV #2:
training wins from Python (HuggingFace, distillation, ONNX export);
serving wins from the JVM (homogeneous deploy, observability, IAM,
lower memory than Python+CUDA).

## Pipeline

1. **Data load** (`data.py`): query the `bert_training_samples` Mongo
   collection for samples with the org's top-N categories.
2. **Fine-tune** (`train.py`): load `answerdotai/ModernBERT-base`,
   train a sequence-classification head against the labelled data,
   evaluate on a held-out split.
3. **ONNX export** (`train.py`): export the fine-tuned model with
   `optimum.onnxruntime.ORTModelForSequenceClassification`.
4. **Publish** (`publish.py`): upload the ONNX file + label-mapping
   metadata to MinIO under
   `${GLS_BERT_ARTIFACTS_BUCKET}/${trainerVersion}/${modelVersion}/model.onnx`.
5. **Notify** (`publish.py`): emit a `bert.training.completed`
   message so the inference service's reload trigger picks it up.

## Run modes

The trainer is a one-shot Kubernetes Job (or `docker run` for
local). Configuration is env-driven (see `config.py`):

```sh
MONGO_URI=mongodb://root:example@localhost:27017/governance_led_storage_main?authSource=admin \
MINIO_ENDPOINT=http://localhost:9000 \
MINIO_ACCESS_KEY=minioadmin \
MINIO_SECRET_KEY=minioadmin \
GLS_BERT_ARTIFACTS_BUCKET=gls-bert-artifacts \
GLS_BERT_TRAINER_TOP_N=3 \
GLS_BERT_TRAINER_MIN_SAMPLES_PER_CLASS=50 \
gls-bert-trainer
```

## What's in this Phase 1.4 first cut

- `data.py` — real pymongo reader with category filter + sample count gate.
- `train.py` — HuggingFace fine-tune + Optimum ONNX export. Real but
  conservative: defaults to ModernBERT-base, 3 epochs, batch size 16.
  Skipped (with a clear log) if the data loader returns fewer samples
  than `GLS_BERT_TRAINER_MIN_SAMPLES_PER_CLASS * top_n`.
- `publish.py` — real minio upload + label-mapping JSON sidecar.
- Tests covering the data loader's query shape and the publisher's
  object-key construction. The actual training run requires a GPU + a
  populated samples collection and is out of scope for unit tests.

## Why ModernBERT

Per architecture §8.1 / CSV #2: ModernBERT (Dec 2024) is faster and
more accurate than the older DistilBERT / DeBERTa baselines for
sub-1B-parameter classifiers. It supports up to 8K context which
matters for long documents that don't fit in DistilBERT's 512 token
window. License: Apache 2.0, suitable for commercial deployment.
