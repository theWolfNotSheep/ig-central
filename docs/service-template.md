---
title: ig-central service template
lifecycle: forward
---

# ig-central service template

The cloneable pattern for v2 services. `gls-extraction-tika` (Phase 0.5 reference implementation) was the first instance; this document is the generic version. **Read it before starting a new service Maven module.**

The pattern is contract-first, audit-aware, idempotent, and built around three substrate libraries (`gls-platform-audit`, `gls-platform-config`, generated OpenAPI stubs). It also documents the surprises that bit the reference implementation, so the next service skips them.

## What "a v2 service" means

A v2 service is a Spring Boot app that:

1. Declares its HTTP surface in an OpenAPI 3.1.1 spec under `contracts/<service>/openapi.yaml`.
2. Generates server interfaces + model records via `openapi-generator-maven-plugin`.
3. Implements those interfaces in a `@RestController`.
4. Emits Tier 2 audit events for every request via `gls-platform-audit`.
5. Idempotency on the relevant request key (CSV #16) backed by Mongo with a TTL index.
6. Surfaces errors as RFC 7807 with concrete `code` extensions.
7. Ships as its own container with a `HEALTHCHECK`.

The reference is `gls-extraction-tika` — copy from there and adapt.

## Bring-up checklist

In rough chronological order:

1. **Contract first.** Author `contracts/<service>/openapi.yaml` referencing `_shared/`. `VERSION` 0.1.0; `CHANGELOG.md`; `README.md`. **No code yet.**
2. **Add the Maven module.** New `backend/<service>/pom.xml` with parent ref. Add to the reactor in `backend/pom.xml` and to the BOM in `backend/bom/pom.xml` (with a fresh `gls.<service>.version` per-deployable property — see "Independent Deployable Versions" in `CLAUDE.md`).
3. **Wire the generator.** Add `openapi-generator-maven-plugin` to the new pom with the same config options used in `gls-extraction-tika` (`interfaceOnly=true`, `useSpringBoot3=true`, `useJakartaEe=true`, `skipDefaultInterface=true`, `openApiNullable=false`, `useTags=true`). `inputSpec` points at the contract; `apiPackage` / `modelPackage` follow the `co.uk.wolfnotsheep.<domain>.api` / `.model` convention.
4. **Write the parser / source / sink layers as plain services.** Decouple them from the controller via interfaces so the controller is unit-testable against fakes.
5. **Implement the generated `*Api` interface in a `@RestController`.** This is where the orchestration lives. Wrap the work in a try/catch that emits `<DOMAIN>_FAILED` audit events and rethrows.
6. **Add a `MetaController implements MetaApi`** for `GET /v1/capabilities` and `GET /actuator/health`.
7. **Add a `<Domain>ExceptionHandler` `@RestControllerAdvice`.** Map every domain exception to RFC 7807 with a `code` extension that aligns with the audit `errorCode`.
8. **Idempotency.** Add a Mongo collection with a TTL index, a store with `tryAcquire` / `cacheResult` / `releaseOnFailure` semantics, and a check at the top of the controller.
9. **Dockerfile.** Multi-stage from Eclipse Temurin 25. Build context **must be repo root** (not `./backend/`) — see "Repo-root build context" below.
10. **Compose definition.** Add (or uncomment) the placeholder block in `docker-compose.yml`. `context: .` and `dockerfile: backend/<service>/Dockerfile`.
11. **Tests.** Unit tests for each layer; controller test that wires the layers via mocks. Integration tests when issue #7 unblocks Testcontainers.

## Substrate use

### Audit (`gls-platform-audit`)

Every service emits Tier 2 audit events on success and failure:

```java
@Autowired ObjectProvider<AuditEmitter> auditEmitterProvider;

// success
auditEmitter.emit(<Domain>Events.completed(serviceName, version, instance,
        nodeRunId, traceparent, /* domain-specific fields */));

// failure (called from controller's catch block — keeps traceparent +
// nodeRunId in scope without a request-scoped bean)
auditEmitter.emit(<Domain>Events.failed(serviceName, version, instance,
        nodeRunId, traceparent, errorCode, cause.getMessage()));
```

`<Domain>Events` is a pure factory living at `<package>.audit.<Domain>Events`. Tier 2 events don't chain (no `previousEventHash`) and don't carry a `retentionClass`. `eventId` is currently 26 uppercase hex chars from `UUID.randomUUID()` until the ULID utility lands in `gls-platform-audit`.

### Config cache (`gls-platform-config`)

If the service caches governance reference data, use `ConfigCache<V>` and register it with `ConfigCacheRegistry` on construction. Mutations elsewhere (e.g. via Hub or admin UI) publish `gls.config.changed` events; the dispatcher routes them to the cache by `entityType`. **Do not roll a Caffeine TTL** — that's the previous pattern, replaced by the change-driven invalidation per CSV #30.

### Generated stubs

`*Api` interfaces and model records are generated under `target/generated-sources/openapi/`. Do not edit; regenerate from the spec. Implementations live in your service's `web` package and `implements` the generated interface.

## Conventions worth their own callouts

### RFC 7807 error codes

The exception handler maps each domain exception to a 7807 problem detail with a `code` extension. The `code` aligns with the `errorCode` on the corresponding audit `EXTRACTION_FAILED` (or domain equivalent) event so a Tier 2 reader can correlate the wire-level error with the audited one. See `ExtractionExceptionHandler` for the canonical pattern.

### Idempotency

CSV #16 mandates idempotent retries on the relevant request key (e.g. `nodeRunId` for extraction). Pattern: a Mongo collection with a TTL index on `expiresAt`, a store with three return states (`ACQUIRED`, `IN_FLIGHT`, `CACHED`), and a check at the top of the controller. Failures must call `releaseOnFailure` (delete the row) so retries can proceed without waiting for TTL.

### Capabilities

`GET /v1/capabilities` advertises the build's identity (`service`, `version`), the supported `tiers`, and any loaded `models`. Pure extractors / routers leave `models` empty; classifier / inference services populate it.

## Repo-root Docker build context

Every service Dockerfile **must use the repo root as build context**, not `./backend/`. Reason: `gls-platform-audit`'s Maven build copies `contracts/audit/event-envelope.schema.json` into its jar via a build-time resource at `../../contracts/audit/`. With a `./backend/` context, Docker can't reach `contracts/`, the resource silently doesn't bundle, and the deployed jar's runtime schema validator fails on first emit.

Compose pattern:

```yaml
gls-<service>:
  build:
    context: .
    dockerfile: backend/gls-<service>/Dockerfile
```

Inside the Dockerfile, `WORKDIR /workspace`, copy `backend/` and `contracts/`, then `cd /workspace/backend && ./mvnw …`.

The existing `backend/Dockerfile` and `backend/Dockerfile.mcp` quietly violate this — separate fix tracked in the Phase 0.12 log entry.

## Generator gotchas

### `oneOf` schemas have no Jackson discriminator

`openapi-generator-maven-plugin` 7.10.0 emits `oneOf` shapes as an empty Java interface. There's **no `@JsonTypeInfo`**, no discriminator field. Wire serialisation works (Jackson knows the concrete subtype at write time) but **deserialisation cannot pick the subtype** — Jackson sees the interface and gives up.

If your service round-trips a response shape that contains a `oneOf` (idempotency cache, broker payload), register a Jackson mixin:

```java
@JsonTypeInfo(use = JsonTypeInfo.Id.DEDUCTION)
@JsonSubTypes({
    @JsonSubTypes.Type(<Branch1>.class),
    @JsonSubTypes.Type(<Branch2>.class)
})
abstract static class TextMixin {}

mapper.copy().addMixIn(<OneOfInterface>.class, TextMixin.class);
```

`DEDUCTION` picks the subtype by which fields are present — works as long as the branches share no required fields.

### Inline backticked colons break the spec parser

If a docstring in your spec contains `` `key: value` ``-style snippets, prefer the folded-scalar form (`description: |`) over the single-line form. Spectral lint accepts inline backticked-colons; the generator's snakeyaml parser sees the colon as a YAML mapping key and aborts. Symptom: `mapping values are not allowed here` from the generator.

### `kind` discriminator is not materialised

If the spec's `oneOf` has a `kind` discriminator on the union, the **interface** carries the schema info but the **concrete branch classes don't have a `setKind(...)`**. The discriminator is implicit in the implementing class identity. Don't try to set it; use `instanceof` (or the deduction mixin above) when reading.

## Spring Boot 4 gotchas

### `HealthIndicator` moved out of `org.springframework.boot.actuate.health`

Spring Boot 4 split actuator into multiple jars and relocated the health primitives:

| Boot 3 | Boot 4 |
|---|---|
| `org.springframework.boot.actuate.health.Health` | `org.springframework.boot.health.contributor.Health` |
| `org.springframework.boot.actuate.health.HealthIndicator` | `org.springframework.boot.health.contributor.HealthIndicator` |
| `org.springframework.boot.actuate.health.Status` | `org.springframework.boot.health.contributor.Status` |

Symptom: compile error `package org.springframework.boot.actuate.health does not exist` on a fresh module that follows Boot 3 documentation. The `spring-boot-starter-actuator` dep transitively pulls in `spring-boot-health` (4.0.x) — the classes are there, just at a different path. Same pattern likely applies to other actuator surfaces (`info`, `metrics`); check the new home before importing.

## Mockito gotchas

### `mock()` inside an outer `when()` chain

```java
// Trips Mockito's UnfinishedStubbing lint.
when(repository.findById(any())).thenReturn(buildHelperMock());
```

Fix: bind helper mocks to a local first.

```java
SomeMock helper = buildHelperMock();
when(repository.findById(any())).thenReturn(helper);
```

### `ConfigChangePublisher` needs spring-amqp on the test classpath

Mockito instruments the class to mock it. Instantiation triggers loading of `org.springframework.amqp.AmqpException` (referenced through `ConfigChangePublisher`'s optional dep). If your service depends on `gls-platform-config` and tests mock `ConfigChangePublisher`, declare:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-amqp</artifactId>
    <scope>test</scope>
</dependency>
```

Runtime is unaffected (services that need Rabbit pull it in via their own poms).

## Cross-references

- `version-2-architecture.md` — the *what* (how the system works).
- `version-2-decision-tree.csv` — the *why*.
- `version-2-implementation-plan.md` — the phased plan.
- `version-2-implementation-log.md` — the running narrative.
- `CLAUDE.md` — the rules.
- `backend/gls-extraction-tika/` — the reference implementation.
- `contracts/extraction/openapi.yaml` — the reference spec.
