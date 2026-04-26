---
title: Messaging contracts
lifecycle: forward
---

# Messaging contracts

AsyncAPI 3.0 specifications for every RabbitMQ exchange, channel, queue, and message envelope used by ig-central.

## Why AsyncAPI

The role OpenAPI plays for synchronous interfaces, AsyncAPI plays for async ones. Without it, Rabbit topology lives in scattered `@RabbitListener` annotations and `RabbitTemplate` calls — there is no single place that says *"this is the producer / consumer / payload shape for `gls.documents.classified`."*

Per `CLAUDE.md`: AsyncAPI 3.0 only. No 2.x.

## Content (target — populated in Phase 0.6)

- `asyncapi.yaml` — declares the existing topology as it stands today:
  - `gls.documents.{ingested, classified}` — pipeline-stage events.
  - `gls.pipeline.llm.*` — orchestrator → worker dispatch.
  - `gls.audit.*` — Tier 2 / 3 audit fan-out (Tier 1 lives in `audit/`).
  - `gls.config.changed` — cache invalidation per CSV #30.

## Versioning

The `messaging/VERSION` value covers all channels in `asyncapi.yaml`. Breaking changes (renamed channel, removed required field, narrowed enum) require a major bump and a deprecation window — old and new channels run in parallel until consumers migrate.

## Listener-stub strategy

AsyncAPI tooling for the JVM is less mature than OpenAPI. Strategy:

- **Validate** `asyncapi.yaml` in pre-commit + CI via `@asyncapi/cli`.
- **Do not generate** Spring AMQP listener stubs from AsyncAPI. The hand-written `@RabbitListener` form is clearer than what the current generators produce, and stays close to the consumer's transactional concerns.
- **Document** the contract → code mapping in each consumer's class-level Javadoc, so a reader can trace from the spec to the listener and back.

This decision will be revisited if AsyncAPI tooling matures or if the consumer count grows beyond the point where hand-rolling stays manageable.
