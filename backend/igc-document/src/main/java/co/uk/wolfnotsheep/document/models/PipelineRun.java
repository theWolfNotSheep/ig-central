package co.uk.wolfnotsheep.document.models;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tracks a single execution of a pipeline against a document.
 * One PipelineRun per document pipeline execution — retries create new runs.
 */
@Document(collection = "pipeline_runs")
@CompoundIndexes({
        @CompoundIndex(name = "idx_pipeline_run_doc_created", def = "{'documentId': 1, 'createdAt': -1}"),
        @CompoundIndex(name = "idx_pipeline_run_org_status", def = "{'organisationId': 1, 'status': 1}")
})
public class PipelineRun {

    @Id
    private String id;

    @Indexed
    private String documentId;

    @Indexed
    private String organisationId;

    private String pipelineId;
    private int pipelineVersion;

    @Indexed
    private PipelineRunStatus status;

    private String currentNodeKey;

    @Indexed(unique = true)
    private String correlationId;

    /** Compiled node execution order from topological sort */
    private List<String> executionPlan;

    /** Index into executionPlan for the current/next node */
    private int currentNodeIndex;

    /** Accumulated node outputs for downstream node consumption */
    private Map<String, Object> sharedContext = new HashMap<>();

    private Instant startedAt;
    private Instant completedAt;
    private long totalDurationMs;

    private String error;
    private String errorNodeKey;
    private int retryCount;

    private Instant createdAt;
    private Instant updatedAt;

    // ── Constructors ────────────────────────────────────

    public PipelineRun() {
    }

    // ── Getters and setters ─────────────────────────────

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDocumentId() {
        return documentId;
    }

    public void setDocumentId(String documentId) {
        this.documentId = documentId;
    }

    public String getOrganisationId() {
        return organisationId;
    }

    public void setOrganisationId(String organisationId) {
        this.organisationId = organisationId;
    }

    public String getPipelineId() {
        return pipelineId;
    }

    public void setPipelineId(String pipelineId) {
        this.pipelineId = pipelineId;
    }

    public int getPipelineVersion() {
        return pipelineVersion;
    }

    public void setPipelineVersion(int pipelineVersion) {
        this.pipelineVersion = pipelineVersion;
    }

    public PipelineRunStatus getStatus() {
        return status;
    }

    public void setStatus(PipelineRunStatus status) {
        this.status = status;
    }

    public String getCurrentNodeKey() {
        return currentNodeKey;
    }

    public void setCurrentNodeKey(String currentNodeKey) {
        this.currentNodeKey = currentNodeKey;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public List<String> getExecutionPlan() {
        return executionPlan;
    }

    public void setExecutionPlan(List<String> executionPlan) {
        this.executionPlan = executionPlan;
    }

    public int getCurrentNodeIndex() {
        return currentNodeIndex;
    }

    public void setCurrentNodeIndex(int currentNodeIndex) {
        this.currentNodeIndex = currentNodeIndex;
    }

    public Map<String, Object> getSharedContext() {
        return sharedContext;
    }

    public void setSharedContext(Map<String, Object> sharedContext) {
        this.sharedContext = sharedContext;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public long getTotalDurationMs() {
        return totalDurationMs;
    }

    public void setTotalDurationMs(long totalDurationMs) {
        this.totalDurationMs = totalDurationMs;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public String getErrorNodeKey() {
        return errorNodeKey;
    }

    public void setErrorNodeKey(String errorNodeKey) {
        this.errorNodeKey = errorNodeKey;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
