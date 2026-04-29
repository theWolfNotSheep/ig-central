---
title: gls-extraction-archive
lifecycle: forward
---

# gls-extraction-archive

Phase 1.1 service. Synchronous archive unpack — `.zip`, `.mbox`, `.pst`. Streams bytes from MinIO, walks the archive **one level deep**, lands each direct child as a fresh MinIO object, and returns the list of child `documentRef`s. The caller (`gls-app-assembly` / orchestrator) commits child `DocumentModel` rows and publishes the per-child `gls.documents.ingested` events. Recursion through nested archives happens via the normal pipeline routing — a `.zip` containing `.mbox` re-routes the `.mbox` back through this service on its own request, with its own `nodeRunId`. Decision: CSV #43.

Cloned from the `gls-extraction-tika` reference pattern (Phase 0.5) and `docs/service-template.md`.

## Status

**Phase 1.1, first PR — module + contract only.** Implementation, generated stubs, Dockerfile, audit / trace / JWT wiring, and tests land in subsequent PRs that mirror Tika's 0.5.2–0.5.6 sequence.

What exists today:

- `pom.xml` declaring the module against the parent reactor.
- `contracts/extraction-archive/openapi.yaml` (in the `contracts/` tree, not here) — the source of truth.

What's deferred to follow-up PRs:

- Generated server stub via `openapi-generator-maven-plugin`.
- Per-format walker dispatch: ZIP via Commons Compress, MBOX via Tika's `MboxParser`, PST via `java-libpst` (or equivalent — added with the PST walker).
- MinIO source for the source archive, MinIO sink for emitted children.
- `nodeRunId` idempotency with 24h TTL (CSV #16).
- Audit emission (`EXTRACTION_COMPLETED` / `EXTRACTION_FAILED`, Tier 2).
- `traceparent` propagation; spans for `archive.walk`, `minio.fetch`, `minio.put`.
- Spring Boot Actuator readiness probe (parser init + MinIO reach).
- RFC 7807 errors with `ARCHIVE_TOO_LARGE` / `ARCHIVE_TOO_MANY_CHILDREN` / `ARCHIVE_DEPTH_EXCEEDED` / `ARCHIVE_CORRUPT` codes (the 413 / 422 codes on the contract).
- Micrometer counters + latency histogram.
- JWT validation middleware (CSV #18).
- Dockerfile + compose service definition (placeholder block in `docker-compose.yml` activates here).

## Cross-references

- `contracts/extraction-archive/openapi.yaml` — the contract.
- `version-2-decision-tree.csv` #43 — fan-out responsibility + recursion depth.
- `version-2-implementation-plan.md` Phase 1.1 — sub-phase plan.
- `docs/service-template.md` — generic v2 service pattern; this is the second instance.
- `backend/gls-extraction-tika/` — reference implementation; clone source for the impl PRs.
