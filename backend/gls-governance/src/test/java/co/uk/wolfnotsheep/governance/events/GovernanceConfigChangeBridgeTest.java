package co.uk.wolfnotsheep.governance.events;

import co.uk.wolfnotsheep.governance.models.ClassificationCategory;
import co.uk.wolfnotsheep.governance.models.GovernancePolicy;
import co.uk.wolfnotsheep.platformconfig.event.ChangeType;
import co.uk.wolfnotsheep.platformconfig.publish.ConfigChangePublisher;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.mongodb.core.mapping.event.AfterDeleteEvent;
import org.springframework.data.mongodb.core.mapping.event.AfterSaveEvent;

import java.lang.reflect.Method;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GovernanceConfigChangeBridgeTest {

    private ConfigChangePublisher publisher;
    private GovernanceConfigChangeBridge bridge;

    @BeforeEach
    void setUp() {
        publisher = mock(ConfigChangePublisher.class);
        bridge = new GovernanceConfigChangeBridge(providerOf(publisher));
    }

    @Test
    void save_of_known_entity_publishes_single_with_mapped_type_and_id() {
        ClassificationCategory cat = new ClassificationCategory();
        setId(cat, "cat-1");

        bridge.onAfterSave(new AfterSaveEvent<>(cat, new Document(), "classification_categories"));

        verify(publisher).publishSingle(eq("TAXONOMY"), eq("cat-1"), eq(ChangeType.UPDATED));
    }

    @Test
    void save_of_unknown_entity_is_ignored() {
        Object random = new Object();

        bridge.onAfterSave(new AfterSaveEvent<>(random, new Document(), "rando"));

        verifyNoInteractions(publisher);
    }

    @Test
    void save_with_null_id_falls_back_to_bulk() {
        GovernancePolicy policy = new GovernancePolicy();
        // policy.id stays null — simulates a pre-save where Mongo hasn't assigned yet.

        bridge.onAfterSave(new AfterSaveEvent<>(policy, new Document(), "governance_policies"));

        verify(publisher).publishBulk(eq("POLICY"), eq(ChangeType.UPDATED));
        verify(publisher, never()).publishSingle(eq("POLICY"), eq(null), eq(ChangeType.UPDATED));
    }

    @Test
    void delete_event_publishes_bulk_for_mapped_type() {
        bridge.onAfterDelete(new AfterDeleteEvent<>(new Document("_id", "cat-x"),
                ClassificationCategory.class, "classification_categories"));

        verify(publisher).publishBulk(eq("TAXONOMY"), eq(ChangeType.DELETED));
    }

    @Test
    void delete_event_for_unknown_type_is_ignored() {
        bridge.onAfterDelete(new AfterDeleteEvent<>(new Document(), Object.class, "rando"));

        verifyNoInteractions(publisher);
    }

    @Test
    void publisher_absence_is_non_fatal() {
        GovernanceConfigChangeBridge offline = new GovernanceConfigChangeBridge(providerOf(null));
        ClassificationCategory cat = new ClassificationCategory();
        setId(cat, "cat-2");

        // No NPE — getIfAvailable returns null and the bridge bails out.
        offline.onAfterSave(new AfterSaveEvent<>(cat, new Document(), "classification_categories"));

        verify(publisher, times(0)).publishSingle(eq("TAXONOMY"), eq("cat-2"), eq(ChangeType.UPDATED));
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<ConfigChangePublisher> providerOf(ConfigChangePublisher publisher) {
        ObjectProvider<ConfigChangePublisher> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(publisher);
        return provider;
    }

    private static void setId(Object entity, String id) {
        try {
            Method setId = entity.getClass().getMethod("setId", String.class);
            setId.invoke(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
