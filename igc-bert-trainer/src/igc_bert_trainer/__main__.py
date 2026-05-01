"""CLI entry point for the trainer.

Run as ``igc-bert-trainer`` (from the installed package) or
``python -m igc_bert_trainer`` (from a checkout).

Pipeline:
1. Load config from env (`config.py`).
2. Connect to Mongo, load training samples (`data.py`).
3. Bail out if there aren't enough samples per class.
4. Fine-tune ModernBERT, export ONNX (`train.py`).
5. Publish the artefact + metadata sidecar to MinIO (`publish.py`).
"""

from __future__ import annotations

import logging
import sys
import tempfile
from pathlib import Path

from minio import Minio
from pymongo import MongoClient
from urllib.parse import urlparse

from . import config as cfg
from .data import TrainingDataLoader, has_enough_samples
from .publish import ArtefactPublisher

logger = logging.getLogger("igc_bert_trainer")


def main(argv: list[str] | None = None) -> int:
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s %(levelname)s %(name)s %(message)s",
    )
    config = cfg.load()
    logger.info(
        "trainer starting: trainerVersion=%s baseModel=%s topN=%d minPerClass=%d",
        config.trainer_version,
        config.base_model_id,
        config.top_n_categories,
        config.min_samples_per_class,
    )

    client = MongoClient(config.mongo_uri)
    loader = TrainingDataLoader(
        client=client,
        db_name=config.mongo_db_name,
        collection_name=config.samples_collection,
        top_n_categories=config.top_n_categories,
        min_samples_per_class=config.min_samples_per_class,
    )
    dataset = loader.load()
    if not has_enough_samples(dataset, config.min_samples_per_class):
        logger.warning(
            "skipping training run — fewer than %d samples per class. Wait for "
            "more classifications before re-running.",
            config.min_samples_per_class,
        )
        return 0

    # Lazy import — fine_tune_and_export pulls in torch / transformers /
    # optimum, which we don't want to require for an early-exit no-data run.
    from .train import fine_tune_and_export

    with tempfile.TemporaryDirectory(prefix="igc-bert-trainer-") as tmp:
        result = fine_tune_and_export(
            dataset=dataset,
            base_model_id=config.base_model_id,
            epochs=config.epochs,
            batch_size=config.batch_size,
            learning_rate=config.learning_rate,
            max_seq_length=config.max_seq_length,
            output_dir=Path(tmp),
        )

        publisher = ArtefactPublisher(
            client=_minio_client(config),
            bucket=config.artifacts_bucket,
            trainer_version=config.trainer_version,
        )
        publish_result = publisher.publish(
            onnx_path=result.onnx_path,
            labels=result.labels,
            eval_metrics=result.eval_metrics,
            base_model_id=result.base_model_id,
            train_size=result.train_size,
            test_size=result.test_size,
        )

    logger.info(
        "trainer complete: modelVersion=%s s3://%s/%s",
        publish_result.model_version,
        publish_result.bucket,
        publish_result.object_key,
    )
    return 0


def _minio_client(config: cfg.TrainerConfig) -> Minio:
    parsed = urlparse(config.minio_endpoint)
    host = parsed.netloc or parsed.path
    return Minio(
        host,
        access_key=config.minio_access_key,
        secret_key=config.minio_secret_key,
        secure=config.minio_secure or parsed.scheme == "https",
    )


if __name__ == "__main__":  # pragma: no cover
    sys.exit(main())
