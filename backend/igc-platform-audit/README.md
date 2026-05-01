---
title: igc-platform-audit
lifecycle: forward
---

# igc-platform-audit

Shared library that every JVM service imports to emit audit events. Implements the persist-before-publish outbox pattern documented in `CLAUDE.md` → Audit Relay Pattern.

## Status

**Phase 0.7 — auto-configured, schema-validating, relay-publishing starter.** This module currently provides:

- The `AuditEvent` envelope record (mirrors `contracts/audit/event-envelope.schema.json`) and its supporting enums / nested records (`Tier`, `Outcome`, `ActorType`, `ResourceType`, `Actor`, `Resource`, `AuditDetails`).
- `AuditOutboxRecord` (the Mongo document mapping for `audit_outbox`).
- `AuditOutboxRepository` (Spring Data Mongo).
- `AuditEmitter` interface and `OutboxAuditEmitter` default implementation.
- `EnvelopeValidator` — JSON Schema 2020-12 validation against the bundled `event-envelope.schema.json`. Every emit goes through it; invalid envelopes raise `IllegalArgumentException` at the call site and never reach the outbox.
- `OutboxRelay` — `@Scheduled` task that polls `audit_outbox` for `PENDING` rows, transforms `details.content` to sha256 hashes for Tier 1 (per CSV #6, via `Tier1HashTransformer`), and publishes JSON envelopes to `audit.tier1.<eventType>` / `audit.tier2.<eventType>` on the `igc.audit` topic exchange. Exponential backoff on transient failures; rows mark `FAILED` after the configured attempt cap. Tunable via `igc.platform.audit.relay.*` properties.
- `PlatformAuditAutoConfiguration` — registers the repository, emitter, exchange, and relay as beans. The relay only activates when `RabbitTemplate` is on the classpath (consumer pulled in `spring-boot-starter-amqp`) and `igc.platform.audit.relay.enabled` is not `false`. Activated through `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.

**Not yet here (follow-ups):**

- **Leader election for Tier 1 single-writer per CSV #4** — multi-replica deployments today risk double-publishing the same envelope. Solve with ShedLock (or equivalent) before running more than one replica with the relay enabled. For a single replica the at-least-once delivery is masked by the downstream consumer's `eventId` dedup.
- **Comprehensive observability** — Micrometer counters for queue depth, publish rate, error rate, retry distribution. Today the relay only logs.
- **Circuit breaker on Rabbit** — currently each row trips the same backoff independently. A global circuit breaker would short-circuit the poll cycle once the broker is observably down.
- **Relay-side integration tests against a real RabbitMQ + Mongo Testcontainer** — blocked by issue #7 (Testcontainers MongoDB cleanup). The unit tests against mocks cover serialisation, routing, retry/backoff, and FAILED-cap behaviour.

## Configuration

Properties bound under `igc.platform.audit.relay.*`:

| Property | Default | What it does |
|---|---|---|
| `enabled` | `true` | Toggle the entire relay off. |
| `poll-interval` | `PT5S` | Duration between `pollOnce` cycles. |
| `batch-size` | `50` | Max rows per poll. |
| `max-attempts` | `5` | Retries before marking `FAILED`. |
| `backoff-base` | `PT1S` | Initial retry delay; doubled per attempt. |
| `backoff-max` | `PT5M` | Cap on the exponential backoff. |
| `exchange` | `igc.audit` | AMQP topic exchange to publish to. |

## Usage

```java
@Service
public class ClassificationEnforcementService {

    private final AuditEmitter auditEmitter;
    private final DocumentService documentService;

    public ClassificationEnforcementService(AuditEmitter auditEmitter, DocumentService documentService) {
        this.auditEmitter = auditEmitter;
        this.documentService = documentService;
    }

    @Transactional
    public void applyGovernance(String documentId, /* ... */) {
        // 1. Mutate state.
        DocumentModel doc = documentService.applyGovernance(documentId, /* ... */);

        // 2. Emit audit event in the SAME transaction. If the transaction
        //    rolls back, the outbox row rolls back with it.
        auditEmitter.emit(new AuditEvent(
                ulid(),                                   // eventId — generate a fresh ULID
                "GOVERNANCE_APPLIED",
                Tier.DOMAIN,
                AuditEvent.CURRENT_SCHEMA_VERSION,
                Instant.now(),
                documentId,                               // documentId
                /* pipelineRunId */ null,
                /* nodeRunId */ null,
                /* traceparent */ MDC.get("traceparent"),
                Actor.system("igc-app-assembly", buildVersion(), instanceId()),
                Resource.of(ResourceType.DOCUMENT, documentId),
                "ENFORCE_GOVERNANCE",
                Outcome.SUCCESS,
                AuditDetails.metadataOnly(Map.of(
                        "categoryId", doc.getCategoryId(),
                        "sensitivityLabel", doc.getSensitivityLabel().name()
                )),
                "7Y",                                     // retentionClass
                /* previousEventHash */ null               // first-in-chain
        ));
    }
}
```

## Rules (also in CLAUDE.md)

- Never bypass the emitter for audit traffic — no direct `RabbitTemplate.convertAndSend` for audit channels.
- Call `emit()` inside the originating transaction; the outbox write must commit / roll back atomically with the state change.
- `eventId` is the idempotency key. Construct fresh ULIDs per event; never reuse one across logically distinct events.
- For Tier 1 (`Tier.DOMAIN`) events: always set `resource`, `retentionClass`, and `previousEventHash` (null for first-in-chain). The chain integrity is the relay's responsibility once it lands.

## Cross-references

- `contracts/audit/event-envelope.schema.json` — the canonical envelope schema.
- `contracts/audit/asyncapi.yaml` — channel declarations.
- `version-2-architecture.md` §7 — three-tier audit model.
- `version-2-decision-tree.csv` rows #3 / #4 / #5 / #6 / #7 / #8 — audit decisions.
- `CLAUDE.md` — Audit Relay Pattern + Schema Migrations.
