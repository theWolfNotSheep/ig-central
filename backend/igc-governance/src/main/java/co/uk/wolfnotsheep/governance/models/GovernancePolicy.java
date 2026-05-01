package co.uk.wolfnotsheep.governance.models;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * A governance policy defines rules that the LLM uses to decide how
 * a document should be handled. Policies are versioned and auditable.
 *
 * The MCP server exposes these to the LLM so it can reason about
 * which policies apply to a given document.
 */
@Document(collection = "governance_policies")
public class GovernancePolicy {

    @Id
    private String id;
    private String name;
    private String description;
    private int version;
    private boolean active;
    private Instant effectiveFrom;
    private Instant effectiveUntil;

    /** Natural-language rules the LLM should follow when this policy applies. */
    private List<String> rules;

    /** Which classification categories this policy applies to (empty = all). */
    private List<String> applicableCategoryIds;

    /** Which sensitivity labels trigger this policy (empty = all). */
    private List<SensitivityLabel> applicableSensitivities;

    /** Actions to enforce: e.g. {"encryption": "AES-256", "accessReview": "quarterly"} */
    private Map<String, String> enforcementActions;

    /** IDs of Legislation documents that mandate this policy. */
    private List<String> legislationIds;

    private Instant createdAt;
    private String createdBy;
    private Instant updatedAt;

    // Import provenance
    private String sourcePackSlug;
    private Integer sourcePackVersion;
    private Instant importedAt;

    public GovernancePolicy() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public Instant getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(Instant effectiveFrom) { this.effectiveFrom = effectiveFrom; }

    public Instant getEffectiveUntil() { return effectiveUntil; }
    public void setEffectiveUntil(Instant effectiveUntil) { this.effectiveUntil = effectiveUntil; }

    public List<String> getRules() { return rules; }
    public void setRules(List<String> rules) { this.rules = rules; }

    public List<String> getApplicableCategoryIds() { return applicableCategoryIds; }
    public void setApplicableCategoryIds(List<String> applicableCategoryIds) { this.applicableCategoryIds = applicableCategoryIds; }

    public List<SensitivityLabel> getApplicableSensitivities() { return applicableSensitivities; }
    public void setApplicableSensitivities(List<SensitivityLabel> applicableSensitivities) { this.applicableSensitivities = applicableSensitivities; }

    public Map<String, String> getEnforcementActions() { return enforcementActions; }
    public void setEnforcementActions(Map<String, String> enforcementActions) { this.enforcementActions = enforcementActions; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public List<String> getLegislationIds() { return legislationIds; }
    public void setLegislationIds(List<String> legislationIds) { this.legislationIds = legislationIds; }

    public String getSourcePackSlug() { return sourcePackSlug; }
    public void setSourcePackSlug(String sourcePackSlug) { this.sourcePackSlug = sourcePackSlug; }

    public Integer getSourcePackVersion() { return sourcePackVersion; }
    public void setSourcePackVersion(Integer sourcePackVersion) { this.sourcePackVersion = sourcePackVersion; }

    public Instant getImportedAt() { return importedAt; }
    public void setImportedAt(Instant importedAt) { this.importedAt = importedAt; }
}
