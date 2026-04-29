"""Upload the trained ONNX artefact + metadata sidecar to MinIO.

Object key shape:

.. code-block:: text

    ${bucket}/${trainerVersion}/${modelVersion}/model.onnx
    ${bucket}/${trainerVersion}/${modelVersion}/metadata.json

The JVM ``gls-bert-inference`` service consumes ``model.onnx`` directly
and reads ``metadata.json`` for the label mapping + eval metrics so it
can populate its ``GET /v1/models`` response without having to derive
them from the ONNX file.
"""

from __future__ import annotations

import io
import json
import logging
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from minio import Minio
from minio.error import S3Error

logger = logging.getLogger(__name__)


@dataclass(frozen=True)
class PublishResult:
    bucket: str
    object_key: str
    metadata_object_key: str
    model_version: str


class ArtefactPublisher:
    def __init__(
        self,
        client: Minio,
        bucket: str,
        trainer_version: str,
    ) -> None:
        self._client = client
        self._bucket = bucket
        self._trainer_version = trainer_version

    def publish(
        self,
        onnx_path: Path,
        labels: list[str],
        eval_metrics: dict[str, Any],
        base_model_id: str,
        train_size: int,
        test_size: int,
        model_version: str | None = None,
    ) -> PublishResult:
        """Upload ``onnx_path`` + a metadata sidecar to MinIO.

        ``model_version`` defaults to a UTC timestamp if not provided —
        ``YYYY.MM.DD-HHMMSS`` so the inference service can sort by
        version without parsing.
        """
        self._ensure_bucket()

        if model_version is None:
            model_version = datetime.now(timezone.utc).strftime("%Y.%m.%d-%H%M%S")

        prefix = f"{self._trainer_version}/{model_version}"
        onnx_key = f"{prefix}/model.onnx"
        metadata_key = f"{prefix}/metadata.json"

        # Upload ONNX
        size = onnx_path.stat().st_size
        with onnx_path.open("rb") as fh:
            self._client.put_object(
                self._bucket,
                onnx_key,
                fh,
                length=size,
                content_type="application/octet-stream",
            )

        # Upload metadata sidecar
        metadata = build_metadata(
            labels=labels,
            eval_metrics=eval_metrics,
            base_model_id=base_model_id,
            trainer_version=self._trainer_version,
            model_version=model_version,
            train_size=train_size,
            test_size=test_size,
        )
        metadata_bytes = json.dumps(metadata, indent=2).encode("utf-8")
        self._client.put_object(
            self._bucket,
            metadata_key,
            io.BytesIO(metadata_bytes),
            length=len(metadata_bytes),
            content_type="application/json",
        )

        logger.info(
            "published artefact: bucket=%s onnx=%s metadata=%s size=%d",
            self._bucket,
            onnx_key,
            metadata_key,
            size,
        )
        return PublishResult(
            bucket=self._bucket,
            object_key=onnx_key,
            metadata_object_key=metadata_key,
            model_version=model_version,
        )

    def _ensure_bucket(self) -> None:
        try:
            if not self._client.bucket_exists(self._bucket):
                self._client.make_bucket(self._bucket)
                logger.info("created MinIO bucket %s", self._bucket)
        except S3Error as e:  # pragma: no cover — depends on MinIO state
            logger.warning(
                "could not verify / create bucket %s: %s — assuming it exists",
                self._bucket,
                e,
            )


def build_metadata(
    labels: list[str],
    eval_metrics: dict[str, Any],
    base_model_id: str,
    trainer_version: str,
    model_version: str,
    train_size: int,
    test_size: int,
) -> dict[str, Any]:
    """Build the sidecar JSON the inference service reads on load.

    Shape mirrors the BERT_CLASSIFIER block content schema's
    ``trainingMetadata`` field (per ``contracts/blocks/bert-classifier.schema.json``)
    so the admin UI's "create block from artefact" path can copy fields
    one-to-one without any reshaping.
    """
    return {
        "trainerVersion": trainer_version,
        "modelVersion": model_version,
        "baseModelId": base_model_id,
        "trainedAt": datetime.now(timezone.utc).isoformat(),
        "datasetSize": train_size + test_size,
        "trainSize": train_size,
        "testSize": test_size,
        "labels": labels,
        "evaluationMetrics": eval_metrics,
    }
