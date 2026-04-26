---
title: Hello World — smoke spec
lifecycle: forward
---

# Hello World

**This is not a real service.** It exists solely to exercise the OpenAPI tooling pipeline end-to-end:

1. Spectral lint resolves cross-file `$ref`s under `_shared/` and reports clean.
2. `openapi-generator-maven-plugin` generates a Spring server stub from the spec.
3. The generated Java compiles in the `backend/contracts-smoke` Maven module.

A change to this spec, to `_shared/`, to `.spectral.yaml`, or to the generator wiring that breaks any of the three steps fails CI. That's the value: the smoke catches tooling regressions before they bite a per-service spec author.

## How it's exercised

- **Local:** `cd backend && ./mvnw verify -pl contracts-smoke -am`
- **Pre-commit:** Spectral lints this file (and everything else under `contracts/`) on every commit per `.pre-commit-config.yaml`.
- **CI:** `contracts-validate` job in `.github/workflows/ci.yml` runs Spectral plus the Maven smoke build.

## What this spec demonstrates (for per-service authors)

Patterns a real per-service spec should copy:

- Cross-file `$ref` to `_shared/error-envelope.yaml` for `4XX` / `5XX` responses.
- Cross-file `$ref` to `_shared/common-headers.yaml` for `Traceparent` (and other shared headers).
- Cross-file `$ref` to `_shared/security-schemes.yaml` populating a local `securitySchemes.serviceJwt` entry.
- A document-level `security:` block applying `serviceJwt` to every operation by default.
- Operation-level `tags`, `operationId`, `summary` so the generator emits clean Java identifiers.

## Versioning

This is a smoke spec; treat it like any other contract for version-bump purposes. Changes that adjust what we expect the generator to emit — new operations, new ref patterns we want to test — bump the version and add a CHANGELOG entry.
