---
title: gls-extraction-archive
lifecycle: forward
---

# gls-extraction-archive

Phase 1.1 service. Synchronous archive unpack — `.zip`, `.mbox`, `.pst`. Streams bytes from MinIO, walks the archive **one level deep**, lands each direct child as a fresh MinIO object, and returns the list of child `documentRef`s. The caller (`gls-app-assembly` / orchestrator) commits child `DocumentModel` rows and publishes the per-child `gls.documents.ingested` events. Recursion through nested archives happens via the normal pipeline routing — a `.zip` containing `.mbox` re-routes the `.mbox` back through this service on its own request, with its own `nodeRunId`. Decision: CSV #43.

Cloned from the `gls-extraction-tika` reference pattern (Phase 0.5) and `docs/service-template.md`.

## Status

**Phase 1.1 — substantively complete.** ZIP / MBOX / PST walkers wired; idempotency, audit, health, metrics, exception handler, controller all live. Dockerfile + Compose entry shipped. Unit tests at 37; integration tests blocked on issue #7.

Outstanding:

- **JWT** — blocked on JWKS infra (same as Tika).
- **PST attachments** — `PstArchiveWalker` emits one `.eml` per message with headers + body only; attachments are dropped on the floor for now. Follow-up PR adds attachment children.
- **Integration tests** — `@ServiceConnection` + Testcontainers PST fixture once issue #7 is unblocked.

## Supported archive types

| Type | Walker | Library | Notes |
|---|---|---|---|
| `.zip` | `ZipArchiveWalker` | Apache Commons Compress (transitive via `tika-parsers-standard-package`) | Streaming reader, single pass; encrypted entries → `ARCHIVE_CORRUPT`. |
| `.mbox` | `MboxArchiveWalker` | Hand-rolled RFC 4155 splitter | One `.eml` per `From `-prefixed message; tolerates leading garbage. |
| `.pst` | `PstArchiveWalker` | `com.pff:java-libpst` (CSV #44) | Materialises to temp file (libpst requires `RandomAccessFile`); each PSTMessage emitted as basic synthesised `.eml`. |

## Cross-references

- `contracts/extraction-archive/openapi.yaml` — the contract.
- `version-2-decision-tree.csv` #43 — fan-out responsibility + recursion depth.
- `version-2-implementation-plan.md` Phase 1.1 — sub-phase plan.
- `docs/service-template.md` — generic v2 service pattern; this is the second instance.
- `backend/gls-extraction-tika/` — reference implementation; clone source for the impl PRs.
