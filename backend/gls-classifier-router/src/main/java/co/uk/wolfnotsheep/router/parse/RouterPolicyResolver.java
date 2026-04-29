package co.uk.wolfnotsheep.router.parse;

import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Reads the active {@code ROUTER} block from {@code pipeline_blocks}
 * and parses its content into a {@link RouterPolicy}.
 *
 * <p>Caches the parsed policy with a configurable TTL
 * ({@code gls.router.policy.refresh-seconds}, default 60s). On a
 * cache miss / expiry, re-reads from Mongo. On any failure (block
 * missing, content malformed, Mongo unreachable) returns
 * {@link RouterPolicy#DEFAULT} and logs at WARN — the cascade keeps
 * working on the conservative defaults rather than failing closed.
 *
 * <p>Same minimal-coupling pattern as {@code PromptBlockResolver} in
 * {@code gls-slm-worker}: reads the raw {@code pipeline_blocks}
 * collection via {@link MongoTemplate} so the router doesn't depend
 * on {@code gls-governance}'s class shape.
 */
@Component
public class RouterPolicyResolver {

    private static final Logger log = LoggerFactory.getLogger(RouterPolicyResolver.class);
    private static final String COLLECTION = "pipeline_blocks";

    private final MongoTemplate mongo;
    private final String blockName;
    private final Duration refreshInterval;
    private final AtomicReference<Cached> cache = new AtomicReference<>(null);

    public RouterPolicyResolver(
            MongoTemplate mongo,
            @Value("${gls.router.policy.block-name:default-router}") String blockName,
            @Value("${gls.router.policy.refresh-seconds:60}") int refreshSeconds) {
        this.mongo = mongo;
        this.blockName = blockName;
        this.refreshInterval = Duration.ofSeconds(Math.max(1, refreshSeconds));
    }

    public RouterPolicy current() {
        Instant now = Instant.now();
        Cached cached = cache.get();
        if (cached != null && cached.fetchedAt.plus(refreshInterval).isAfter(now)) {
            return cached.policy;
        }
        RouterPolicy fresh = load();
        cache.set(new Cached(fresh, now));
        return fresh;
    }

    private RouterPolicy load() {
        Document doc;
        try {
            Query q = new Query(Criteria.where("name").is(blockName));
            doc = mongo.findOne(q, Document.class, COLLECTION);
        } catch (RuntimeException e) {
            log.warn("router policy: Mongo lookup failed for block name={} — using defaults: {}",
                    blockName, e.getMessage());
            return RouterPolicy.DEFAULT;
        }
        if (doc == null) {
            log.warn("router policy: no ROUTER block named '{}' — using defaults", blockName);
            return RouterPolicy.DEFAULT;
        }
        Map<String, Object> content = contentForVersion(doc);
        if (content == null) {
            log.warn("router policy: ROUTER block '{}' has no resolvable content — using defaults",
                    blockName);
            return RouterPolicy.DEFAULT;
        }
        return parse(content);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> contentForVersion(Document doc) {
        Integer activeVersion = doc.getInteger("activeVersion");
        List<Document> versions = (List<Document>) doc.get("versions");
        if (activeVersion != null && versions != null) {
            for (Document v : versions) {
                Integer ver = v.getInteger("version");
                if (ver != null && ver.equals(activeVersion)) {
                    Object content = v.get("content");
                    if (content instanceof Document d) return d;
                    if (content instanceof Map<?, ?> m) return (Map<String, Object>) m;
                }
            }
        }
        Object draft = doc.get("draftContent");
        if (draft instanceof Document d) return d;
        if (draft instanceof Map<?, ?> m) return (Map<String, Object>) m;
        return null;
    }

    @SuppressWarnings("unchecked")
    static RouterPolicy parse(Map<String, Object> content) {
        Object tiersObj = content.get("tiers");
        if (!(tiersObj instanceof Map<?, ?> tiersMap)) {
            return RouterPolicy.DEFAULT;
        }
        Map<String, Object> tiers = (Map<String, Object>) tiersMap;
        return new RouterPolicy(
                parseTier(tiers.get("bert"), RouterPolicy.DEFAULT.bert()),
                parseTier(tiers.get("slm"), RouterPolicy.DEFAULT.slm()),
                parseTier(tiers.get("llm"), RouterPolicy.DEFAULT.llm()));
    }

    @SuppressWarnings("unchecked")
    private static RouterPolicy.TierPolicy parseTier(Object tierObj, RouterPolicy.TierPolicy fallback) {
        if (!(tierObj instanceof Map<?, ?> tierMap)) {
            return fallback;
        }
        Map<String, Object> tier = (Map<String, Object>) tierMap;
        boolean enabled = parseBool(tier.get("enabled"), fallback.enabled());
        float accept = parseFloat(tier.get("accept"), fallback.accept());
        return new RouterPolicy.TierPolicy(enabled, accept);
    }

    private static boolean parseBool(Object v, boolean fallback) {
        if (v instanceof Boolean b) return b;
        return fallback;
    }

    private static float parseFloat(Object v, float fallback) {
        if (v instanceof Number n) return n.floatValue();
        return fallback;
    }

    /** Visible for tests — bypasses the cache, forces a fresh load. */
    void invalidateCache() {
        cache.set(null);
    }

    private record Cached(RouterPolicy policy, Instant fetchedAt) {
    }
}
