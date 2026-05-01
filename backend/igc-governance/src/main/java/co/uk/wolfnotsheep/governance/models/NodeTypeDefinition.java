package co.uk.wolfnotsheep.governance.models;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Defines a pipeline node type — its visual appearance, config schema,
 * execution category, and connection handles. Stored in MongoDB so that
 * new node types can be added at runtime without code changes.
 *
 * Built-in types are seeded on first startup and cannot be deleted.
 * Custom types (e.g. new HTTP-based classifiers) can be created via the admin API.
 */
@Document(collection = "node_type_definitions")
public class NodeTypeDefinition {

    @Id
    private String id;

    @Indexed(unique = true)
    private String key;                  // "bertClassifier", "textExtraction", etc.

    private String displayName;          // "BERT Classifier"
    private String description;          // Help text shown in UI

    // Categorisation
    private String category;             // TRIGGER, PROCESSING, ACCELERATOR, LOGIC, ACTION, ERROR_HANDLING
    private int sortOrder;               // Display order within category

    // Execution metadata
    private String executionCategory;    // NOOP, BUILT_IN, ACCELERATOR, GENERIC_HTTP, ASYNC_BOUNDARY
    private String handlerBeanName;      // Spring bean name for BUILT_IN (null for others)
    private boolean requiresDocReload;   // Whether the engine should reload the document after this handler
    private String pipelinePhase;        // PRE_CLASSIFICATION, POST_CLASSIFICATION, BOTH

    // Visual / UI metadata
    private String iconName;             // Lucide icon name: "Cpu", "FileText", "Brain", etc.
    private String colorTheme;           // Tailwind colour key: "violet", "blue", "amber", etc.
    private List<HandleDefinition> handles;
    private List<BranchLabel> branchLabels;

    // Config schema (JSON Schema subset with ui: annotations for dynamic form rendering)
    private Map<String, Object> configSchema;
    private Map<String, Object> configDefaults;

    // Block linkage
    private String compatibleBlockType;  // "BERT_CLASSIFIER", "PROMPT", etc. — null if no block support

    // Summary & validation
    private String summaryTemplate;      // "Threshold: {{confidenceThreshold}}" — mustache-style
    private String validationExpression;  // "blockId" or "config.triggerType" — null means always valid

    // Performance impact indicators (shown in UI)
    private String performanceImpact;    // "high", "medium", null
    private String performanceWarning;   // Human-readable warning for UI

    // Generic HTTP executor config (used when executionCategory = GENERIC_HTTP)
    private GenericHttpConfig httpConfig;

    // Flags
    private boolean active;
    private boolean builtIn;             // true = seeded, cannot be deleted by admin

    private Instant createdAt;
    private Instant updatedAt;

    public NodeTypeDefinition() {}

    // ── Nested records ────────────────────────────────────────────────────

    public record HandleDefinition(
            String type,       // "source" or "target"
            String position,   // "Top", "Bottom", "Left", "Right"
            String id,         // null for default, "error" for error handles, "true"/"false" for conditions
            String color,      // "!bg-red-500", "!bg-green-500", etc.
            Map<String, Object> style  // optional CSS overrides (e.g. { left: "30%" })
    ) {}

    public record BranchLabel(
            String handleId,   // matches HandleDefinition.id
            String label,      // "True", "False", "Approved", "Rejected", etc.
            String color       // "text-green-600", "text-red-500", etc.
    ) {}

    public record GenericHttpConfig(
            String defaultUrl,
            String path,
            String method,
            int defaultTimeoutMs,
            Map<String, String> defaultHeaders,
            String requestBodyTemplate,
            Map<String, String> responseMapping   // JSON path -> AcceleratorResult field
    ) {}

    // ── Getters & setters ─────────────────────────────────────────────────

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }

    public String getExecutionCategory() { return executionCategory; }
    public void setExecutionCategory(String executionCategory) { this.executionCategory = executionCategory; }

    public String getHandlerBeanName() { return handlerBeanName; }
    public void setHandlerBeanName(String handlerBeanName) { this.handlerBeanName = handlerBeanName; }

    public boolean isRequiresDocReload() { return requiresDocReload; }
    public void setRequiresDocReload(boolean requiresDocReload) { this.requiresDocReload = requiresDocReload; }

    public String getPipelinePhase() { return pipelinePhase; }
    public void setPipelinePhase(String pipelinePhase) { this.pipelinePhase = pipelinePhase; }

    public String getIconName() { return iconName; }
    public void setIconName(String iconName) { this.iconName = iconName; }

    public String getColorTheme() { return colorTheme; }
    public void setColorTheme(String colorTheme) { this.colorTheme = colorTheme; }

    public List<HandleDefinition> getHandles() { return handles; }
    public void setHandles(List<HandleDefinition> handles) { this.handles = handles; }

    public List<BranchLabel> getBranchLabels() { return branchLabels; }
    public void setBranchLabels(List<BranchLabel> branchLabels) { this.branchLabels = branchLabels; }

    public Map<String, Object> getConfigSchema() { return configSchema; }
    public void setConfigSchema(Map<String, Object> configSchema) { this.configSchema = configSchema; }

    public Map<String, Object> getConfigDefaults() { return configDefaults; }
    public void setConfigDefaults(Map<String, Object> configDefaults) { this.configDefaults = configDefaults; }

    public String getCompatibleBlockType() { return compatibleBlockType; }
    public void setCompatibleBlockType(String compatibleBlockType) { this.compatibleBlockType = compatibleBlockType; }

    public String getSummaryTemplate() { return summaryTemplate; }
    public void setSummaryTemplate(String summaryTemplate) { this.summaryTemplate = summaryTemplate; }

    public String getValidationExpression() { return validationExpression; }
    public void setValidationExpression(String validationExpression) { this.validationExpression = validationExpression; }

    public String getPerformanceImpact() { return performanceImpact; }
    public void setPerformanceImpact(String performanceImpact) { this.performanceImpact = performanceImpact; }

    public String getPerformanceWarning() { return performanceWarning; }
    public void setPerformanceWarning(String performanceWarning) { this.performanceWarning = performanceWarning; }

    public GenericHttpConfig getHttpConfig() { return httpConfig; }
    public void setHttpConfig(GenericHttpConfig httpConfig) { this.httpConfig = httpConfig; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public boolean isBuiltIn() { return builtIn; }
    public void setBuiltIn(boolean builtIn) { this.builtIn = builtIn; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
