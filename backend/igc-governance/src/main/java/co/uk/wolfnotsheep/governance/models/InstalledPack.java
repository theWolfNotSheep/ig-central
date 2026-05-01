package co.uk.wolfnotsheep.governance.models;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Tracks which governance packs have been imported from the hub,
 * enabling the update observer to check for newer versions.
 */
@Document(collection = "installed_packs")
public class InstalledPack {

    @Id
    private String id;

    @Indexed(unique = true)
    private String packSlug;

    private String packName;
    private int installedVersion;
    private Instant importedAt;
    private List<String> componentTypesImported = new ArrayList<>();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getPackSlug() {
        return packSlug;
    }

    public void setPackSlug(String packSlug) {
        this.packSlug = packSlug;
    }

    public String getPackName() {
        return packName;
    }

    public void setPackName(String packName) {
        this.packName = packName;
    }

    public int getInstalledVersion() {
        return installedVersion;
    }

    public void setInstalledVersion(int installedVersion) {
        this.installedVersion = installedVersion;
    }

    public Instant getImportedAt() {
        return importedAt;
    }

    public void setImportedAt(Instant importedAt) {
        this.importedAt = importedAt;
    }

    public List<String> getComponentTypesImported() {
        return componentTypesImported;
    }

    public void setComponentTypesImported(List<String> componentTypesImported) {
        this.componentTypesImported = componentTypesImported;
    }
}
