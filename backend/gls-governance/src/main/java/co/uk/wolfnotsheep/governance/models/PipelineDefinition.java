package co.uk.wolfnotsheep.governance.models;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.List;

/**
 * A configurable AI processing pipeline. Defines the sequence of steps
 * a document goes through after upload. Admins can create, reorder,
 * enable/disable steps, and configure prompts — all without code changes.
 */
@Document(collection = "pipeline_definitions")
public class PipelineDefinition {

    @Id
    private String id;
    private String name;
    private String description;
    private boolean active;
    private boolean isDefault;
    private List<String> applicableCategoryIds;
    private boolean includeSubCategories;
    private List<String> applicableMimeTypes;
    private List<PipelineStep> steps;
    private List<VisualNode> visualNodes;
    private List<VisualEdge> visualEdges;
    private Instant createdAt;
    private Instant updatedAt;

    public PipelineDefinition() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public boolean isDefault() { return isDefault; }
    public void setDefault(boolean isDefault) { this.isDefault = isDefault; }

    public List<String> getApplicableCategoryIds() { return applicableCategoryIds; }
    public void setApplicableCategoryIds(List<String> applicableCategoryIds) { this.applicableCategoryIds = applicableCategoryIds; }

    public boolean isIncludeSubCategories() { return includeSubCategories; }
    public void setIncludeSubCategories(boolean includeSubCategories) { this.includeSubCategories = includeSubCategories; }

    public List<String> getApplicableMimeTypes() { return applicableMimeTypes; }
    public void setApplicableMimeTypes(List<String> applicableMimeTypes) { this.applicableMimeTypes = applicableMimeTypes; }

    public List<PipelineStep> getSteps() { return steps; }
    public void setSteps(List<PipelineStep> steps) { this.steps = steps; }

    public List<VisualNode> getVisualNodes() { return visualNodes; }
    public void setVisualNodes(List<VisualNode> visualNodes) { this.visualNodes = visualNodes; }

    public List<VisualEdge> getVisualEdges() { return visualEdges; }
    public void setVisualEdges(List<VisualEdge> visualEdges) { this.visualEdges = visualEdges; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public record VisualNode(
            @Field("_id") String id,
            String type,        // node type: trigger, textExtraction, piiScanner, aiClassification, condition, governance, humanReview, notification, errorHandler, webhook, transform
            String label,
            double x,
            double y,
            String blockId,
            String status,      // configured, incomplete, error
            java.util.Map<String, Object> data  // type-specific configuration
    ) {}

    public record VisualEdge(
            @Field("_id") String id,
            String source,
            String target,
            String sourceHandle,
            String targetHandle,
            String label
    ) {}

    /**
     * A single step in the processing pipeline.
     */
    public static class PipelineStep {

        private int order;
        private String name;
        private String description;
        private StepType type;
        private boolean enabled;

        /** Reference to a PipelineBlock — if set, block config is used */
        private String blockId;

        /** Pin to a specific block version (null = use active version) */
        private Integer blockVersion;

        /** For LLM_PROMPT steps: the system prompt (legacy, prefer blockId) */
        private String systemPrompt;

        /** For LLM_PROMPT steps: the user prompt template with {placeholders} (legacy, prefer blockId) */
        private String userPromptTemplate;

        /** For CONDITIONAL steps: only run if this condition is met */
        private String condition;

        /** Arbitrary config for the step (timeout, model override, etc.) */
        private java.util.Map<String, String> config;

        public enum StepType {
            BUILT_IN,       // Text extraction, governance enforcement
            PATTERN,        // Regex-based scanning (PII Tier 1)
            LLM_PROMPT,     // Send to Claude with a configurable prompt
            CONDITIONAL,    // Run only if condition met
            ACCELERATOR,    // Pre-classification accelerator (BERT, fingerprint, rules, similarity, external HTTP)
            SYNC_LLM        // Synchronous LLM call via pipeline engine
        }

        public PipelineStep() {}

        public int getOrder() { return order; }
        public void setOrder(int order) { this.order = order; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public StepType getType() { return type; }
        public void setType(StepType type) { this.type = type; }

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public String getBlockId() { return blockId; }
        public void setBlockId(String blockId) { this.blockId = blockId; }

        public Integer getBlockVersion() { return blockVersion; }
        public void setBlockVersion(Integer blockVersion) { this.blockVersion = blockVersion; }

        public String getSystemPrompt() { return systemPrompt; }
        public void setSystemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; }

        public String getUserPromptTemplate() { return userPromptTemplate; }
        public void setUserPromptTemplate(String userPromptTemplate) { this.userPromptTemplate = userPromptTemplate; }

        public String getCondition() { return condition; }
        public void setCondition(String condition) { this.condition = condition; }

        public java.util.Map<String, String> getConfig() { return config; }
        public void setConfig(java.util.Map<String, String> config) { this.config = config; }
    }
}
