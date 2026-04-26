---
title: gls-platform-audit
lifecycle: forward
---

# gls-platform-audit

Shared library that every JVM service imports to emit audit events. Implements the persist-before-publish outbox pattern documented in `CLAUDE.md` → Audit Relay Pattern.

## Status

**Phase 0.7 — auto-configured, schema-validating starter.** This module currently provides:

- The `AuditEvent` envelope record (mirrors `contracts/audit/event-envelope.schema.json`) and its supporting enums / nested records (`Tier`, `Outcome`, `ActorType`, `ResourceType`, `Actor`, `Resource`, `AuditDetails`).
- `AuditOutboxRecord` (the Mongo document mapping for `audit_outbox`).
- `AuditOutboxRepository` (Spring Data Mongo).
- `AuditEmitter` interface and `OutboxAuditEmitter` default implementation.
- `EnvelopeValidator` — JSON Schema 2020-12 validation against the bundled `event-envelope.schema.json`. Every emit goes through it; invalid envelopes raise `IllegalArgumentException` at the call site and never reach the outbox.
- `PlatformAuditAutoConfiguration` — registers the repository (via `@EnableMongoRepositories`) and the default emitter as beans whenever Spring Data Mongo is on the classpath. Activated through `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`. Consumers depending on this module pick up `AuditEmitter` automatically; override by declaring their own `AuditEmitter` bean.

**Not yet here (follow-ups):**

- The **outbox-to-Rabbit relay** — a scheduled task that polls `audit_outbox`, publishes to `audit.tier1.{eventType}` / `audit.tier2.{eventType}`, marks rows `PUBLISHED`. The interesting operational logic (retry/backoff, circuit-breaker on Rabbit, observability, leader election for the Tier 1 single-writer constraint per CSV #4) lives in this piece.
- **Relay-side tests and tests for the emitter against a real Mongo Testcontainer** — currently blocked by issue #7 (Testcontainers MongoDB cleanup). `EnvelopeValidator` has direct unit coverage that exercises the bundled schema.

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
                Actor.system("gls-app-assembly", buildVersion(), instanceId()),
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
