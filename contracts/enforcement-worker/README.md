---
title: enforcement-worker
lifecycle: forward
---

# `igc-enforcement-worker` contract

The post-classification enforcement stage of the pipeline (architecture
§3 / §11.A). Today this logic lives inside the monolith via the
`igc-governance-enforcement` Spring Boot module dependency on
`igc-app-assembly`; Phase 1.10 splits it out into a standalone
deployable so the orchestrator calls it over HTTP rather than
invoking the in-process `EnforcementService`.

## Surface

- `POST /v1/enforce` — apply governance rules to a classified
  document. Sync (returns 200 with the enforcement summary) or async
  (`Prefer: respond-async` → 202 + `Location: /v1/jobs/{nodeRunId}`).
  `nodeRunId`-keyed idempotency for 24h.
- `GET /v1/jobs/{nodeRunId}` — poll the async enforcement job.
- `GET /v1/capabilities`, `GET /actuator/health`.

## What enforcement does

Given a classified document (canonical signal:
`DocumentClassifiedEvent`), the worker:

- copies classification onto the document (categoryId,
  categoryName, sensitivity, applied policies);
- computes the retention schedule + expected disposition action,
  denormalising into the document for offline audit;
- migrates the storage tier when the resolved policy demands it
  (e.g. `RESTRICTED` → cold WORM bucket);
- writes a Tier 2 `enforcement.applied` audit event.

## Dependencies

- Mongo (read pipeline blocks + governance policies; write document
  + audit).
- Object storage (storage-tier migration writes to the destination
  bucket, deletes from source).
- The seeded POLICY block for the document's category, where
  populated. PR3 (Phase 1.9) plumbs `policyGovernancePolicyIds`
  through the engine; the enforcement worker reads them as part of
  the `applicablePolicyIds` it acts on.

## Versioning

Per the API Contracts rule in `CLAUDE.md`. `VERSION` bumps on every
spec change; major bumps for breaking changes; `CHANGELOG.md` records
each version.
