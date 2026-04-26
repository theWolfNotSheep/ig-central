package co.uk.wolfnotsheep.platformaudit.envelope;

/**
 * Who or what performed the action.
 *
 * <p>{@code id} is required when {@link #type} is {@link ActorType#USER} (the schema
 * enforces this conditionally; see {@code event-envelope.schema.json}).
 *
 * @param type     SYSTEM / USER / CONNECTOR
 * @param service  Service identifier (e.g. {@code gls-classifier-router})
 * @param version  Service version
 * @param instance Pod / container instance id; nullable
 * @param id       User id; required when {@code type=USER}, otherwise null
 */
public record Actor(
        ActorType type,
        String service,
        String version,
        String instance,
        String id
) {

    public static Actor system(String service, String version, String instance) {
        return new Actor(ActorType.SYSTEM, service, version, instance, null);
    }

    public static Actor user(String userId, String service, String version) {
        return new Actor(ActorType.USER, service, version, null, userId);
    }

    public static Actor connector(String service, String version) {
        return new Actor(ActorType.CONNECTOR, service, version, null, null);
    }
}
