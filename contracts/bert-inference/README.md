---
title: contracts/bert-inference
lifecycle: forward
---

# `contracts/bert-inference/`

OpenAPI 3.1.1 spec for `igc-bert-inference` — Phase 1.4. JVM + DJL + ONNX Runtime per CSV #2 hybrid (Python trains, JVM serves).

## Operations

| Method + path | Op id | Notes |
|---|---|---|
| `POST /v1/infer` | `infer` | Synchronous BERT classification. Returns `503 MODEL_NOT_LOADED` until an artefact is loaded; cascade router falls through to the next tier. |
| `GET /v1/models` | `listModels` | Loaded models with version + labels + block ids. |
| `POST /v1/models/reload` | `reloadModels` | Ops trigger; re-fetches from MinIO. 202 + Location-style status. |
| `GET /v1/capabilities` | `getCapabilities` | Per CSV #21. |
| `GET /actuator/health` | `getHealth` | Readiness flips UP only when ≥ 1 model is loaded. |

## Phase 1.4 sequencing

1. **PR1 (this contract)** — bert-inference module skeleton + DJL + ONNX Runtime classpath + stub backend that returns `MODEL_NOT_LOADED`.
2. **PR2** — `igc-bert-trainer` (Python) — fine-tune ModernBERT, export ONNX to MinIO. Probably published as a separate repo / Python package; only the Dockerfile + scheduler trigger live here.
3. **PR3** — Wire bert-inference into the `igc-classifier-router` cascade as the BERT tier behind the existing ROUTER block. Conservative `bertAccept=0.92` defaults; per-category rollout.
4. **PR4** — Real ONNX load from MinIO; hot reload via `/v1/models/reload`. Lands once the trainer publishes its first artefact.

## Cross-references

- CSV #2 — BERT serving choice (Python trains, JVM serves).
- CSV #14 — block-version pinning.
- CSV #17 — error envelope.
- CSV #19 — TextPayload.
- CSV #21 — capabilities.
- Architecture §3 / §8.1 / §8.2 — cascade design + BERT serving rationale.
- `version-2-implementation-plan.md` Phase 1.4 — sub-phase plan.

## No idempotency

Inference is stateless and cheap relative to the LLM tier. The cascade router's idempotency layer (CSV #16) covers replay correlation upstream — the same `nodeRunId` going through the cascade twice gets the same router-side cached response, never reaching this service. Sparing the per-request Mongo round-trip + TTL bookkeeping is worth the simplicity.
