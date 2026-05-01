package co.uk.wolfnotsheep.auditcollector.store;

import java.util.List;
import java.util.Optional;

/**
 * Tier 1 (compliance) storage abstraction. Phase 1.12 PR4 ships
 * {@link AppendOnlyMongoTier1Store} as the default — Mongo collection
 * with app-layer append-only enforcement (reject every write that
 * isn't a fresh insert; expose no update/delete operations on the
 * interface). Future PRs can drop in an S3 Object Lock implementation
 * behind the same interface.
 *
 * <p>The interface is deliberately narrow — it carries only the
 * operations the collector's three callers
 * ({@code Tier1Consumer}, {@code ChainVerifier}, {@code EventsController})
 * need. There is intentionally <b>no</b> {@code update}, {@code save},
 * or {@code delete} method — the audit-of-record is immutable.
 */
public interface Tier1Store {

    /**
     * Append a new event. Throws {@link AppendOnlyViolationException}
     * if a row with the same {@code eventId} already exists — the
     * collector's consumer treats that as an idempotent no-op. All
     * other failures bubble.
     */
    void append(StoredTier1Event event) throws AppendOnlyViolationException;

    /** Fetch a single event by id. */
    Optional<StoredTier1Event> findById(String eventId);

    /**
     * Latest event in a per-resource chain — used for chain
     * validation in {@link co.uk.wolfnotsheep.auditcollector.consumer.Tier1Consumer}.
     */
    Optional<StoredTier1Event> findLatestForResource(String resourceType, String resourceId);

    /**
     * Walk a resource's chain in order — used by
     * {@link co.uk.wolfnotsheep.auditcollector.chain.ChainVerifier}.
     */
    List<StoredTier1Event> findChainAsc(String resourceType, String resourceId);
}
