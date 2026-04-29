"""Fine-tune ModernBERT on the loaded dataset and export ONNX.

Imports of HuggingFace + Optimum are deferred until inside the actual
training functions so the module can be imported in test environments
that don't have torch / transformers / optimum on the path. The unit
tests under ``tests/`` exercise the data loader and the publisher; the
training step itself is integration-tested end-to-end (gated on a GPU
+ a populated samples collection — same blocker as issue #7 across the
v2 services).
"""

from __future__ import annotations

import logging
from dataclasses import dataclass
from pathlib import Path
from typing import TYPE_CHECKING

from .data import LoadedDataset, split_train_test

if TYPE_CHECKING:  # pragma: no cover — typing-only imports
    pass

logger = logging.getLogger(__name__)


@dataclass(frozen=True)
class TrainingResult:
    onnx_path: Path
    """Filesystem path to the exported ONNX file."""

    labels: list[str]
    """Ordered label list — index corresponds to softmax output."""

    eval_metrics: dict[str, float]
    """Held-out evaluation metrics (accuracy, f1_macro, plus per-label
    precision / recall if computed)."""

    base_model_id: str
    epochs: int
    train_size: int
    test_size: int


def fine_tune_and_export(
    dataset: LoadedDataset,
    base_model_id: str,
    epochs: int,
    batch_size: int,
    learning_rate: float,
    max_seq_length: int,
    output_dir: Path,
) -> TrainingResult:
    """Run the fine-tune + ONNX export pipeline.

    Imports HuggingFace + Optimum lazily so the rest of the module
    stays importable in test environments. Idempotent on
    ``output_dir`` — overwrites if the dir already exists.
    """
    # Lazy imports — see module docstring.
    import torch
    from datasets import Dataset
    from optimum.onnxruntime import ORTModelForSequenceClassification
    from sklearn.metrics import accuracy_score, f1_score, precision_recall_fscore_support
    from transformers import (
        AutoModelForSequenceClassification,
        AutoTokenizer,
        Trainer,
        TrainingArguments,
    )

    output_dir = output_dir.resolve()
    output_dir.mkdir(parents=True, exist_ok=True)

    labels = [label_id for label_id, _ in dataset.label_mapping]
    label_to_id = {label: idx for idx, label in enumerate(labels)}

    train_samples, test_samples = split_train_test(dataset.samples)
    logger.info(
        "fine-tune split: train=%d test=%d labels=%s",
        len(train_samples),
        len(test_samples),
        labels,
    )

    tokenizer = AutoTokenizer.from_pretrained(base_model_id)

    def _to_dataset(samples) -> "Dataset":
        return Dataset.from_dict(
            {
                "text": [s.text for s in samples],
                "label": [label_to_id[s.label] for s in samples],
            }
        )

    def _tokenize(batch):
        return tokenizer(
            batch["text"],
            truncation=True,
            padding="max_length",
            max_length=max_seq_length,
        )

    train_ds = _to_dataset(train_samples).map(_tokenize, batched=True)
    test_ds = _to_dataset(test_samples).map(_tokenize, batched=True)

    model = AutoModelForSequenceClassification.from_pretrained(
        base_model_id,
        num_labels=len(labels),
        id2label={idx: label for label, idx in label_to_id.items()},
        label2id=label_to_id,
    )

    training_args = TrainingArguments(
        output_dir=str(output_dir / "checkpoint"),
        num_train_epochs=epochs,
        per_device_train_batch_size=batch_size,
        per_device_eval_batch_size=batch_size,
        learning_rate=learning_rate,
        eval_strategy="epoch",
        save_strategy="epoch",
        logging_steps=50,
        load_best_model_at_end=True,
        metric_for_best_model="f1_macro",
        report_to=[],
    )

    def _metrics(eval_pred):
        logits, label_ids = eval_pred
        preds = logits.argmax(axis=1)
        precision, recall, f1, _ = precision_recall_fscore_support(
            label_ids, preds, average=None, zero_division=0, labels=range(len(labels))
        )
        return {
            "accuracy": float(accuracy_score(label_ids, preds)),
            "f1_macro": float(f1_score(label_ids, preds, average="macro", zero_division=0)),
            "per_label": [
                {"label": labels[i], "precision": float(precision[i]),
                 "recall": float(recall[i]), "f1": float(f1[i])}
                for i in range(len(labels))
            ],
        }

    trainer = Trainer(
        model=model,
        args=training_args,
        train_dataset=train_ds,
        eval_dataset=test_ds,
        tokenizer=tokenizer,
        compute_metrics=_metrics,
    )

    trainer.train()
    eval_metrics = trainer.evaluate()
    # Strip Trainer's own loss / runtime keys — we only want our own.
    eval_metrics_clean = {
        k.removeprefix("eval_"): v
        for k, v in eval_metrics.items()
        if k in ("eval_accuracy", "eval_f1_macro", "eval_per_label")
    }

    # Save model + tokenizer to a HF-format dir, then re-load via
    # Optimum which exports the ONNX graph alongside.
    hf_dir = output_dir / "hf"
    trainer.save_model(str(hf_dir))
    tokenizer.save_pretrained(str(hf_dir))

    ort_model = ORTModelForSequenceClassification.from_pretrained(
        str(hf_dir), export=True
    )
    onnx_dir = output_dir / "onnx"
    ort_model.save_pretrained(str(onnx_dir))

    onnx_file = onnx_dir / "model.onnx"
    if not onnx_file.exists():
        # Optimum names the file `model.onnx` by default; keep this
        # check explicit so a future Optimum bump that changes the
        # output name surfaces immediately.
        candidates = list(onnx_dir.glob("*.onnx"))
        if not candidates:
            raise RuntimeError(
                f"ONNX export produced no .onnx file in {onnx_dir}"
            )
        onnx_file = candidates[0]

    return TrainingResult(
        onnx_path=onnx_file,
        labels=labels,
        eval_metrics=eval_metrics_clean,
        base_model_id=base_model_id,
        epochs=epochs,
        train_size=len(train_samples),
        test_size=len(test_samples),
    )
