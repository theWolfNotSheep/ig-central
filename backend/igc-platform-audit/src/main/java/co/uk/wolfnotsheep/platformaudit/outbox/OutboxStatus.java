package co.uk.wolfnotsheep.platformaudit.outbox;

/**
 * Status of an {@link AuditOutboxRecord}.
 *
 * <p>The relay (in a follow-up PR) transitions records:
 *
 * <ul>
 *     <li>{@link #PENDING} → {@link #PUBLISHED} on successful Rabbit publish.</li>
 *     <li>{@link #PENDING} → {@link #FAILED} after the configured retry cap.</li>
 *     <li>{@link #FAILED} → {@link #PENDING} via operator action (re-queue UI).</li>
 * </ul>
 */
public enum OutboxStatus {
    PENDING,
    PUBLISHED,
    FAILED
}
