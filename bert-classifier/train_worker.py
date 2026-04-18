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

        base_model = config.get("base_model", "answerdotai/ModernBERT-base")
        epochs = int(config.get("epochs", 3))
        batch_size = int(config.get("batch_size", 16))
        lr = float(config.get("learning_rate", 2e-5))
        max_len = int(config.get("max_length", 512))
        val_split = float(config.get("val_split", 0.2))
        model_version = config.get("model_version", f"v{job_id}")
        output_dir = f"/app/models/{model_version}"

        num_labels = len(label_map_raw)
        texts = [s["text"] for s in samples]
        labels = [int(s["label"]) for s in samples]

        logger.info("Training %s: %d samples, %d labels, %d epochs",
                     base_model, len(texts), num_labels, epochs)

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
            learning_rate=lr,
            eval_strategy="epoch",
            save_strategy="epoch",
            load_best_model_at_end=True,
            metric_for_best_model="accuracy",
            logging_steps=10,
            report_to="none",
            seed=42,
        )

        def compute_metrics(eval_pred):
            preds = eval_pred.predictions.argmax(-1)
            acc = accuracy_score(eval_pred.label_ids, preds)
            f1 = f1_score(eval_pred.label_ids, preds, average="weighted", zero_division=0)
            return {"accuracy": acc, "f1": f1}

        trainer = Trainer(
            model=model, args=args,
            train_dataset=train_ds, eval_dataset=val_ds,
            compute_metrics=compute_metrics,
        )

        trainer.train()

        eval_result = trainer.evaluate()
        logger.info("Eval results: %s", eval_result)

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
