---
title: Shared schemas
lifecycle: forward
---

# Shared schemas

Cross-cutting OpenAPI components and patterns referenced by every service's spec via `$ref`.

## Why centralise

If five services each hand-write their own error response, they will drift. Defining the error envelope once and `$ref`-ing it from every operation guarantees that orchestrator-side error handling is a single shape, not a per-worker translation layer.

## Content (target — populated in Phase 0.4)

- `error-envelope.yaml` — RFC 7807 `application/problem+json` plus ig-central extensions: `code`, `lastErrorStage`, `retryable`, `retryAfterMs`, `trace[]`. Per CSV #17.
- `security-schemes.yaml` — service-account JWT bearer scheme. Per CSV #18.
- `common-headers.yaml` — `traceparent` (CSV #20), `Idempotency-Key` (CSV #16), `X-Request-Id`, `Prefer: respond-async` (CSV #13).
- `capabilities.yaml` — response shape for `GET /v1/capabilities`. Per CSV #21.
- `text-payload.yaml` — inline-text-vs-`textRef` pattern (≤ 256 KB inline; otherwise presigned MinIO URL). Per CSV #19.
- `pagination.yaml` — cursor-based pagination convention.
- `idempotency.yaml` — replay / duplicate-key semantics, 24 h TTL. Per CSV #16.
- `retry.yaml` — 429 + `Retry-After` semantics. Per CSV #23.

## How it's referenced

```yaml
# In any service's openapi.yaml:
components:
  responses:
    Error:
      $ref: '../_shared/error-envelope.yaml#/components/responses/Error'
```

Bundling for distribution: Spectral and `openapi-generator-maven-plugin` both resolve `$ref` across files. Generated artefacts under `contracts/<service>/generated/` will inline the resolved schema, so consumers don't need to walk relative paths.

## Versioning

The `_shared/VERSION` value is the version of this folder's contract surface. A breaking change here ripples — every service that `$ref`s a changed schema must regenerate stubs and verify the call sites. CI checks this on PR.
