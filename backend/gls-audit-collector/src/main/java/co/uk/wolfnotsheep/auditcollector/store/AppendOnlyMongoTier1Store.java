package co.uk.wolfnotsheep.auditcollector.store;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Mongo-backed {@link Tier1Store} with app-layer append-only
 * enforcement per CSV #3 (the recommended Mongo-with-role-based-deny
 * option for the first cut). The hardening lives in two places:
 *
 * <ol>
 *   <li><b>App layer.</b> The interface exposes only
 *       {@link #append(StoredTier1Event)} + reads. There's no
 *       {@code update} / {@code save} method; the entire surface
 *       semantically forbids overwrites. Re-appending the same id
 *       throws {@link AppendOnlyViolationException} (the consumer
 *       treats that as an idempotent no-op).</li>
 *   <li><b>Datastore layer (operator responsibility).</b> The Mongo
 *       service account this container uses should grant only
 *       <code>insert</code> + <code>find</code> on
 *       {@code audit_tier1_events}; <code>update</code>,
 *       <code>delete</code>, <code>renameCollectionSameDB</code>, and
 *       <code>dropCollection</code> should be explicitly denied.
 *       Documented in
 *       {@code contracts/audit-collector/README.md} (PR1).</li>
 * </ol>
 *
 * <p>Default backend per
 * {@code gls.audit.collector.tier1-backend=mongo} (or absent). Future
 * PRs may add an S3-Object-Lock-backed implementation behind the
 * same interface — selectable via
 * {@code gls.audit.collector.tier1-backend=s3-object-lock}.
 */
@Service
@ConditionalOnProperty(name = "gls.audit.collector.tier1-backend", havingValue = "mongo", matchIfMissing = true)
public class AppendOnlyMongoTier1Store implements Tier1Store {

    private final Tier1Repository repo;

    public AppendOnlyMongoTier1Store(Tier1Repository repo) {
        this.repo = repo;
    }

    @Override
    public void append(StoredTier1Event event) {
        try {
            // Use insert (not save) to guarantee no upsert. Save would replace
            // an existing row with matching @Id; insert is the only Mongo write
            // primitive that throws on collision.
            repo.insert(event);
        } catch (DuplicateKeyException e) {
            throw new AppendOnlyViolationException(event.getEventId());
        }
    }

    @Override
    public Optional<StoredTier1Event> findById(String eventId) {
        return repo.findById(eventId);
    }

    @Override
    public Optional<StoredTier1Event> findLatestForResource(String resourceType, String resourceId) {
        return repo.findFirstByResourceTypeAndResourceIdOrderByTimestampDesc(resourceType, resourceId);
    }

    @Override
    public List<StoredTier1Event> findChainAsc(String resourceType, String resourceId) {
        return repo.findByResourceTypeAndResourceIdOrderByTimestampAsc(resourceType, resourceId);
    }
}
