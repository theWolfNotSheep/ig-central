package co.uk.wolfnotsheep.governance.events;

import co.uk.wolfnotsheep.governance.models.ClassificationCategory;
import co.uk.wolfnotsheep.governance.models.ClassificationCorrection;
import co.uk.wolfnotsheep.governance.models.GovernancePolicy;
import co.uk.wolfnotsheep.governance.models.MetadataSchema;
import co.uk.wolfnotsheep.governance.models.PiiTypeDefinition;
import co.uk.wolfnotsheep.governance.models.RetentionSchedule;
import co.uk.wolfnotsheep.governance.models.SensitivityDefinition;
import co.uk.wolfnotsheep.governance.models.StorageTier;
import co.uk.wolfnotsheep.governance.models.TraitDefinition;
import co.uk.wolfnotsheep.platformconfig.event.ChangeType;
import co.uk.wolfnotsheep.platformconfig.publish.ConfigChangePublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.event.EventListener;
import org.springframework.data.mongodb.core.mapping.event.AfterDeleteEvent;
import org.springframework.data.mongodb.core.mapping.event.AfterSaveEvent;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Map;

/**
 * Bridges Spring Data Mongo lifecycle events for governance entities to
 * the {@code gls.config.changed} channel. Every save on a known entity
 * type fires a single-entity {@code ConfigChangedEvent}; every
 * type-aware delete fires a bulk event for the affected entity type.
 *
 * <p>This closes the loop on the MCP cache migration — the listener in
 * {@code gls-mcp-server} ({@code McpConfigInvalidator}) only matters
 * when something is publishing. This class is the something.
 *
 * <p>Coverage strategy: a {@link Map} of model class → entity-type
 * identifier. Saves of types not in the map are silently ignored —
 * Mongo fires lifecycle events for every save in the application
 * (including {@code audit_outbox}, document writes, etc.); the map is
 * the filter. Add a new entry whenever a new governance model joins
 * the change-driven cache pattern.
 *
 * <p><strong>CREATED vs UPDATED is collapsed to UPDATED.</strong>
 * Distinguishing them requires either a pre-save event with the prior
 * state or post-hoc inspection of the id assignment — both fragile.
 * The cache layer treats all three change types identically, and audit
 * pipelines that care about the distinction publish via their own paths.
 *
 * <p><strong>Publisher absence is non-fatal.</strong> The
 * {@link ObjectProvider} pattern lets the bridge instantiate in
 * broker-less test contexts without forcing every consumer to depend on
 * Rabbit at runtime.
 */
@Component
public class GovernanceConfigChangeBridge {

    private static final Logger log = LoggerFactory.getLogger(GovernanceConfigChangeBridge.class);

    /** Class → wire-protocol entity-type identifier for the cache contract per CSV #30. */
    public static final Map<Class<?>, String> ENTITY_TYPE_BY_CLASS = Map.ofEntries(
            Map.entry(ClassificationCategory.class, "TAXONOMY"),
            Map.entry(SensitivityDefinition.class, "SENSITIVITY"),
            Map.entry(TraitDefinition.class, "TRAIT"),
            Map.entry(GovernancePolicy.class, "POLICY"),
            Map.entry(RetentionSchedule.class, "RETENTION_SCHEDULE"),
            Map.entry(StorageTier.class, "STORAGE_TIER"),
            Map.entry(MetadataSchema.class, "METADATA_SCHEMA"),
            Map.entry(ClassificationCorrection.class, "CORRECTION"),
            Map.entry(PiiTypeDefinition.class, "PII_PATTERN_SET"));

    private final ObjectProvider<ConfigChangePublisher> publisherProvider;

    public GovernanceConfigChangeBridge(ObjectProvider<ConfigChangePublisher> publisherProvider) {
        this.publisherProvider = publisherProvider;
    }

    @EventListener
    public void onAfterSave(AfterSaveEvent<?> event) {
        Object source = event.getSource();
        String entityType = ENTITY_TYPE_BY_CLASS.get(source.getClass());
        if (entityType == null) {
            return;
        }
        ConfigChangePublisher publisher = publisherProvider.getIfAvailable();
        if (publisher == null) {
            return;
        }
        String id = extractId(source);
        if (id == null) {
            // Save event without an id is unusual — fall back to bulk so the
            // cache definitely re-reads on next access.
            publisher.publishBulk(entityType, ChangeType.UPDATED);
            log.debug("governance bridge: save with null id → bulk for {}", entityType);
            return;
        }
        publisher.publishSingle(entityType, id, ChangeType.UPDATED);
    }

    @EventListener
    public void onAfterDelete(AfterDeleteEvent<?> event) {
        Class<?> type = event.getType();
        String entityType = type == null ? null : ENTITY_TYPE_BY_CLASS.get(type);
        if (entityType == null) {
            return;
        }
        ConfigChangePublisher publisher = publisherProvider.getIfAvailable();
        if (publisher == null) {
            return;
        }
        // AfterDeleteEvent doesn't reliably carry the deleted id (deletes
        // can target a query, not an entity). Fall back to bulk — peers
        // re-read fresh on next access. Slightly wasteful, correct.
        publisher.publishBulk(entityType, ChangeType.DELETED);
        log.debug("governance bridge: delete on {} → bulk invalidate", entityType);
    }

    private static String extractId(Object entity) {
        try {
            Method getId = entity.getClass().getMethod("getId");
            Object value = getId.invoke(entity);
            return value == null ? null : value.toString();
        } catch (ReflectiveOperationException e) {
            log.warn("governance bridge: no getId() on {}, dropping event ({})",
                    entity.getClass().getSimpleName(), e.getMessage());
            return null;
        }
    }
}
