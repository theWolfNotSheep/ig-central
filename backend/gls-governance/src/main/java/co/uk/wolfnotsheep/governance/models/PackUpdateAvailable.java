package co.uk.wolfnotsheep.governance.models;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Records an available update for a previously imported governance pack.
 * Populated by the PackUpdateObserver polling service and consumed by
 * the frontend to show update notifications.
 */
@Document(collection = "pack_updates_available")
public class PackUpdateAvailable {

    @Id
    private String id;

    @Indexed(unique = true)
    private String packSlug;

    private String packName;
    private int installedVersion;
    private int latestVersion;
    private String changelog;
    private Instant publishedAt;
    private List<String> componentTypes = new ArrayList<>();
    private boolean dismissed;
    private Instant detectedAt;

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

    public int getLatestVersion() {
        return latestVersion;
    }

    public void setLatestVersion(int latestVersion) {
        this.latestVersion = latestVersion;
    }

    public String getChangelog() {
        return changelog;
    }

    public void setChangelog(String changelog) {
        this.changelog = changelog;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(Instant publishedAt) {
        this.publishedAt = publishedAt;
    }

    public List<String> getComponentTypes() {
        return componentTypes;
    }

    public void setComponentTypes(List<String> componentTypes) {
        this.componentTypes = componentTypes;
    }

    public boolean isDismissed() {
        return dismissed;
    }

    public void setDismissed(boolean dismissed) {
        this.dismissed = dismissed;
    }

    public Instant getDetectedAt() {
        return detectedAt;
    }

    public void setDetectedAt(Instant detectedAt) {
        this.detectedAt = detectedAt;
    }
}
