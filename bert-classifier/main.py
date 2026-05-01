"""
BERT Classifier Sidecar Service

A FastAPI service that runs BERT-based document classification.
Designed to sit alongside the IGC Java backend as a pipeline accelerator.

Supports two modes:
1. ONNX Runtime inference (production) — fast, CPU-optimized
2. HuggingFace transformers pipeline (development/fine-tuning)

The service loads a label map from a JSON file that maps model output
indices to IGC taxonomy categories. This file is generated during
fine-tuning and must be mounted or baked into the container.
"""

import json
import logging
import os
import time
from contextlib import asynccontextmanager
from pathlib import Path
from typing import Optional

import numpy as np
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("bert-classifier")

# ── Configuration ────────────────────────────────────────────────────────

MODEL_DIR = os.getenv("BERT_MODEL_DIR", "/app/models/default")
LABEL_MAP_PATH = os.getenv("BERT_LABEL_MAP", "/app/models/default/label_map.json")
USE_ONNX = os.getenv("BERT_USE_ONNX", "true").lower() == "true"
MAX_LENGTH = int(os.getenv("BERT_MAX_LENGTH", "512"))

# ── State ────────────────────────────────────────────────────────────────

tokenizer = None
onnx_session = None
hf_pipeline = None
label_map: dict[int, dict] = {}  # idx -> {category_id, category_name, sensitivity_label}


def load_label_map() -> dict[int, dict]:
    """Load the label map that maps model output indices to IGC categories."""
    path = Path(LABEL_MAP_PATH)
    if not path.exists():
        logger.warning("No label_map.json found at %s — using demo labels", path)
        return _demo_label_map()

    with open(path) as f:
        raw = json.load(f)

    # Support both {0: {...}} and {"0": {...}} formats
    return {int(k): v for k, v in raw.items()}


def _demo_label_map() -> dict[int, dict]:
    """Fallback demo labels for development/testing."""
    return {
        0: {"category_id": "demo-hr", "category_name": "HR Documents", "sensitivity_label": "CONFIDENTIAL"},
        1: {"category_id": "demo-finance", "category_name": "Financial Records", "sensitivity_label": "RESTRICTED"},
        2: {"category_id": "demo-legal", "category_name": "Legal Documents", "sensitivity_label": "CONFIDENTIAL"},
        3: {"category_id": "demo-operations", "category_name": "Operations", "sensitivity_label": "INTERNAL"},
        4: {"category_id": "demo-general", "category_name": "General", "sensitivity_label": "PUBLIC"},
    }


def find_best_model_dir() -> Path:
    """Find the best available model directory.
    Priority: configured MODEL_DIR > model with most labels (multi-category) > fallback to demo.
    Single-category models are deprioritised — they classify everything as one category."""
    configured = Path(MODEL_DIR)
    if configured.exists() and (configured / "label_map.json").exists():
        return configured

    # Check for a promoted model marker file (written by the promote endpoint)
    promoted_marker = Path("/app/models/.promoted")
    if promoted_marker.exists():
        promoted_dir = Path(promoted_marker.read_text().strip())
        if promoted_dir.exists() and (promoted_dir / "label_map.json").exists():
            logger.info("Loading promoted model from marker: %s", promoted_dir)
            return promoted_dir

    # Scan /app/models for versioned models and pick the best one
    models_root = Path("/app/models")
    if models_root.exists():
        candidates = []
        for d in models_root.iterdir():
            if not d.is_dir():
                continue
            label_map_path = d / "label_map.json"
            has_model = (d / "model.onnx").exists() or (d / "config.json").exists()
            if label_map_path.exists() and has_model:
                try:
                    with open(label_map_path) as f:
                        label_count = len(json.load(f))
                except Exception:
                    label_count = 0
                candidates.append((d, label_count))

        if candidates:
            # Prefer models with more categories (multi-category > single-category)
            candidates.sort(key=lambda x: (-x[1], x[0].name))
            best, labels = candidates[0]
            if labels < 2:
                logger.warning("Only single-category models found — loading %s but it will classify everything the same", best)
            else:
                logger.info("Auto-loading best trained model: %s (%d labels)", best, labels)
            return best

    return configured  # Fall through to demo mode


def load_model():
    """Load the BERT model — ONNX or HuggingFace transformers."""
    global tokenizer, onnx_session, hf_pipeline, MODEL_DIR

    model_path = find_best_model_dir()
    MODEL_DIR = str(model_path)

    # Always load the tokenizer
    from transformers import AutoTokenizer

    if model_path.exists() and (model_path / "tokenizer_config.json").exists():
        logger.info("Loading tokenizer from %s", model_path)
        tokenizer = AutoTokenizer.from_pretrained(str(model_path))
    else:
        logger.info("Loading default tokenizer: distilbert-base-uncased")
        tokenizer = AutoTokenizer.from_pretrained("distilbert-base-uncased")

    # Try ONNX first
    onnx_path = model_path / "model.onnx"
    if USE_ONNX and onnx_path.exists():
        import onnxruntime as ort
        logger.info("Loading ONNX model from %s", onnx_path)
        onnx_session = ort.InferenceSession(
            str(onnx_path),
            providers=["CPUExecutionProvider"],
        )
        logger.info("ONNX model loaded — inputs: %s", [i.name for i in onnx_session.get_inputs()])
        return

    # Fall back to HuggingFace pipeline
    hf_model_path = model_path / "config.json"
    if hf_model_path.exists():
        from transformers import pipeline
        logger.info("Loading HuggingFace model from %s", model_path)
        hf_pipeline = pipeline("text-classification", model=str(model_path), tokenizer=tokenizer)
        return

    # No model found — run in demo mode with random predictions
    logger.warning("No model found in %s — running in DEMO mode with random predictions", model_path)


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Load model on startup."""
    global label_map, LABEL_MAP_PATH
    # Load model first (may update MODEL_DIR via auto-discovery)
    load_model()
    # Load label map from the active model directory
    discovered_label_map = Path(MODEL_DIR) / "label_map.json"
    if discovered_label_map.exists():
        LABEL_MAP_PATH = str(discovered_label_map)
    label_map = load_label_map()
    logger.info("BERT classifier ready — %d labels, onnx=%s, hf=%s, demo=%s, model_dir=%s",
                len(label_map), onnx_session is not None, hf_pipeline is not None,
                onnx_session is None and hf_pipeline is None, MODEL_DIR)
    yield


app = FastAPI(
    title="IGC BERT Classifier",
    description="Document classification sidecar using fine-tuned BERT models",
    version="1.0.0",
    lifespan=lifespan,
)

# ── Request / Response Models ────────────────────────────────────────────


class ClassifyRequest(BaseModel):
    text: str
    document_id: Optional[str] = None
    mime_type: Optional[str] = None
    model_id: Optional[str] = None


class ClassifyResponse(BaseModel):
    category_id: Optional[str] = None
    category_name: Optional[str] = None
    sensitivity_label: Optional[str] = None
    confidence: float = 0.0
    reasoning: str = ""
    tags: list[str] = []
    model_id: str = "default"
    inference_ms: int = 0


class HealthResponse(BaseModel):
    status: str
    model_loaded: bool
    mode: str
    label_count: int


# ── Inference ────────────────────────────────────────────────────────────


def classify_onnx(text: str) -> tuple[int, float, list[float]]:
    """Run classification using ONNX Runtime."""
    inputs = tokenizer(
        text,
        return_tensors="np",
        max_length=MAX_LENGTH,
        truncation=True,
        padding="max_length",
    )

    input_feed = {
        "input_ids": inputs["input_ids"].astype(np.int64),
        "attention_mask": inputs["attention_mask"].astype(np.int64),
    }

    outputs = onnx_session.run(None, input_feed)
    logits = outputs[0][0]  # First output, first batch item

    # Softmax
    exp_logits = np.exp(logits - np.max(logits))
    probs = exp_logits / exp_logits.sum()

    predicted_idx = int(np.argmax(probs))
    confidence = float(probs[predicted_idx])

    return predicted_idx, confidence, probs.tolist()


def classify_hf(text: str) -> tuple[int, float, list[float]]:
    """Run classification using HuggingFace transformers pipeline."""
    results = hf_pipeline(text, truncation=True, max_length=MAX_LENGTH, top_k=None)

    # Results are sorted by score descending
    best = results[0]
    # Extract label index from "LABEL_N" format
    label_str = best["label"]
    if label_str.startswith("LABEL_"):
        predicted_idx = int(label_str.split("_")[1])
    else:
        # Try to find the label in our map
        predicted_idx = 0
        for idx, info in label_map.items():
            if info.get("category_name") == label_str:
                predicted_idx = idx
                break

    confidence = float(best["score"])
    probs = [r["score"] for r in results]

    return predicted_idx, confidence, probs


def classify_demo(text: str) -> tuple[int, float, list[float]]:
    """Demo mode — returns a deterministic prediction based on text hash."""
    text_hash = hash(text) % len(label_map) if label_map else 0
    num_labels = max(len(label_map), 1)

    # Generate fake but deterministic probabilities
    probs = [0.05] * num_labels
    probs[text_hash] = 0.75
    total = sum(probs)
    probs = [p / total for p in probs]

    return text_hash, probs[text_hash], probs


# ── Endpoints ────────────────────────────────────────────────────────────


@app.post("/classify", response_model=ClassifyResponse)
async def classify(request: ClassifyRequest):
    """Classify a document's text and return the predicted category."""
    if not request.text.strip():
        raise HTTPException(status_code=400, detail="Text is required")

    start = time.time()

    if onnx_session is not None:
        predicted_idx, confidence, probs = classify_onnx(request.text)
        mode = "onnx"
    elif hf_pipeline is not None:
        predicted_idx, confidence, probs = classify_hf(request.text)
        mode = "transformers"
    else:
        predicted_idx, confidence, probs = classify_demo(request.text)
        mode = "demo"

    inference_ms = int((time.time() - start) * 1000)

    # Map prediction to IGC category
    label_info = label_map.get(predicted_idx, {})
    category_id = label_info.get("category_id")
    category_name = label_info.get("category_name", f"Unknown ({predicted_idx})")
    sensitivity_label = label_info.get("sensitivity_label", "INTERNAL")

    # Build reasoning string
    top_3 = sorted(enumerate(probs), key=lambda x: x[1], reverse=True)[:3]
    reasoning_parts = [
        f"BERT ({mode}) prediction: {category_name} ({confidence:.3f})"
    ]
    if len(top_3) > 1:
        alternatives = [
            f"{label_map.get(idx, {}).get('category_name', f'Label {idx}')}: {prob:.3f}"
            for idx, prob in top_3[1:]
        ]
        reasoning_parts.append(f"Alternatives: {', '.join(alternatives)}")

    return ClassifyResponse(
        category_id=category_id,
        category_name=category_name,
        sensitivity_label=sensitivity_label,
        confidence=confidence,
        reasoning=". ".join(reasoning_parts),
        tags=["bert-classified", f"bert-{mode}"],
        model_id=request.model_id or "default",
        inference_ms=inference_ms,
    )


@app.get("/health", response_model=HealthResponse)
async def health():
    """Health check endpoint."""
    if onnx_session is not None:
        mode = "onnx"
        loaded = True
    elif hf_pipeline is not None:
        mode = "transformers"
        loaded = True
    else:
        mode = "demo"
        loaded = False

    return HealthResponse(
        status="ok",
        model_loaded=loaded,
        mode=mode,
        label_count=len(label_map),
    )


@app.get("/models")
async def list_models():
    """List available models."""
    models_dir = Path("/app/models")
    available = []
    if models_dir.exists():
        for d in models_dir.iterdir():
            if d.is_dir():
                has_onnx = (d / "model.onnx").exists()
                has_hf = (d / "config.json").exists()
                has_labels = (d / "label_map.json").exists()
                available.append({
                    "id": d.name,
                    "path": str(d),
                    "has_onnx": has_onnx,
                    "has_transformers": has_hf,
                    "has_label_map": has_labels,
                })
    return {"models": available, "active_model_dir": MODEL_DIR}


# ── Training ────────────────────────────────────────────────────────────

import uuid
import subprocess
import multiprocessing
from collections import Counter

training_jobs: dict[str, dict] = {}


class TrainRequest(BaseModel):
    samples: list[dict]
    label_map: dict[str, dict]
    config: dict = {}


class ActivateRequest(BaseModel):
    model_dir: str


@app.post("/train")
async def train(request: TrainRequest):
    """Start a fine-tuning job in a subprocess. Returns immediately with a job ID."""
    logger.info("Train request received: %d samples, %d labels", len(request.samples), len(request.label_map))
    job_id = str(uuid.uuid4())[:8]
    training_jobs[job_id] = {"status": "TRAINING", "progress": 0, "started_at": time.time()}

    # Write training data to a temp file for the subprocess
    data_path = Path(f"/tmp/train_{job_id}.json")
    with open(data_path, "w") as f:
        json.dump({
            "samples": request.samples,
            "label_map": request.label_map,
            "config": request.config,
            "job_id": job_id,
        }, f)

    # Launch training as a separate process so it doesn't block uvicorn
    proc = subprocess.Popen(
        ["python3", "/app/train_worker.py", str(data_path)],
        stdout=None, stderr=None,  # let output go to container logs
    )

    # Monitor the process in a thread — polls result file for progress, then reads final result
    import threading
    def _monitor():
        result_path = Path(f"/tmp/train_{job_id}_result.json")

        # Poll for progress while process is running
        while proc.poll() is None:
            if result_path.exists():
                try:
                    with open(result_path) as f:
                        progress = json.load(f)
                    if progress.get("status") == "TRAINING":
                        training_jobs[job_id].update(progress)
                except (json.JSONDecodeError, IOError):
                    pass
            time.sleep(3)

        # Process exited — read final result
        if result_path.exists():
            try:
                with open(result_path) as f:
                    result = json.load(f)
                training_jobs[job_id] = result
                logger.info("Training job %s finished: %s", job_id, result.get("status"))
            except (json.JSONDecodeError, IOError) as e:
                training_jobs[job_id]["status"] = "FAILED"
                training_jobs[job_id]["error"] = f"Failed to read result file: {e}"
                training_jobs[job_id]["completed_at"] = time.time()
        else:
            training_jobs[job_id]["status"] = "FAILED"
            training_jobs[job_id]["error"] = f"Training process exited with code {proc.returncode} but no result file"
            training_jobs[job_id]["completed_at"] = time.time()
            logger.error("Training job %s failed: no result file, exit code %d", job_id, proc.returncode)
        # Cleanup
        data_path.unlink(missing_ok=True)
        result_path.unlink(missing_ok=True)

    threading.Thread(target=_monitor, daemon=True).start()

    return {"job_id": job_id, "status": "TRAINING"}


@app.get("/train/{job_id}/status")
async def train_status(job_id: str):
    """Check training job status."""
    job = training_jobs.get(job_id)
    if not job:
        raise HTTPException(status_code=404, detail="Job not found")
    return job


@app.post("/models/activate")
async def activate_model(request: ActivateRequest):
    """Hot-swap the active model to a new directory."""
    global tokenizer, onnx_session, hf_pipeline, label_map, MODEL_DIR

    model_path = Path(request.model_dir)
    if not model_path.exists():
        raise HTTPException(status_code=404, detail=f"Model directory not found: {request.model_dir}")

    lm_path = model_path / "label_map.json"
    if not lm_path.exists():
        raise HTTPException(status_code=400, detail="No label_map.json in model directory")

    with open(lm_path) as f:
        label_map = {int(k): v for k, v in json.load(f).items()}

    # Persist promoted model so it survives container restarts
    promoted_marker = Path("/app/models/.promoted")
    promoted_marker.write_text(str(model_path))

    onnx_session = None
    hf_pipeline = None

    from transformers import AutoTokenizer as SwapTokenizer
    tokenizer = SwapTokenizer.from_pretrained(str(model_path))

    onnx_path = model_path / "model.onnx"
    if USE_ONNX and onnx_path.exists():
        import onnxruntime as ort
        onnx_session = ort.InferenceSession(str(onnx_path), providers=["CPUExecutionProvider"])
        MODEL_DIR = str(model_path)
        logger.info("Hot-swapped to ONNX model at %s (%d labels)", model_path, len(label_map))
        return {"status": "activated", "mode": "onnx", "model_dir": str(model_path), "label_count": len(label_map)}

    hf_config = model_path / "config.json"
    if hf_config.exists():
        from transformers import pipeline as hf_pipe
        hf_pipeline = hf_pipe("text-classification", model=str(model_path), tokenizer=tokenizer)
        MODEL_DIR = str(model_path)
        logger.info("Hot-swapped to HuggingFace model at %s (%d labels)", model_path, len(label_map))
        return {"status": "activated", "mode": "transformers", "model_dir": str(model_path), "label_count": len(label_map)}

    raise HTTPException(status_code=400, detail="No model.onnx or config.json found in directory")
