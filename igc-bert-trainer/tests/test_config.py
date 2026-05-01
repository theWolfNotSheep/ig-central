import os
from unittest.mock import patch

import pytest

from igc_bert_trainer import config as cfg


def test_load_with_minimal_env(monkeypatch):
    monkeypatch.setenv("MONGO_URI", "mongodb://localhost:27017/db")
    config = cfg.load()
    assert config.mongo_uri == "mongodb://localhost:27017/db"
    assert config.mongo_db_name == "governance_led_storage_main"
    assert config.samples_collection == "bert_training_samples"
    assert config.artifacts_bucket == "igc-bert-artifacts"
    assert config.top_n_categories == 3
    assert config.min_samples_per_class == 50
    assert config.base_model_id == "answerdotai/ModernBERT-base"
    assert config.epochs == 3


def test_load_overrides_via_env(monkeypatch):
    monkeypatch.setenv("MONGO_URI", "mongodb://h:27017/d")
    monkeypatch.setenv("IGC_BERT_TRAINER_TOP_N", "5")
    monkeypatch.setenv("IGC_BERT_TRAINER_MIN_SAMPLES_PER_CLASS", "100")
    monkeypatch.setenv("IGC_BERT_TRAINER_BASE_MODEL", "custom/model")
    monkeypatch.setenv("IGC_BERT_TRAINER_EPOCHS", "5")
    monkeypatch.setenv("MINIO_SECURE", "true")
    config = cfg.load()
    assert config.top_n_categories == 5
    assert config.min_samples_per_class == 100
    assert config.base_model_id == "custom/model"
    assert config.epochs == 5
    assert config.minio_secure is True


def test_load_raises_when_mongo_uri_missing(monkeypatch):
    monkeypatch.delenv("MONGO_URI", raising=False)
    with pytest.raises(RuntimeError, match="MONGO_URI"):
        cfg.load()
