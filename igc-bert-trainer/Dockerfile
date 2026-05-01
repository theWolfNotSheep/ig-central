# Phase 1.4 BERT trainer (Python, k8s Job per CSV #2).
#
# Builds a multi-arch image that pip-installs the trainer + its
# heavy ML deps (torch, transformers, optimum). The trainer is
# one-shot: it loads, trains, exports ONNX, uploads to MinIO, exits.
# Use as a Kubernetes Job or `docker run --rm` from a host with
# enough RAM / GPU for ModernBERT-base.

FROM python:3.12-slim AS build

WORKDIR /workspace

# Build deps for torch wheel resolution + sentencepiece tokeniser.
RUN apt-get update && apt-get install -y --no-install-recommends \
        build-essential \
        git \
    && rm -rf /var/lib/apt/lists/*

# Install only the trainer's package metadata + deps first so
# subsequent source changes don't bust the dep layer.
COPY igc-bert-trainer/pyproject.toml /workspace/
COPY igc-bert-trainer/src /workspace/src
RUN pip install --no-cache-dir --upgrade pip \
    && pip install --no-cache-dir .

FROM python:3.12-slim
RUN apt-get update && apt-get install -y --no-install-recommends \
        curl \
    && rm -rf /var/lib/apt/lists/*

# Bring the installed packages over from the build stage.
COPY --from=build /usr/local/lib/python3.12/site-packages /usr/local/lib/python3.12/site-packages
COPY --from=build /usr/local/bin/igc-bert-trainer /usr/local/bin/igc-bert-trainer

# No EXPOSE / HEALTHCHECK — this is a one-shot job, not a service.
ENTRYPOINT ["igc-bert-trainer"]
