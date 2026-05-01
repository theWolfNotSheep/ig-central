---
title: igc-platform-config
lifecycle: forward
---

# igc-platform-config

Shared library for the **change-driven config-cache pattern** per CSV #30. Replaces the previous Caffeine TTL caches across services. Per-replica in-memory cache, populated on first read; invalidation flows through the `igc.config.changed` Rabbit channel so every replica drops the right entries the moment a write lands.

## Status

**Phase 0.8 skeleton.** This module currently provides:

- `ConfigCache<V>` — per-replica in-memory cache for one entity type. Keys are strings; values are arbitrary. Backed by `ConcurrentHashMap.computeIfAbsent` so concurrent loaders don't double-fire.
- `ConfigCacheRegistry` — routes `entityType` → `ConfigCache` instances. Multiple caches per type allowed.
- `ConfigChangedEvent` — wire payload mirroring the AsyncAPI declaration in `contracts/messaging/asyncapi.yaml`. ULID-free, structurally simple.
- `ConfigChangePublisher` — single emitter into `igc.config` topic exchange. Local writes and Hub-driven imports both call it.
- `ConfigChangeDispatcher` — `@RabbitListener` against an anonymous queue (one per replica, non-durable + exclusive + auto-delete). Dispatches to caches first, then to `ConfigChangeListener` beans for non-cache reactions.
- `PlatformConfigAutoConfiguration` — wires the registry, publisher, dispatcher, and the `igc.config` exchange. Cache layer works without a broker (consumers can use it as a plain in-memory cache when offline / in tests); broker-touching beans only register when `RabbitTemplate` is on the classpath.

**Not yet here (follow-ups):**

- **`AppConfigService` migration** — replace its hand-rolled `ConcurrentHashMap` with `ConfigCache` and publish on writes. Separate PR.
- **MCP server's Caffeine cache for governance entities** — replace with `ConfigCache`. Separate PR.
- **Tests against a real broker** — blocked by issue #7. Current tests cover the cache primitive, registry, event record, and dispatcher logic via mocks / direct calls.

## Usage

### Wiring a cache

```java
@Configuration
public class TaxonomyConfig {

    @Bean
    public ConfigCache<TaxonomyCategory> taxonomyCache(ConfigCacheRegistry registry) {
        ConfigCache<TaxonomyCategory> cache = new ConfigCache<>("TAXONOMY");
        registry.register(cache);
        return cache;
    }
}
```

### Reading through the cache

```java
@Service
public class TaxonomyService {

    private final ConfigCache<TaxonomyCategory> cache;
    private final TaxonomyRepository repo;

    public Optional<TaxonomyCategory> findById(String id) {
        TaxonomyCategory cached = cache.get(id, k -> repo.findById(k).orElse(null));
        return Optional.ofNullable(cached);
    }
}
```

### Publishing on writes

```java
@Service
public class TaxonomyService {

    private final ConfigChangePublisher publisher;

    @Transactional
    public TaxonomyCategory update(String id, TaxonomyCategory updated) {
        TaxonomyCategory saved = repo.save(updated);
        publisher.publishSingle("TAXONOMY", id, ChangeType.UPDATED);
        return saved;
    }
}
```

### Reacting to invalidations beyond cache eviction

```java
@Component
public class TaxonomyAuditTracker implements ConfigChangeListener {
    @Override
    public void onConfigChanged(ConfigChangedEvent event) {
        if (!"TAXONOMY".equals(event.entityType())) return;
        // ...metric counter, audit emit, etc.
    }
}
```

## Rules (also in CLAUDE.md → Config Cache Pattern, when added)

- **Never bypass `ConfigChangePublisher`** for cache invalidation — no direct `RabbitTemplate.convertAndSend("igc.config", "config.changed", ...)` calls.
- **Both local writes and Hub imports MUST publish** — the channel is single-source per CSV #30.
- **Bulk events (`entityIds` empty)** are the wildcard. Use them sparingly; targeted invalidation keeps caches warm for the rest of the working set.
- **Cache loaders must be idempotent and side-effect-free** — `computeIfAbsent` may be re-invoked under concurrent miss, and the cache happily caches whatever the loader returns.
- **Listener failures are isolated** — one bad listener does not abort the others. Don't rely on cross-listener ordering beyond "caches invalidate first."

## Cross-references

- `contracts/messaging/asyncapi.yaml` — `configChanged` channel + `ConfigChangedEvent` schema.
- `version-2-decision-tree.csv` row #30 — change-driven invalidation decision (DECIDED).
- `version-2-architecture.md` §3 / §9.15 — cache + governance reference data.
