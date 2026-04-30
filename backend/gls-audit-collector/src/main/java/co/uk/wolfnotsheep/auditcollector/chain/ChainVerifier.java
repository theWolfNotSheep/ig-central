package co.uk.wolfnotsheep.auditcollector.chain;

import co.uk.wolfnotsheep.auditcollector.store.StoredTier1Event;
import co.uk.wolfnotsheep.auditcollector.store.Tier1Repository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Walks a per-resource Tier 1 chain (oldest → newest), recomputes the
 * hash sequence, and reports either {@code OK} + the count of events
 * traversed or {@code BROKEN} + the offending event id and the hash
 * mismatch detail.
 *
 * <p>The first event in a chain has {@code previousEventHash=null}
 * (per the envelope schema). Each subsequent event's
 * {@code previousEventHash} must equal {@link EventHasher#hashOf} of
 * the row immediately preceding it in timestamp order.
 */
@Service
public class ChainVerifier {

    private final Tier1Repository tier1Repo;

    public ChainVerifier(Tier1Repository tier1Repo) {
        this.tier1Repo = tier1Repo;
    }

    public Result verify(String resourceType, String resourceId) {
        Instant started = Instant.now();
        List<StoredTier1Event> chain =
                tier1Repo.findByResourceTypeAndResourceIdOrderByTimestampAsc(resourceType, resourceId);
        if (chain.isEmpty()) {
            return Result.notFound(resourceType, resourceId,
                    Duration.between(started, Instant.now()).toMillis());
        }

        StoredTier1Event prev = null;
        for (StoredTier1Event row : chain) {
            String expected = row.getPreviousEventHash();
            String computed = prev == null ? null : EventHasher.hashOf(prev);
            if (!eq(expected, computed)) {
                return Result.broken(resourceType, resourceId,
                        chain.get(0).getEventId(), row.getEventId(),
                        chain.size(), expected, computed,
                        Duration.between(started, Instant.now()).toMillis());
            }
            prev = row;
        }
        return Result.ok(resourceType, resourceId,
                chain.get(0).getEventId(), chain.get(chain.size() - 1).getEventId(),
                chain.size(),
                Duration.between(started, Instant.now()).toMillis());
    }

    private static boolean eq(String a, String b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.equals(b);
    }

    public record Result(
            String resourceType,
            String resourceId,
            Status status,
            int eventsTraversed,
            String firstEventId,
            String lastEventId,
            String brokenAtEventId,
            String expectedPreviousHash,
            String computedPreviousHash,
            long durationMs
    ) {
        public enum Status { OK, BROKEN, NOT_FOUND }

        public static Result ok(String type, String id, String first, String last,
                                int n, long durationMs) {
            return new Result(type, id, Status.OK, n, first, last, null, null, null, durationMs);
        }

        public static Result broken(String type, String id, String first, String offending,
                                    int n, String expected, String computed, long durationMs) {
            return new Result(type, id, Status.BROKEN, n, first, null,
                    offending, expected, computed, durationMs);
        }

        public static Result notFound(String type, String id, long durationMs) {
            return new Result(type, id, Status.NOT_FOUND, 0, null, null, null, null, null, durationMs);
        }
    }
}
