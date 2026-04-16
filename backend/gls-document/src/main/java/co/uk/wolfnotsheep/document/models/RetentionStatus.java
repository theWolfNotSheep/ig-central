package co.uk.wolfnotsheep.document.models;

/**
 * Lifecycle of a document's retention clock, derived from the trigger type
 * defined on its classification category and what's happened since.
 */
public enum RetentionStatus {
    /** Trigger is event-based (DATE_CLOSED, EVENT_BASED, SUPERSEDED) and the
     *  triggering event hasn't happened yet — no expiry date computed. */
    AWAITING_TRIGGER,

    /** Retention clock is running — retentionExpiresAt is in the future. */
    RUNNING,

    /** Retention period has elapsed — eligible for the disposition action. */
    EXPIRED,

    /** Disposition action has been carried out (deleted/archived/transferred). */
    DISPOSED,

    /** Document was superseded by a newer version — no longer subject to retention. */
    SUPERSEDED
}
