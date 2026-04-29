"""gls-bert-trainer — Phase 1.4 BERT trainer for the ig-central cascade.

Reads training samples from Mongo, fine-tunes ModernBERT on the org's
top categories, exports ONNX, publishes to MinIO. See README.md for
the full pipeline.
"""

__version__ = "0.1.0"
