"""Env-driven configuration for the trainer.

Defaults are chosen to match the dev `docker-compose.yml`. All values
are overridable via environment variables.
"""

from __future__ import annotations

import os
from dataclasses import dataclass


@dataclass(frozen=True)
class TrainerConfig:
    # Mongo
    mongo_uri: str
    mongo_db_name: str
    samples_collection: str = "bert_training_samples"

    # MinIO
    minio_endpoint: str = "http://localhost:9000"
    minio_access_key: str = "minioadmin"
    minio_secret_key: str = "minioadmin"
    minio_secure: bool = False
    artifacts_bucket: str = "gls-bert-artifacts"

    # Trainer
    top_n_categories: int = 3
    min_samples_per_class: int = 50
    base_model_id: str = "answerdotai/ModernBERT-base"
    epochs: int = 3
    batch_size: int = 16
    learning_rate: float = 2e-5
    max_seq_length: int = 512

    # Output
    trainer_version: str = "0.1.0"
    """Identifier for this trainer build — pinned in the artefact's MinIO
    key so multiple trainer versions can coexist."""


def load() -> TrainerConfig:
    """Build a :class:`TrainerConfig` from environment variables.

    Mongo URI is required; everything else has dev-friendly defaults.
    """
    return TrainerConfig(
        mongo_uri=_required("MONGO_URI"),
        mongo_db_name=os.environ.get("MONGO_DB_NAME", "governance_led_storage_main"),
        samples_collection=os.environ.get(
            "GLS_BERT_TRAINER_SAMPLES_COLLECTION", "bert_training_samples"
        ),
        minio_endpoint=os.environ.get("MINIO_ENDPOINT", "http://localhost:9000"),
        minio_access_key=os.environ.get("MINIO_ACCESS_KEY", "minioadmin"),
        minio_secret_key=os.environ.get("MINIO_SECRET_KEY", "minioadmin"),
        minio_secure=os.environ.get("MINIO_SECURE", "false").lower() == "true",
        artifacts_bucket=os.environ.get("GLS_BERT_ARTIFACTS_BUCKET", "gls-bert-artifacts"),
        top_n_categories=int(os.environ.get("GLS_BERT_TRAINER_TOP_N", "3")),
        min_samples_per_class=int(
            os.environ.get("GLS_BERT_TRAINER_MIN_SAMPLES_PER_CLASS", "50")
        ),
        base_model_id=os.environ.get(
            "GLS_BERT_TRAINER_BASE_MODEL", "answerdotai/ModernBERT-base"
        ),
        epochs=int(os.environ.get("GLS_BERT_TRAINER_EPOCHS", "3")),
        batch_size=int(os.environ.get("GLS_BERT_TRAINER_BATCH_SIZE", "16")),
        learning_rate=float(os.environ.get("GLS_BERT_TRAINER_LEARNING_RATE", "2e-5")),
        max_seq_length=int(os.environ.get("GLS_BERT_TRAINER_MAX_SEQ_LENGTH", "512")),
        trainer_version=os.environ.get("GLS_BERT_TRAINER_VERSION", "0.1.0"),
    )


def _required(key: str) -> str:
    val = os.environ.get(key)
    if not val:
        raise RuntimeError(
            f"required env var {key} is not set — see gls-bert-trainer/README.md"
        )
    return val
