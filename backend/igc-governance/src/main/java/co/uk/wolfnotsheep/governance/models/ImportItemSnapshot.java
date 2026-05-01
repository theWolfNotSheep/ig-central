package co.uk.wolfnotsheep.governance.models;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

/**
 * Stores a snapshot of field values for each item at import time.
 * Used for three-way diff: snapshot (baseline) vs local entity vs new hub data.
 */
@Document(collection = "import_item_snapshots")
@CompoundIndex(name = "idx_pack_component_key", def = "{'packSlug':1, 'componentType':1, 'itemKey':1}", unique = true)
public class ImportItemSnapshot {

    @Id
    private String id;

    private String packSlug;
    private int packVersion;
    private String componentType;
    private String itemKey;
    private String entityId;
    private Map<String, Object> snapshotFields;
    private Instant importedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getPackSlug() { return packSlug; }
    public void setPackSlug(String packSlug) { this.packSlug = packSlug; }

    public int getPackVersion() { return packVersion; }
    public void setPackVersion(int packVersion) { this.packVersion = packVersion; }

    public String getComponentType() { return componentType; }
    public void setComponentType(String componentType) { this.componentType = componentType; }

    public String getItemKey() { return itemKey; }
    public void setItemKey(String itemKey) { this.itemKey = itemKey; }

    public String getEntityId() { return entityId; }
    public void setEntityId(String entityId) { this.entityId = entityId; }

    public Map<String, Object> getSnapshotFields() { return snapshotFields; }
    public void setSnapshotFields(Map<String, Object> snapshotFields) { this.snapshotFields = snapshotFields; }

    public Instant getImportedAt() { return importedAt; }
    public void setImportedAt(Instant importedAt) { this.importedAt = importedAt; }
}
