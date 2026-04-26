---
title: Contracts
lifecycle: forward
---

# Contracts

The single source of truth for every service-to-service interface in ig-central v2.

Per `CLAUDE.md` (API Contracts section): no controller method ships without an OpenAPI / AsyncAPI / JSON-Schema entry under this directory first. Implementation derives from the contract — never the other way round.

## Layout

```
contracts/
├── .spectral.yaml         # OpenAPI 3.1.1 lint ruleset (Spectral)
├── _shared/               # Cross-cutting schemas referenced via $ref
├── messaging/             # AsyncAPI 3.0 — RabbitMQ topology
├── audit/                 # AsyncAPI + JSON Schema for audit Tier 1/2/3
├── blocks/                # JSON Schema 2020-12 for pipeline-block content
└── <service>/             # Per-service OpenAPI specs (added when the service's phase begins)
```

Each folder contains:

- `README.md` — scope and conventions for this folder.
- `VERSION` — semver, starts at `0.1.0`. Bumps on every spec change.
- `CHANGELOG.md` — what changed at each version (Keep a Changelog format).
- The spec(s) themselves (`openapi.yaml`, `asyncapi.yaml`, `*.schema.json`).
- `generated/` (per-service only) — committed server stubs and client SDKs from `openapi-generator-maven-plugin`.

## Versioning

- **Semver, per folder.** Each folder evolves independently.
- **Every spec change requires a `VERSION` bump in the same PR.** CI fails the build on missing bumps.
- **Breaking changes require a major bump** (1.x → 2.x) and a deprecation window — the old major stays callable until consumers migrate.

## Tooling

- **Linter:** Spectral (per CSV #40). Run `npx @stoplight/spectral-cli lint contracts/<path>` locally; pre-commit hook does it automatically. Rules in `contracts/.spectral.yaml`.
- **Generator:** `openapi-generator-maven-plugin` (per CSV #39). Wired into the Maven build; outputs land under `contracts/<service>/generated/`.
- **AsyncAPI validation:** `@asyncapi/cli` in pre-commit + CI. See `messaging/README.md` for the listener-stub strategy (tl;dr: hand-write the Spring AMQP annotations; the contracts validate the topology, not the bindings).

## Workflow for a contract change

1. Edit the spec file.
2. Bump `VERSION`.
3. Add a `CHANGELOG.md` entry.
4. Run lint (`spectral lint`) — pre-commit will catch you if you forget.
5. Regenerate stubs (`mvn -pl contracts/<service> generate-sources`).
6. Commit everything together — spec, version, changelog, generated/.
7. CI re-validates and fails on drift.

## Per-service folders

Service-specific folders (e.g. `extraction-tika/`, `classifier-router/`) are added in the PR that begins that service's contract work — not preemptively. The convention is established here; following it for a new service is a copy of the cross-cutting folder layout.

## Cross-references

- `CLAUDE.md` — API Contracts section (the rules).
- `version-2-architecture.md` — §11 (contract shape decisions); §3 (container topology).
- `version-2-decision-tree.csv` — decisions #13 / #14 / #16 / #17 / #18 / #19–26 / #39 / #40 govern this directory.
- `version-2-implementation-plan.md` — Phase 0.3 / 0.4 / 0.5 / 0.6 / 0.7 scope.
