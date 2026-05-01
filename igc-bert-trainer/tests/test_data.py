from unittest.mock import MagicMock

import pytest

from igc_bert_trainer.data import (
    LoadedDataset,
    TrainingDataLoader,
    TrainingSample,
    has_enough_samples,
    split_train_test,
)


def _make_loader(client, *, top_n=3, min_per_class=50):
    return TrainingDataLoader(
        client=client,
        db_name="testdb",
        collection_name="bert_training_samples",
        top_n_categories=top_n,
        min_samples_per_class=min_per_class,
    )


def test_load_returns_top_n_categories_meeting_min_per_class():
    coll = MagicMock()
    coll.aggregate.return_value = iter(
        [
            {"_id": "cat-hr", "categoryName": "HR Letter", "count": 120},
            {"_id": "cat-finance", "categoryName": "Finance", "count": 80},
            {"_id": "cat-ops", "categoryName": "Ops", "count": 60},
        ]
    )
    coll.find.return_value = iter(
        [
            {"documentText": "doc1", "categoryId": "cat-hr"},
            {"documentText": "doc2", "categoryId": "cat-finance"},
        ]
    )
    client = MagicMock()
    client.__getitem__.return_value.__getitem__.return_value = coll

    loader = _make_loader(client, top_n=3, min_per_class=50)
    dataset = loader.load()

    assert [label_id for label_id, _ in dataset.label_mapping] == [
        "cat-hr",
        "cat-finance",
        "cat-ops",
    ]
    assert [s.label for s in dataset.samples] == ["cat-hr", "cat-finance"]
    # Aggregation pipeline was called with the expected min-per-class gate.
    pipeline = coll.aggregate.call_args.args[0]
    match_step = next(step for step in pipeline if "$match" in step)
    assert match_step["$match"]["count"]["$gte"] == 50
    limit_step = next(step for step in pipeline if "$limit" in step)
    assert limit_step["$limit"] == 3


def test_load_returns_empty_dataset_when_no_categories_meet_gate():
    coll = MagicMock()
    coll.aggregate.return_value = iter([])
    client = MagicMock()
    client.__getitem__.return_value.__getitem__.return_value = coll

    dataset = _make_loader(client).load()
    assert dataset.samples == []
    assert dataset.label_mapping == []
    coll.find.assert_not_called()


def test_load_skips_rows_with_blank_text_or_label():
    coll = MagicMock()
    coll.aggregate.return_value = iter(
        [{"_id": "cat-hr", "categoryName": "HR", "count": 50}]
    )
    coll.find.return_value = iter(
        [
            {"documentText": "doc1", "categoryId": "cat-hr"},
            {"documentText": "", "categoryId": "cat-hr"},
            {"documentText": "doc2", "categoryId": ""},
            {"documentText": None, "categoryId": "cat-hr"},
        ]
    )
    client = MagicMock()
    client.__getitem__.return_value.__getitem__.return_value = coll

    dataset = _make_loader(client).load()
    assert [s.text for s in dataset.samples] == ["doc1"]


def test_has_enough_samples_passes_when_all_labels_meet_gate():
    dataset = LoadedDataset(
        samples=[TrainingSample("a", "x")] * 50 + [TrainingSample("b", "y")] * 50,
        label_mapping=[("x", "X"), ("y", "Y")],
    )
    assert has_enough_samples(dataset, min_per_class=50) is True


def test_has_enough_samples_fails_when_one_label_short():
    dataset = LoadedDataset(
        samples=[TrainingSample("a", "x")] * 50 + [TrainingSample("b", "y")] * 49,
        label_mapping=[("x", "X"), ("y", "Y")],
    )
    assert has_enough_samples(dataset, min_per_class=50) is False


def test_has_enough_samples_fails_on_empty():
    assert (
        has_enough_samples(LoadedDataset(samples=[], label_mapping=[]), min_per_class=1)
        is False
    )


def test_split_train_test_is_deterministic():
    samples = [TrainingSample(f"text-{i}", "x") for i in range(20)]
    train1, test1 = split_train_test(samples, test_ratio=0.1)
    train2, test2 = split_train_test(samples, test_ratio=0.1)
    assert [s.text for s in train1] == [s.text for s in train2]
    assert [s.text for s in test1] == [s.text for s in test2]
    # 1-in-10 ratio → 2 test samples for 20 inputs.
    assert len(test1) == 2
    assert len(train1) == 18


def test_split_train_test_rejects_invalid_ratio():
    with pytest.raises(ValueError):
        split_train_test([], test_ratio=0.0)
    with pytest.raises(ValueError):
        split_train_test([], test_ratio=1.0)
