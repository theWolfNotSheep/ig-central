package co.uk.wolfnotsheep.auditcollector.store;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Tier 2 (operational) storage abstraction. Phase 1.12 PR2 ships
 * the {@link MongoTier2Store} as the default; PR3 adds
 * {@link EsTier2Store} for Elasticsearch. Selection happens via
 * {@code gls.audit.collector.tier2-backend=mongo|es} (default
 * {@code mongo} until ES indices + ILM are wired in production).
 *
 * <p>Both implementations consume the same envelope shape; the
 * interface is deliberately narrow — it carries only what the
 * collector's two callers ({@code Tier2Consumer} and
 * {@code EventsController}) need.
 */
public interface Tier2Store {

    /**
     * Persist a Tier 2 event. Idempotent on {@code eventId} —
     * duplicates must not throw, must not double-write.
     */
    void save(StoredTier2Event event);

    /**
     * Fetch a single event by id. Returns empty when the id is not
     * present in this backend (the controller falls back to Tier 1).
     */
    Optional<StoredTier2Event> findById(String eventId);

    /**
     * Page through events matching the contracted Tier 2 search
     * filters. Page size is the cap — return up to {@code pageSize}
     * rows, ordered timestamp DESC. The controller signals
     * "more pages exist" by checking whether the returned list has
     * exactly {@code pageSize} elements.
     *
     * <p>{@code pageIndex} is 0-indexed and matches the simple
     * page-token scheme used by {@code EventsController}.
     */
    List<StoredTier2Event> search(SearchCriteria criteria, int pageIndex, int pageSize);

    /** Filter set for Tier 2 search. Any field may be null. */
    record SearchCriteria(
            String documentId,
            String eventType,
            String actorService,
            Instant fromInclusive,
            Instant toExclusive
    ) {}
}
