package co.uk.wolfnotsheep.platformconfig.listen;

import co.uk.wolfnotsheep.platformconfig.cache.ConfigCache;
import co.uk.wolfnotsheep.platformconfig.cache.ConfigCacheRegistry;
import co.uk.wolfnotsheep.platformconfig.event.ChangeType;
import co.uk.wolfnotsheep.platformconfig.event.ConfigChangedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigChangeDispatcherTest {

    @Test
    void dispatcher_invalidates_caches_then_runs_listeners() {
        ConfigCacheRegistry registry = new ConfigCacheRegistry();
        ConfigCache<String> cache = new ConfigCache<>("TAXONOMY");
        registry.register(cache);
        cache.get("hr-leave", k -> "VAL");

        List<ConfigChangedEvent> seen = new ArrayList<>();
        ConfigChangeListener listener = event -> {
            // By the time the listener runs, the cache has dropped its entry —
            // the next get() through the loader observes the fresh value.
            assertThat(cache.size()).isZero();
            seen.add(event);
        };

        ConfigChangeDispatcher dispatcher = new ConfigChangeDispatcher(
                registry, List.of(listener), new ObjectMapper().registerModule(new JavaTimeModule()));

        ConfigChangedEvent event = ConfigChangedEvent.single(
                "TAXONOMY", "hr-leave", ChangeType.UPDATED, "test");
        dispatcher.dispatch(event);

        assertThat(seen).containsExactly(event);
    }

    @Test
    void listener_failure_does_not_abort_other_listeners() {
        ConfigCacheRegistry registry = new ConfigCacheRegistry();
        List<String> trace = new ArrayList<>();
        ConfigChangeListener bad = e -> { trace.add("bad"); throw new RuntimeException("oops"); };
        ConfigChangeListener good = e -> trace.add("good");

        ConfigChangeDispatcher dispatcher = new ConfigChangeDispatcher(
                registry, List.of(bad, good), new ObjectMapper().registerModule(new JavaTimeModule()));

        dispatcher.dispatch(ConfigChangedEvent.bulk("X", ChangeType.UPDATED, "test"));

        assertThat(trace).containsExactly("bad", "good");
    }

    @Test
    void onMessage_decodes_json_and_dispatches() throws Exception {
        ConfigCacheRegistry registry = new ConfigCacheRegistry();
        ConfigCache<String> cache = new ConfigCache<>("POLICY");
        registry.register(cache);
        cache.get("p1", k -> "v1");

        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        ConfigChangeDispatcher dispatcher = new ConfigChangeDispatcher(
                registry, List.of(), mapper);

        ConfigChangedEvent event = new ConfigChangedEvent(
                "POLICY", List.of("p1"), ChangeType.DELETED,
                Instant.parse("2026-04-26T22:00:00Z"), "gls-app-assembly", null);
        byte[] body = mapper.writeValueAsBytes(event);

        dispatcher.onMessage(body);

        assertThat(cache.size()).isZero();
    }

    @Test
    void onMessage_drops_malformed_payload_silently() {
        ConfigCacheRegistry registry = new ConfigCacheRegistry();
        ConfigChangeDispatcher dispatcher = new ConfigChangeDispatcher(
                registry, List.of(), new ObjectMapper().registerModule(new JavaTimeModule()));

        // Should not throw — malformed events are logged and dropped per CSV #30.
        dispatcher.onMessage("not-json".getBytes());
    }
}
