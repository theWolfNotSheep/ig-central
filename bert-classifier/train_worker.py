"""
Standalone training worker — runs in a subprocess so it doesn't block uvicorn.
Reads training data from a JSON file, fine-tunes the model, writes results to a JSON file.
"""
import json
import logging
import sys
import time
from collections import Counter
from pathlib import Path

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("train-worker")


def main():
    data_path = Path(sys.argv[1])
    with open(data_path) as f:
        data = json.load(f)

    job_id = data["job_id"]
    samples = data["samples"]
    label_map_raw = data["label_map"]
    config = data.get("config", {})
    result_path = Path(f"/tmp/train_{job_id}_result.json")

    result = {"status": "TRAINING", "progress": 0, "started_at": time.time()}

    try:
        import torch
        from transformers import (
            AutoTokenizer,
            AutoModelForSequenceClassification,
            TrainingArguments,
            Trainer,
        )
        from sklearn.model_selection import train_test_split
        from sklearn.metrics import accuracy_score, f1_score

        # Memory-safe defaults for CPU/constrained environments:
        #   batch_size=4 (down from 16) — reduces peak memory ~4x
        #   max_length=256 (down from 512) — halves token memory
        #   gradient_accumulation_steps=4 — effective batch=16 without the memory cost
        base_model = config.get("base_model", "distilbert-base-uncased")
        epochs = int(config.get("epochs", 3))
        batch_size = int(config.get("batch_size", 4))
        gradient_accumulation = int(config.get("gradient_accumulation_steps", 4))
        lr = float(config.get("learning_rate", 2e-5))
        max_len = int(config.get("max_length", 256))
        val_split = float(config.get("val_split", 0.2))
        model_version = config.get("model_version", f"v{job_id}")
        output_dir = f"/app/models/{model_version}"

        num_labels = len(label_map_raw)
        texts = [s["text"] for s in samples]
        labels = [int(s["label"]) for s in samples]

        logger.info("Training %s: %d samples, %d labels, %d epochs, batch=%d, grad_accum=%d, max_len=%d",
                     base_model, len(texts), num_labels, epochs, batch_size, gradient_accumulation, max_len)

        # Train/val split — fall back to random if stratified fails
        try:
            train_texts, val_texts, train_labels, val_labels = train_test_split(
                texts, labels, test_size=val_split, stratify=labels, random_state=42
            )
        except ValueError:
            logger.warning("Stratified split failed, using random split")
            train_texts, val_texts, train_labels, val_labels = train_test_split(
                texts, labels, test_size=val_split, random_state=42
            )

        tok = AutoTokenizer.from_pretrained(base_model)
        model = AutoModelForSequenceClassification.from_pretrained(
            base_model, num_labels=num_labels, ignore_mismatched_sizes=True,
        )

        # Enable gradient checkpointing to trade compute for memory
        if hasattr(model, "gradient_checkpointing_enable"):
            model.gradient_checkpointing_enable()
            logger.info("Gradient checkpointing enabled — reduces memory at cost of ~20%% slower training")

        train_enc = tok(train_texts, truncation=True, padding=True, max_length=max_len)
        val_enc = tok(val_texts, truncation=True, padding=True, max_length=max_len)

        class SimpleDataset(torch.utils.data.Dataset):
            def __init__(self, encodings, ds_labels):
                self.encodings = encodings
                self.labels = ds_labels
            def __len__(self):
                return len(self.labels)
            def __getitem__(self, idx):
                item = {k: torch.tensor(v[idx]) for k, v in self.encodings.items()}
                item["labels"] = torch.tensor(self.labels[idx])
                return item

        train_ds = SimpleDataset(train_enc, train_labels)
        val_ds = SimpleDataset(val_enc, val_labels)

        args = TrainingArguments(
            output_dir=output_dir,
            num_train_epochs=epochs,
            per_device_train_batch_size=batch_size,
            per_device_eval_batch_size=batch_size,
            gradient_accumulation_steps=gradient_accumulation,
            learning_rate=lr,
            eval_strategy="epoch",
            save_strategy="epoch",
            load_best_model_at_end=True,
            metric_for_best_model="accuracy",
            logging_steps=10,
            report_to="none",
            seed=42,
            dataloader_pin_memory=False,
            save_total_limit=2,
        )

        def compute_metrics(eval_pred):
            preds = eval_pred.predictions.argmax(-1)
            acc = accuracy_score(eval_pred.label_ids, preds)
            f1 = f1_score(eval_pred.label_ids, preds, average="weighted", zero_division=0)
            return {"accuracy": acc, "f1": f1}

        # Progress callback: writes intermediate results after each epoch
        from transformers import TrainerCallback

        class ProgressCallback(TrainerCallback):
            def on_epoch_end(self, targs, state, control, **kwargs):
                progress = int((state.epoch / epochs) * 100)
                epoch_result = {
                    "status": "TRAINING",
                    "progress": progress,
                    "current_epoch": int(state.epoch),
                    "total_epochs": epochs,
                    "current_loss": round(state.log_history[-1].get("loss", 0), 4) if state.log_history else None,
                    "started_at": result.get("started_at", time.time()),
                }
                # Write progress to result file so the monitor thread can relay it
                with open(result_path, "w") as pf:
                    json.dump(epoch_result, pf)
                logger.info("Epoch %d/%d complete (progress: %d%%)", int(state.epoch), epochs, progress)

        trainer = Trainer(
            model=model, args=args,
            train_dataset=train_ds, eval_dataset=val_ds,
            compute_metrics=compute_metrics,
            callbacks=[ProgressCallback()],
        )

        trainer.train()

        eval_result = trainer.evaluate()
        logger.info("Eval results: %s", eval_result)

        # Per-class metrics for detailed reporting
        from sklearn.metrics import classification_report, confusion_matrix
        val_preds = trainer.predict(val_ds)
        pred_labels = val_preds.predictions.argmax(-1)
        true_labels = val_labels

        label_names = [label_map_raw.get(str(i), {}).get("category_name", f"Label {i}")
                       for i in range(num_labels)]

        per_class = {}
        cls_report = classification_report(true_labels, pred_labels, labels=list(range(num_labels)),
                                            target_names=label_names, output_dict=True, zero_division=0)
        for name in label_names:
            if name in cls_report:
                entry = cls_report[name]
                per_class[name] = {
                    "precision": round(entry["precision"], 3),
                    "recall": round(entry["recall"], 3),
                    "f1": round(entry["f1-score"], 3),
                    "support": int(entry["support"]),
                }

        # Data quality warnings
        warnings = []
        total_samples = len(texts)
        if total_samples < 50:
            warnings.append(f"Very low sample count ({total_samples}). Aim for 50-100+ samples per category.")
        samples_per_cat = Counter(labels)
        min_cat = min(samples_per_cat.values())
        max_cat = max(samples_per_cat.values())
        if max_cat > min_cat * 5:
            warnings.append(f"Severe class imbalance: largest category has {max_cat}x vs smallest {min_cat}. Add more samples to underrepresented categories.")
        cats_with_one = sum(1 for v in samples_per_cat.values() if v <= 1)
        if cats_with_one > 0:
            warnings.append(f"{cats_with_one} categories have only 1 sample — model cannot learn these reliably.")

        trainer.save_model(output_dir)
        tok.save_pretrained(output_dir)

        # Save label map
        lm_path = Path(output_dir) / "label_map.json"
        with open(lm_path, "w") as f:
            json.dump(label_map_raw, f, indent=2)

        # Export to ONNX
        onnx_exported = False
        try:
            from optimum.onnxruntime import ORTModelForSequenceClassification
            ort_model = ORTModelForSequenceClassification.from_pretrained(output_dir, export=True)
            ort_model.save_pretrained(output_dir)
            onnx_exported = True
            logger.info("ONNX export complete: %s/model.onnx", output_dir)
        except Exception as e:
            logger.warning("ONNX export failed (model still usable via transformers): %s", e)

        result = {
            "status": "COMPLETED",
            "progress": 100,
            "model_path": output_dir,
            "onnx_exported": onnx_exported,
            "metrics": {
                "accuracy": round(eval_result.get("eval_accuracy", 0), 4),
                "f1": round(eval_result.get("eval_f1", 0), 4),
                "loss": round(eval_result.get("eval_loss", 0), 4),
                "train_samples": len(train_texts),
                "val_samples": len(val_texts),
                "epochs": epochs,
                "label_distribution": dict(Counter(labels)),
                "per_class": per_class,
                "warnings": warnings,
            },
            "started_at": data.get("started_at", time.time()),
            "completed_at": time.time(),
        }
        result["duration_seconds"] = int(result["completed_at"] - result["started_at"])

        logger.info("Training complete: %s — accuracy=%.3f, f1=%.3f",
                     model_version,
                     result["metrics"]["accuracy"],
                     result["metrics"]["f1"])

    except Exception as e:
        logger.error("Training failed: %s", e, exc_info=True)
        result = {
            "status": "FAILED",
            "error": str(e),
            "completed_at": time.time(),
        }

    with open(result_path, "w") as f:
        json.dump(result, f)


if __name__ == "__main__":
    main()
