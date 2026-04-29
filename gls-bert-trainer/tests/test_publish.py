import io
import json
from pathlib import Path
from unittest.mock import MagicMock

from gls_bert_trainer.publish import ArtefactPublisher, build_metadata


def _client(*, bucket_exists=True):
    client = MagicMock()
    client.bucket_exists.return_value = bucket_exists
    return client


def test_publish_uploads_onnx_and_metadata_under_versioned_prefix(tmp_path: Path):
    onnx_file = tmp_path / "model.onnx"
    onnx_file.write_bytes(b"\x00ONNX\x00DUMMY")

    client = _client(bucket_exists=True)
    publisher = ArtefactPublisher(
        client=client, bucket="gls-bert-artifacts", trainer_version="0.1.0"
    )

    result = publisher.publish(
        onnx_path=onnx_file,
        labels=["cat-hr", "cat-finance"],
        eval_metrics={"accuracy": 0.91},
        base_model_id="answerdotai/ModernBERT-base",
        train_size=180,
        test_size=20,
        model_version="2026.04.0",
    )

    assert result.bucket == "gls-bert-artifacts"
    assert result.object_key == "0.1.0/2026.04.0/model.onnx"
    assert result.metadata_object_key == "0.1.0/2026.04.0/metadata.json"
    assert result.model_version == "2026.04.0"

    # Two put_object calls — one for ONNX, one for the metadata sidecar.
    assert client.put_object.call_count == 2
    onnx_call, meta_call = client.put_object.call_args_list

    assert onnx_call.args[0] == "gls-bert-artifacts"
    assert onnx_call.args[1] == "0.1.0/2026.04.0/model.onnx"
    assert onnx_call.kwargs["length"] == len(b"\x00ONNX\x00DUMMY")
    assert onnx_call.kwargs["content_type"] == "application/octet-stream"

    assert meta_call.args[1] == "0.1.0/2026.04.0/metadata.json"
    assert meta_call.kwargs["content_type"] == "application/json"
    metadata_buf: io.BytesIO = meta_call.args[2]
    metadata = json.loads(metadata_buf.getvalue().decode("utf-8"))
    assert metadata["modelVersion"] == "2026.04.0"
    assert metadata["labels"] == ["cat-hr", "cat-finance"]
    assert metadata["trainSize"] == 180
    assert metadata["testSize"] == 20
    assert metadata["datasetSize"] == 200


def test_publish_creates_bucket_when_absent(tmp_path: Path):
    onnx_file = tmp_path / "model.onnx"
    onnx_file.write_bytes(b"x")

    client = _client(bucket_exists=False)
    publisher = ArtefactPublisher(
        client=client, bucket="brand-new", trainer_version="0.1.0"
    )

    publisher.publish(
        onnx_path=onnx_file,
        labels=["cat-hr"],
        eval_metrics={},
        base_model_id="m",
        train_size=10,
        test_size=2,
        model_version="2026.04.0",
    )
    client.make_bucket.assert_called_once_with("brand-new")


def test_publish_default_model_version_uses_utc_timestamp(tmp_path: Path):
    onnx_file = tmp_path / "model.onnx"
    onnx_file.write_bytes(b"x")

    client = _client()
    publisher = ArtefactPublisher(
        client=client, bucket="b", trainer_version="0.1.0"
    )

    result = publisher.publish(
        onnx_path=onnx_file,
        labels=["cat-hr"],
        eval_metrics={},
        base_model_id="m",
        train_size=1,
        test_size=1,
    )
    # Format: YYYY.MM.DD-HHMMSS — 17 chars (10 for date + dash + 6 for time)
    assert len(result.model_version) == 17
    assert result.model_version[4] == "."
    assert result.model_version[7] == "."
    assert result.model_version[10] == "-"


def test_build_metadata_shape_matches_block_schema_field_names():
    metadata = build_metadata(
        labels=["x"],
        eval_metrics={"accuracy": 0.5, "f1_macro": 0.4},
        base_model_id="answerdotai/ModernBERT-base",
        trainer_version="0.1.0",
        model_version="2026.04.0",
        train_size=100,
        test_size=10,
    )
    # These keys are the same shape as
    # contracts/blocks/bert-classifier.schema.json's `trainingMetadata`
    # so the admin UI can copy them to a BERT_CLASSIFIER block content
    # without reshaping.
    expected_keys = {
        "trainerVersion",
        "modelVersion",
        "baseModelId",
        "trainedAt",
        "datasetSize",
        "trainSize",
        "testSize",
        "labels",
        "evaluationMetrics",
    }
    assert set(metadata.keys()) == expected_keys
