# Changelog — `_shared/`

Format: [Keep a Changelog](https://keepachangelog.com/en/1.1.0/). Versions bumped per `CLAUDE.md` API Contracts rule.

## [Unreleased]

## [0.2.0] — 2026-04-26

### Added

- `error-envelope.yaml` — RFC 7807 + ig-central extensions; `ProblemDetails`, `ProblemTraceStep`, and the `Error` response (CSV #17).
- `security-schemes.yaml` — `serviceJwt` bearer scheme; required claims documented (CSV #18).
- `common-headers.yaml` — `Traceparent`, `IdempotencyKey`, `XRequestId`, `Prefer` parameters (CSV #13/#16/#20).
- `capabilities.yaml` — `Capabilities` and `CapabilityModel` schemas for `GET /v1/capabilities` (CSV #21).
- `text-payload.yaml` — `TextPayload` `oneOf` of `TextInline` (≤ 256 KB) and `TextReference` (pre-signed URL) (CSV #19).
- `pagination.yaml` — cursor-based `Page` envelope; `PageCursor` and `PageSize` parameters.
- `idempotency.yaml` — `InFlight` 409 response for in-progress idempotency keys (CSV #16).
- `retry.yaml` — `RetryAfter` header and `TooManyRequests` 429 response (CSV #23).

## [0.1.0] — 2026-04-26

### Added

- Folder scaffolding for cross-cutting schemas (Phase 0.3). Content YAMLs land in 0.4.
