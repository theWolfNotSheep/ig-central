package co.uk.wolfnotsheep.governance.models;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

/**
 * Phase 3 PR12 — chronological log of every governance pack import.
 *
 * <p>{@link InstalledPack} captures only the currently-installed
 * version (one row per pack); this collection captures every import
 * event so operators can:
 * <ul>
 *   <li>see the full import history per pack (when, by whom, what
 *       components, how many items)</li>
 *   <li>roll back by re-importing a previous version (the import
 *       service already accepts any version number — the UI just
 *       needs a button per history row)</li>
 * </ul>
 *
 * <p>Append-only by convention; nothing in the application updates a
 * landed history row. Operators can manually delete rows via the
 * Mongo shell if storage becomes a concern, but at typical import
 * cadence (a few imports per pack per year) the collection stays
 * tiny.
 */
@Document(collection = "pack_import_history")
public class PackImportHistory {

    @Id
    private String id;

    @Indexed
    private String packSlug;

    /** Pack version that was imported (matches `PackVersionDto.versionNumber`). */
    private int version;

    /** Wall-clock time the import landed. */
    @Indexed(direction = org.springframework.data.mongodb.core.index.IndexDirection.DESCENDING)
    private Instant importedAt;

    /** Authenticated principal that triggered the import; "ADMIN" if no auth. */
    private String importedBy;

    /** Import mode: MERGE / OVERWRITE / SELECTIVE. PREVIEW imports are not recorded. */
    private String mode;

    /** Component types the import touched (TAXONOMY_CATEGORIES, RETENTION_SCHEDULES, …). */
    private List<String> componentTypes;

    /** When mode=SELECTIVE, the {@code componentType:itemKey} pairs the operator picked. */
    private List<String> selectedItemKeys;

    private int totalCreated;
    private int totalUpdated;
    private int totalSkipped;
    private int totalFailed;

    public PackImportHistory() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getPackSlug() { return packSlug; }
    public void setPackSlug(String packSlug) { this.packSlug = packSlug; }

    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }

    public Instant getImportedAt() { return importedAt; }
    public void setImportedAt(Instant importedAt) { this.importedAt = importedAt; }

    public String getImportedBy() { return importedBy; }
    public void setImportedBy(String importedBy) { this.importedBy = importedBy; }

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }

    public List<String> getComponentTypes() { return componentTypes; }
    public void setComponentTypes(List<String> componentTypes) { this.componentTypes = componentTypes; }

    public List<String> getSelectedItemKeys() { return selectedItemKeys; }
    public void setSelectedItemKeys(List<String> selectedItemKeys) { this.selectedItemKeys = selectedItemKeys; }

    public int getTotalCreated() { return totalCreated; }
    public void setTotalCreated(int totalCreated) { this.totalCreated = totalCreated; }

    public int getTotalUpdated() { return totalUpdated; }
    public void setTotalUpdated(int totalUpdated) { this.totalUpdated = totalUpdated; }

    public int getTotalSkipped() { return totalSkipped; }
    public void setTotalSkipped(int totalSkipped) { this.totalSkipped = totalSkipped; }

    public int getTotalFailed() { return totalFailed; }
    public void setTotalFailed(int totalFailed) { this.totalFailed = totalFailed; }
}
