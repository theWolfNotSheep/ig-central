package co.uk.wolfnotsheep.governance.models;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

/**
 * Defines the structured metadata fields the LLM should extract
 * for documents in a given category. For example, an "HR Leave Request"
 * schema might have fields: employee_name, leave_type, start_date, end_date.
 *
 * Schemas are linked to classification categories. When the LLM classifies
 * a document into a category with a schema, it extracts the defined fields
 * into extractedMetadata on the classification result.
 */
@Document(collection = "metadata_schemas")
public class MetadataSchema {

    @Id
    private String id;

    @Indexed(unique = true)
    private String name;

    private String description;
    private String extractionContext;
    private List<MetadataField> fields;
    private List<String> linkedMimeTypes; // e.g. ["application/pdf", "application/vnd.openxmlformats"]
    private boolean active;
    private Instant createdAt;
    private Instant updatedAt;

    // Import provenance
    private String sourcePackSlug;
    private Integer sourcePackVersion;
    private Instant importedAt;

    public MetadataSchema() {}

    public record MetadataField(
            String fieldName,
            FieldType dataType,
            boolean required,
            String description,
            String extractionHint,
            List<String> examples
    ) {}

    public enum FieldType {
        TEXT, NUMBER, DATE, CURRENCY, BOOLEAN, KEYWORD
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getExtractionContext() { return extractionContext; }
    public void setExtractionContext(String extractionContext) { this.extractionContext = extractionContext; }

    public List<MetadataField> getFields() { return fields; }
    public void setFields(List<MetadataField> fields) { this.fields = fields; }

    public List<String> getLinkedMimeTypes() { return linkedMimeTypes; }
    public void setLinkedMimeTypes(List<String> linkedMimeTypes) { this.linkedMimeTypes = linkedMimeTypes; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public String getSourcePackSlug() { return sourcePackSlug; }
    public void setSourcePackSlug(String sourcePackSlug) { this.sourcePackSlug = sourcePackSlug; }

    public Integer getSourcePackVersion() { return sourcePackVersion; }
    public void setSourcePackVersion(Integer sourcePackVersion) { this.sourcePackVersion = sourcePackVersion; }

    public Instant getImportedAt() { return importedAt; }
    public void setImportedAt(Instant importedAt) { this.importedAt = importedAt; }
}
