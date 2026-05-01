---
title: contracts/classifier-router
lifecycle: forward
---

# `contracts/classifier-router/`

OpenAPI 3.1.1 spec for `igc-classifier-router` — the task-agnostic cascade router (Phase 1.2). One service, one contract, many tasks: classification, PII / PHI / PCI scans, metadata extraction. The block coordinates on the request decide what the cascade does; the cascade decides which tier (BERT → SLM → LLM) does it.

## Operations

| Method + path | Op id | Notes |
|---|---|---|
| `POST /v1/classify` | `classify` | Cascade dispatch. Phase 1.2 first cut returns a deterministic mock with `tierOfDecision=MOCK`. `Idempotency-Key` honoured (CSV #16). |
| `GET /v1/capabilities` | `getCapabilities` | Tier readiness. |
| `GET /actuator/health` | `getHealth` | Liveness + readiness. |

## Block-shape contract

The router doesn't constrain `result`'s shape — that belongs to the block. By block type:

- **PROMPT (classification)** — `{ category: string, sensitivity: string, confidence: number }`.
- **PROMPT (PII / PHI / PCI scan)** — `{ piiHits: [{ type, span, confidence }] }`.
- **PROMPT (metadata extraction)** — `{ extractedMetadata: { fieldA: ..., fieldB: ... } }`.
- **BERT_CLASSIFIER** — `{ label: string, confidence: number }`.

The block schema is the source of truth for what `result` carries; the router's job is to produce that shape, not enforce it.

## Cascade behaviour

Cascade order BERT → SLM → LLM, thresholds per ROUTER block (CSV #2 / architecture §8.1). Each tier reports a `CascadeStep` on the response's `cascadeTrace`; the first tier whose `confidence ≥ threshold` short-circuits and becomes `tierOfDecision`. If all tiers fail / fall through, the router returns the LLM tier's result (LLM is the floor). If the ROUTER block disables every tier, `tierOfDecision=ROUTER_SHORT_CIRCUIT` and the result is the block's configured default outcome.

The Phase 1.2 first cut hardcodes `tierOfDecision=MOCK` and returns a fixed deterministic result. The mock is shaped enough for orchestrator-side integration but doesn't actually call any model tier.

## Cross-references

- CSV #2 — BERT serving choice (cascade end-state).
- CSV #14 — block-version pinning.
- CSV #16 — idempotency.
- CSV #17 — error envelope.
- CSV #18 — service JWT.
- CSV #19 — TextPayload.
- CSV #21 — capabilities.
- Architecture §3 / §8.1 — cascade design.
- `version-2-implementation-plan.md` Phase 1.2 — sub-phase plan.

## Implementation status

**Contract + module skeleton + deterministic mock controller.** Real cascade lands as Phases 1.3–1.6 wire orchestrator integration + BERT inference + SLM worker + LLM worker rework.
