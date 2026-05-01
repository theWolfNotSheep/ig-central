package co.uk.wolfnotsheep.document.models;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Records every AI interaction in the system — classifications, schema suggestions,
 * block improvements, metadata extraction tests. Provides full audit trail of prompts,
 * context, tool calls, reasoning, and responses for admin debugging and prompt tuning.
 */
@Document(collection = "ai_usage_log")
public class AiUsageLog {

    @Id
    private String id;

    @Indexed
    private Instant timestamp;

    // ── Who & What ─────────────────────────────────────
    @Indexed
    private String usageType;       // CLASSIFY, SUGGEST_SCHEMA, TEST_SCHEMA, IMPROVE_BLOCK, METADATA_EXTRACT
    @Indexed
    private String triggeredBy;     // user email or "SYSTEM" for pipeline
    private String documentId;
    private String documentName;

    // ── AI Provider ────────────────────────────────────
    private String provider;        // anthropic, ollama
    private String model;           // claude-sonnet-4-20250514, qwen2.5:32b
    private String pipelineId;
    private String promptBlockId;
    private Integer promptBlockVersion;

    // ── Prompts & Context ──────────────────────────────
    private String systemPrompt;
    private String userPrompt;
    private List<ToolCall> toolCalls; // MCP tool calls made during this interaction

    // ── Response ───────────────────────────────────────
    private String response;        // raw AI response
    private String reasoning;       // extracted reasoning/explanation
    private Map<String, Object> result; // structured result (category, sensitivity, metadata, etc.)

    // ── Metrics ────────────────────────────────────────
    private long durationMs;
    private int inputTokens;
    private int outputTokens;
    private double estimatedCost;   // USD estimate

    // ── Status ─────────────────────────────────────────
    @Indexed
    private String status;          // SUCCESS, FAILED, NO_RESULT
    private String errorMessage;

    // ── Outcome (populated later) ──────────────────────
    private String outcome;         // ACCEPTED, OVERRIDDEN, REJECTED (set when human reviews)
    private String overriddenBy;
    private Instant outcomeAt;

    public AiUsageLog() {
        this.timestamp = Instant.now();
    }

    /**
     * Record of an MCP tool call made during an AI interaction.
     */
    public static class ToolCall {
        private String toolName;
        private String input;       // abbreviated input parameters
        private String output;      // abbreviated output (first 500 chars)
        private long durationMs;

        public ToolCall() {}
        public ToolCall(String toolName, String input, String output, long durationMs) {
            this.toolName = toolName; this.input = input; this.output = output; this.durationMs = durationMs;
        }

        public String getToolName() { return toolName; }
        public void setToolName(String toolName) { this.toolName = toolName; }
        public String getInput() { return input; }
        public void setInput(String input) { this.input = input; }
        public String getOutput() { return output; }
        public void setOutput(String output) { this.output = output; }
        public long getDurationMs() { return durationMs; }
        public void setDurationMs(long durationMs) { this.durationMs = durationMs; }
    }

    // ── Getters & Setters ──────────────────────────────

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }

    public String getUsageType() { return usageType; }
    public void setUsageType(String usageType) { this.usageType = usageType; }

    public String getTriggeredBy() { return triggeredBy; }
    public void setTriggeredBy(String triggeredBy) { this.triggeredBy = triggeredBy; }

    public String getDocumentId() { return documentId; }
    public void setDocumentId(String documentId) { this.documentId = documentId; }

    public String getDocumentName() { return documentName; }
    public void setDocumentName(String documentName) { this.documentName = documentName; }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public String getPipelineId() { return pipelineId; }
    public void setPipelineId(String pipelineId) { this.pipelineId = pipelineId; }

    public String getPromptBlockId() { return promptBlockId; }
    public void setPromptBlockId(String promptBlockId) { this.promptBlockId = promptBlockId; }

    public Integer getPromptBlockVersion() { return promptBlockVersion; }
    public void setPromptBlockVersion(Integer promptBlockVersion) { this.promptBlockVersion = promptBlockVersion; }

    public String getSystemPrompt() { return systemPrompt; }
    public void setSystemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; }

    public String getUserPrompt() { return userPrompt; }
    public void setUserPrompt(String userPrompt) { this.userPrompt = userPrompt; }

    public List<ToolCall> getToolCalls() { return toolCalls; }
    public void setToolCalls(List<ToolCall> toolCalls) { this.toolCalls = toolCalls; }

    public String getResponse() { return response; }
    public void setResponse(String response) { this.response = response; }

    public String getReasoning() { return reasoning; }
    public void setReasoning(String reasoning) { this.reasoning = reasoning; }

    public Map<String, Object> getResult() { return result; }
    public void setResult(Map<String, Object> result) { this.result = result; }

    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }

    public int getInputTokens() { return inputTokens; }
    public void setInputTokens(int inputTokens) { this.inputTokens = inputTokens; }

    public int getOutputTokens() { return outputTokens; }
    public void setOutputTokens(int outputTokens) { this.outputTokens = outputTokens; }

    public double getEstimatedCost() { return estimatedCost; }
    public void setEstimatedCost(double estimatedCost) { this.estimatedCost = estimatedCost; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public String getOutcome() { return outcome; }
    public void setOutcome(String outcome) { this.outcome = outcome; }

    public String getOverriddenBy() { return overriddenBy; }
    public void setOverriddenBy(String overriddenBy) { this.overriddenBy = overriddenBy; }

    public Instant getOutcomeAt() { return outcomeAt; }
    public void setOutcomeAt(Instant outcomeAt) { this.outcomeAt = outcomeAt; }
}
