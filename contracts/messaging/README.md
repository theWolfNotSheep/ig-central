---
title: Messaging contracts
lifecycle: forward
---

# Messaging contracts

AsyncAPI 3.0 specifications for every RabbitMQ exchange, channel, queue, and message envelope used by ig-central.

## Why AsyncAPI

The role OpenAPI plays for synchronous interfaces, AsyncAPI plays for async ones. Without it, Rabbit topology lives in scattered `@RabbitListener` annotations and `RabbitTemplate` calls — there is no single place that says *"this is the producer / consumer / payload shape for `igc.documents.classified`."*

Per `CLAUDE.md`: AsyncAPI 3.0 only. No 2.x.

## Content (delivered in Phase 0.6 — VERSION 0.2.0)

- `asyncapi.yaml` — declares the production topology:
  - `igc.documents` topic exchange — `document.ingested`, `document.processed`, `document.classified`, `document.classification.failed`.
  - `igc.pipeline` topic exchange — `pipeline.llm.requested`, `pipeline.llm.completed`, `pipeline.resume`, `pipeline.dlq`.
  - DLX: `igc.documents.dlx` (fanout).

Forward-looking — declared in this file's description but not yet wired in code:

- `igc.config.changed` — cache invalidation per CSV #30. Lands with the Phase 0.8 `igc-platform-config` shared library.
- `igc.audit.*` — audit tier channels per CSV #3 / #4 / #5. Stub exists in `contracts/audit/asyncapi.yaml`; full payload schema and tier wiring in Phase 0.7.

## Versioning

The `messaging/VERSION` value covers all channels in `asyncapi.yaml`. Breaking changes (renamed channel, removed required field, narrowed enum) require a major bump and a deprecation window — old and new channels run in parallel until consumers migrate.

## Listener-stub strategy

AsyncAPI tooling for the JVM is less mature than OpenAPI. Strategy:

- **Validate** `asyncapi.yaml` in pre-commit + CI via `@asyncapi/cli`.
- **Do not generate** Spring AMQP listener stubs from AsyncAPI. The hand-written `@RabbitListener` form is clearer than what the current generators produce, and stays close to the consumer's transactional concerns.
- **Document** the contract → code mapping in each consumer's class-level Javadoc, so a reader can trace from the spec to the listener and back.

This decision will be revisited if AsyncAPI tooling matures or if the consumer count grows beyond the point where hand-rolling stays manageable.
