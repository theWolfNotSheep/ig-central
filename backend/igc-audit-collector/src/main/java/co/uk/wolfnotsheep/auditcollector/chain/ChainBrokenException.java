package co.uk.wolfnotsheep.auditcollector.chain;

/**
 * Thrown when a Tier 1 event's {@code previousEventHash} does not
 * match the recomputed hash of the latest event in its resource's
 * chain (CSV #4). The collector's consumer logs + dead-letters
 * rather than persisting — accepting a chain-broken event would
 * silently corrupt the audit trail.
 */
public class ChainBrokenException extends RuntimeException {

    private final String resourceType;
    private final String resourceId;
    private final String expectedPreviousHash;
    private final String computedPreviousHash;
    private final String offendingEventId;

    public ChainBrokenException(String resourceType, String resourceId,
                                String expectedPreviousHash, String computedPreviousHash,
                                String offendingEventId) {
        super("Tier 1 chain broken for " + resourceType + ":" + resourceId
                + " on event " + offendingEventId
                + " (expected previousEventHash=" + expectedPreviousHash
                + ", computed=" + computedPreviousHash + ")");
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.expectedPreviousHash = expectedPreviousHash;
        this.computedPreviousHash = computedPreviousHash;
        this.offendingEventId = offendingEventId;
    }

    public String resourceType() { return resourceType; }
    public String resourceId() { return resourceId; }
    public String expectedPreviousHash() { return expectedPreviousHash; }
    public String computedPreviousHash() { return computedPreviousHash; }
    public String offendingEventId() { return offendingEventId; }
}
