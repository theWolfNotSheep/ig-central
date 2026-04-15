package co.uk.wolfnotsheep.governance.models;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A versioned, reusable processing unit that plugs into a pipeline.
 * Blocks are the building blocks of document processing — prompts,
 * regex patterns, extractors, routers, enforcers.
 *
 * Each block has an immutable version history. The active version
 * is what pipelines use by default. Drafts can be edited before publishing.
 */
@Document(collection = "pipeline_blocks")
public class PipelineBlock {

    @Id
    private String id;

    @Indexed(unique = true)
    private String name;

    private String description;
    private BlockType type;
    private boolean active;

    // Version control
    private int activeVersion;
    private List<BlockVersion> versions = new ArrayList<>();
    private Map<String, Object> draftContent;
    private String draftChangelog;

    // Metrics
    private long documentsProcessed;
    private long correctionsReceived;
    private long feedbackCount;

    private Instant createdAt;
    private String createdBy;
    private Instant updatedAt;

    // Import provenance
    private String sourcePackSlug;
    private Integer sourcePackVersion;
    private Instant importedAt;

    public PipelineBlock() {}

    public enum BlockType {
        PROMPT,
        REGEX_SET,
        EXTRACTOR,
        ROUTER,
        ENFORCER,
        BERT_CLASSIFIER
    }

    public record BlockVersion(
            int version,
            Map<String, Object> content,
            String changelog,
            String publishedBy,
            Instant publishedAt
    ) {}

    /**
     * Get the content of a specific version.
     */
    public Map<String, Object> getVersionContent(int version) {
        return versions.stream()
                .filter(v -> v.version() == version)
                .findFirst()
                .map(BlockVersion::content)
                .orElse(null);
    }

    /**
     * Get the active version's content.
     */
    public Map<String, Object> getActiveContent() {
        return getVersionContent(activeVersion);
    }

    // Getters & setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public BlockType getType() { return type; }
    public void setType(BlockType type) { this.type = type; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public int getActiveVersion() { return activeVersion; }
    public void setActiveVersion(int activeVersion) { this.activeVersion = activeVersion; }

    public List<BlockVersion> getVersions() { return versions; }
    public void setVersions(List<BlockVersion> versions) { this.versions = versions; }

    public Map<String, Object> getDraftContent() { return draftContent; }
    public void setDraftContent(Map<String, Object> draftContent) { this.draftContent = draftContent; }

    public String getDraftChangelog() { return draftChangelog; }
    public void setDraftChangelog(String draftChangelog) { this.draftChangelog = draftChangelog; }

    public long getDocumentsProcessed() { return documentsProcessed; }
    public void setDocumentsProcessed(long documentsProcessed) { this.documentsProcessed = documentsProcessed; }

    public long getCorrectionsReceived() { return correctionsReceived; }
    public void setCorrectionsReceived(long correctionsReceived) { this.correctionsReceived = correctionsReceived; }

    public long getFeedbackCount() { return feedbackCount; }
    public void setFeedbackCount(long feedbackCount) { this.feedbackCount = feedbackCount; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public String getSourcePackSlug() { return sourcePackSlug; }
    public void setSourcePackSlug(String sourcePackSlug) { this.sourcePackSlug = sourcePackSlug; }

    public Integer getSourcePackVersion() { return sourcePackVersion; }
    public void setSourcePackVersion(Integer sourcePackVersion) { this.sourcePackVersion = sourcePackVersion; }

    public Instant getImportedAt() { return importedAt; }
    public void setImportedAt(Instant importedAt) { this.importedAt = importedAt; }
}
