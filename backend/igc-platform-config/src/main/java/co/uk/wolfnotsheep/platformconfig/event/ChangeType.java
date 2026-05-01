package co.uk.wolfnotsheep.platformconfig.event;

/**
 * Why a {@link ConfigChangedEvent} fired. {@link co.uk.wolfnotsheep.platformconfig.cache.ConfigCache}
 * treats all three identically (invalidate the entry); the distinction
 * matters for downstream listeners that aren't caches — e.g. metric
 * counters, audit pipelines, search-index updaters.
 */
public enum ChangeType {
    CREATED,
    UPDATED,
    DELETED
}
