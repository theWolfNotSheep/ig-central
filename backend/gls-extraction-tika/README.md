---
title: gls-extraction-tika
lifecycle: forward
---

# gls-extraction-tika

Phase 0.5 reference service. Synchronous text extraction via Apache Tika; first per-service split in the v2 cutover, and the cloneable pattern future services copy.

## Status

**Phase 0.5.1 — module + contract only.** Implementation, generated stubs, Dockerfile, audit/trace/JWT wiring, and tests land in 0.5.2+.

What exists today:

- `pom.xml` declaring the module against the parent reactor.
- `contracts/extraction/openapi.yaml` (in the `contracts/` tree, not here) — the source of truth.

What's deferred to 0.5.2+:

- Generated server stub via `openapi-generator-maven-plugin`.
- Tika integration (port from `gls-document-processing`).
- MinIO source/target for `documentRef` / `textRef`.
- `nodeRunId` idempotency with 24h TTL (CSV #16).
- Audit emission (`EXTRACTION_COMPLETED` / `EXTRACTION_FAILED`, Tier 2).
- `traceparent` propagation; spans for `tika.parse`, `minio.fetch`.
- Spring Boot Actuator readiness probe (Tika init + MinIO reach).
- RFC 7807 errors with `EXTRACTION_OOM` / `EXTRACTION_CORRUPT` / `EXTRACTION_TIMEOUT` codes.
- Micrometer counters + latency histogram.
- JWT validation middleware (CSV #18).
- Dockerfile + compose service definition (the placeholder block in `docker-compose.yml` activates here).

## Cross-references

- `contracts/extraction/openapi.yaml` — the contract.
- `version-2-implementation-plan.md` Phase 0.5 — sub-phase plan.
- `docs/service-template.md` (lands with 0.5.6) — generic version of this pattern, for cloning into the next service.
