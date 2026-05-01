package co.uk.wolfnotsheep.platformaudit.envelope;

/**
 * The thing the audited action acted on.
 *
 * @param type    Resource family.
 * @param id      Resource id.
 * @param version Monotonic resource version at the time of the event; nullable.
 */
public record Resource(
        ResourceType type,
        String id,
        Integer version
) {

    public static Resource of(ResourceType type, String id) {
        return new Resource(type, id, null);
    }
}
