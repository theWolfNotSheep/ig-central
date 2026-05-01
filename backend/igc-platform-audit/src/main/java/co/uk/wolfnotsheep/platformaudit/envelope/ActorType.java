package co.uk.wolfnotsheep.platformaudit.envelope;

/**
 * Who or what performed the action.
 *
 * <ul>
 *     <li>{@link #SYSTEM} — service-to-service workflow step.</li>
 *     <li>{@link #USER} — human action (override, approval). Requires {@code actor.id}.</li>
 *     <li>{@link #CONNECTOR} — external source (Drive, Gmail) acting on the user's behalf.</li>
 * </ul>
 */
public enum ActorType {
    SYSTEM,
    USER,
    CONNECTOR
}
