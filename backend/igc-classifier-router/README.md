---
title: igc-classifier-router
lifecycle: forward
---

# igc-classifier-router

Phase 1.2 cascade router. Task-agnostic dispatch for PROMPT and BERT_CLASSIFIER blocks. Contract-first per `contracts/classifier-router/openapi.yaml`.

## Status

**Phase 1.2 first cut — deterministic mock cascade.**

What ships now:

- Contract surface (`POST /v1/classify`, `GET /v1/capabilities`, `GET /actuator/health`).
- Module skeleton (source layout cloned from the extraction services).
- `CascadeService` interface + `MockCascadeService` impl returning a fixed `tierOfDecision=MOCK` result so the orchestrator + admin UI can integrate.
- Idempotency on `nodeRunId` (CSV #16) backed by the `router_idempotency` Mongo TTL collection.
- Tier 2 audit (`CLASSIFY_COMPLETED` / `CLASSIFY_FAILED`).
- RFC 7807 error mapping with `code` extensions: `ROUTER_BLOCK_NOT_FOUND`, `IDEMPOTENCY_IN_FLIGHT`, `ROUTER_DEPENDENCY_UNAVAILABLE`.
- Dockerfile + Compose entry.

What's deferred behind the same `CascadeService` interface:

- **Phase 1.3** — orchestrator cutover via feature flag.
- **Phase 1.4** — `igc-bert-inference` first tier.
- **Phase 1.5** — `igc-slm-worker` middle tier.
- **Phase 1.6** — `igc-llm-worker` rework.

JWT and integration tests blocked family-wide on JWKS infra + issue #7.

## Cross-references

- `contracts/classifier-router/openapi.yaml` — the contract.
- Architecture §3 / §8.1 — cascade design.
- CSV #2, #14, #16 — load-bearing decisions.
- `version-2-implementation-plan.md` Phase 1.2 — sub-phase plan.
