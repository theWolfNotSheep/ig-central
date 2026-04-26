package co.uk.wolfnotsheep.platformconfig.listen;

import co.uk.wolfnotsheep.platformconfig.event.ConfigChangedEvent;

/**
 * Hook for non-cache reactions to a config change — metric counters,
 * audit pipelines, search-index updaters, etc.
 *
 * <p>Caches don't implement this interface; they register with
 * {@link co.uk.wolfnotsheep.platformconfig.cache.ConfigCacheRegistry}
 * directly. Listeners run after caches in a single dispatcher tick, so
 * a listener observing a change can assume caches have already dropped
 * stale entries.
 *
 * <p>Listeners are picked up by Spring's bean discovery — declare them
 * as {@code @Component} or {@code @Bean} and the
 * {@link ConfigChangeDispatcher} fans events out to all of them.
 */
@FunctionalInterface
public interface ConfigChangeListener {
    void onConfigChanged(ConfigChangedEvent event);
}
