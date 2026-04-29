"""Training-sample loader.

Reads from the ``bert_training_samples`` Mongo collection populated by
``BertTrainingDataCollector`` in the orchestrator. Each row is shaped:

.. code-block:: json

    {
        "_id": "...",
        "categoryId": "cat-hr-letter",
        "categoryName": "HR Letter",
        "documentText": "Dear Alice, ...",
        "confidence": 0.94,
        "humanCorrected": false,
        "createdAt": "2026-04-29T10:00:00Z"
    }

The loader picks the ``top_n`` categories by sample count and returns
all samples for those categories — ignoring rows for less-frequent
categories so the resulting model isn't crippled by long-tail noise.
"""

from __future__ import annotations

import logging
from dataclasses import dataclass
from typing import Iterable, Sequence

from pymongo import MongoClient
from pymongo.collection import Collection

logger = logging.getLogger(__name__)


@dataclass(frozen=True)
class TrainingSample:
    text: str
    label: str  # categoryId — the trainer's label space


@dataclass(frozen=True)
class LoadedDataset:
    samples: list[TrainingSample]
    label_mapping: list[tuple[str, str]]
    """Ordered ``(categoryId, categoryName)`` pairs. Index in this list
    corresponds to the model's softmax-output index — pin the order in
    the BERT_CLASSIFIER block's ``labelMapping``."""


class TrainingDataLoader:
    def __init__(
        self,
        client: MongoClient,
        db_name: str,
        collection_name: str,
        top_n_categories: int,
        min_samples_per_class: int,
    ) -> None:
        self._client = client
        self._db_name = db_name
        self._collection_name = collection_name
        self._top_n = top_n_categories
        self._min_per_class = min_samples_per_class

    def _collection(self) -> Collection:
        return self._client[self._db_name][self._collection_name]

    def load(self) -> LoadedDataset:
        coll = self._collection()
        top = self._top_categories(coll)
        if not top:
            logger.warning(
                "no training samples found in %s.%s — skipping",
                self._db_name,
                self._collection_name,
            )
            return LoadedDataset(samples=[], label_mapping=[])

        category_ids = [cat_id for cat_id, _, _ in top]
        cursor = coll.find(
            {"categoryId": {"$in": category_ids}},
            projection={"documentText": 1, "categoryId": 1, "_id": 0},
        )
        samples = [
            TrainingSample(
                text=str(row.get("documentText") or ""),
                label=str(row.get("categoryId") or ""),
            )
            for row in cursor
            if row.get("documentText") and row.get("categoryId")
        ]
        label_mapping = [(cat_id, cat_name) for cat_id, cat_name, _ in top]

        logger.info(
            "loaded %d samples across %d categories: %s",
            len(samples),
            len(label_mapping),
            ", ".join(f"{cat_id}({count})" for cat_id, _, count in top),
        )
        return LoadedDataset(samples=samples, label_mapping=label_mapping)

    def _top_categories(
        self, coll: Collection
    ) -> Sequence[tuple[str, str, int]]:
        """Aggregate samples by categoryId, return the top N that meet
        the minimum-samples gate. Returns ``(categoryId, categoryName, count)``
        tuples."""
        pipeline: list[dict] = [
            {
                "$group": {
                    "_id": "$categoryId",
                    "categoryName": {"$first": "$categoryName"},
                    "count": {"$sum": 1},
                }
            },
            {"$match": {"count": {"$gte": self._min_per_class}}},
            {"$sort": {"count": -1}},
            {"$limit": self._top_n},
        ]
        return [
            (str(row["_id"]), str(row.get("categoryName") or row["_id"]), int(row["count"]))
            for row in coll.aggregate(pipeline)
        ]


def has_enough_samples(dataset: LoadedDataset, min_per_class: int) -> bool:
    """Whether ``dataset`` clears the per-class minimum.

    Returns False if any chosen category falls below the minimum. The
    aggregation step in :meth:`TrainingDataLoader._top_categories` already
    filters to categories with at least ``min_per_class``, but we double-
    check here so a future change to the load shape doesn't silently
    erode the gate.
    """
    if not dataset.samples or not dataset.label_mapping:
        return False
    counts: dict[str, int] = {label: 0 for label, _ in dataset.label_mapping}
    for sample in dataset.samples:
        if sample.label in counts:
            counts[sample.label] += 1
    return all(count >= min_per_class for count in counts.values())


def split_train_test(
    samples: Iterable[TrainingSample], test_ratio: float = 0.1
) -> tuple[list[TrainingSample], list[TrainingSample]]:
    """Deterministic split — sorts by ``(label, text)`` then takes every
    ``ceil(1/test_ratio)``-th sample as test. Stable across runs so
    evaluation metrics are comparable between trainer versions."""
    if not 0 < test_ratio < 1:
        raise ValueError("test_ratio must be between 0 and 1 exclusive")
    sorted_samples = sorted(samples, key=lambda s: (s.label, s.text))
    step = max(2, round(1 / test_ratio))
    train: list[TrainingSample] = []
    test: list[TrainingSample] = []
    for idx, sample in enumerate(sorted_samples):
        if idx % step == step - 1:
            test.append(sample)
        else:
            train.append(sample)
    return train, test
