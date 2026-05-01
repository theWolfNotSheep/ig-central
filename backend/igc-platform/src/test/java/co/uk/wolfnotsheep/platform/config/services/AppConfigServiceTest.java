package co.uk.wolfnotsheep.platform.config.services;

import co.uk.wolfnotsheep.platform.config.models.AppConfig;
import co.uk.wolfnotsheep.platform.config.repositories.AppConfigRepository;
import co.uk.wolfnotsheep.platformconfig.cache.ConfigCacheRegistry;
import co.uk.wolfnotsheep.platformconfig.event.ChangeType;
import co.uk.wolfnotsheep.platformconfig.publish.ConfigChangePublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the migration of {@link AppConfigService} onto
 * {@code igc-platform-config}: registry registration, cache hit/miss,
 * change-driven invalidation + publish on writes.
 */
class AppConfigServiceTest {

    private AppConfigRepository repo;
    private ConfigCacheRegistry registry;
    private ConfigChangePublisher publisher;
    private AppConfigService service;

    @BeforeEach
    void setUp() {
        repo = mock(AppConfigRepository.class);
        registry = new ConfigCacheRegistry();
        publisher = mock(ConfigChangePublisher.class);
        service = new AppConfigService(repo, registry, providerOf(publisher));
    }

    @Test
    void cache_is_registered_with_the_registry_for_APP_CONFIG() {
        assertThat(registry.registrationCount(AppConfigService.ENTITY_TYPE)).isEqualTo(1);
    }

    @Test
    void get_misses_cache_then_loads_then_hits() {
        AppConfig stored = new AppConfig();
        stored.setKey("feature.x");
        stored.setValue("ON");
        when(repo.findByKey("feature.x")).thenReturn(Optional.of(stored));

        Optional<AppConfig> first = service.get("feature.x");
        Optional<AppConfig> second = service.get("feature.x");

        assertThat(first).contains(stored);
        assertThat(second).contains(stored);
        verify(repo, times(1)).findByKey("feature.x"); // miss only on the first
    }

    @Test
    void get_returns_empty_and_does_not_cache_when_repo_misses() {
        when(repo.findByKey("missing")).thenReturn(Optional.empty());

        Optional<AppConfig> first = service.get("missing");
        Optional<AppConfig> second = service.get("missing");

        assertThat(first).isEmpty();
        assertThat(second).isEmpty();
        // both calls should hit the repo (null is not cached, so the loader retries)
        verify(repo, times(2)).findByKey("missing");
    }

    @Test
    void save_new_entry_publishes_CREATED_and_invalidates_local_cache() {
        when(repo.findByKey("feature.y")).thenReturn(Optional.empty());
        when(repo.save(any(AppConfig.class))).thenAnswer(inv -> {
            AppConfig in = inv.getArgument(0);
            in.setId("id-y"); // simulate Mongo assigning an id
            return in;
        });

        AppConfig saved = service.save("feature.y", "features", "VALUE", "desc");

        assertThat(saved.getKey()).isEqualTo("feature.y");
        verify(publisher, times(1)).publishSingle(
                eq(AppConfigService.ENTITY_TYPE), eq("feature.y"), eq(ChangeType.CREATED));
    }

    @Test
    void save_existing_entry_publishes_UPDATED() {
        AppConfig existing = new AppConfig();
        existing.setId("id-z");
        existing.setKey("feature.z");
        when(repo.findByKey("feature.z")).thenReturn(Optional.of(existing));
        when(repo.save(any(AppConfig.class))).thenAnswer(inv -> inv.getArgument(0));

        service.save("feature.z", "features", "NEW", "desc");

        verify(publisher).publishSingle(
                eq(AppConfigService.ENTITY_TYPE), eq("feature.z"), eq(ChangeType.UPDATED));
    }

    @Test
    void save_after_get_invalidates_so_next_get_re_loads() {
        AppConfig before = new AppConfig();
        before.setId("id-q");
        before.setKey("feature.q");
        before.setValue("OLD");
        AppConfig after = new AppConfig();
        after.setId("id-q");
        after.setKey("feature.q");
        after.setValue("NEW");
        when(repo.findByKey("feature.q")).thenReturn(Optional.of(before), Optional.of(after));
        when(repo.save(any(AppConfig.class))).thenReturn(after);

        Optional<AppConfig> first = service.get("feature.q");
        service.save("feature.q", "features", "NEW", "desc");
        Optional<AppConfig> second = service.get("feature.q");

        assertThat(first).map(AppConfig::getValue).contains("OLD");
        assertThat(second).map(AppConfig::getValue).contains("NEW");
    }

    @Test
    void refresh_publishes_bulk_invalidation() {
        service.refresh();
        verify(publisher).publishBulk(AppConfigService.ENTITY_TYPE, ChangeType.UPDATED);
    }

    @Test
    void publishes_are_no_op_when_publisher_bean_is_absent() {
        AppConfigService offlineService = new AppConfigService(repo, registry, providerOf(null));

        when(repo.findByKey("k")).thenReturn(Optional.empty());
        when(repo.save(any(AppConfig.class))).thenAnswer(inv -> inv.getArgument(0));

        offlineService.save("k", "cat", "v", "d");
        offlineService.refresh();

        verify(publisher, never()).publishSingle(any(), any(), any());
        verify(publisher, never()).publishBulk(any(), any());
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<ConfigChangePublisher> providerOf(ConfigChangePublisher publisher) {
        ObjectProvider<ConfigChangePublisher> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(publisher);
        return provider;
    }
}
