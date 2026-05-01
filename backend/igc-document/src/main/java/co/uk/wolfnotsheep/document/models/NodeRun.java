package co.uk.wolfnotsheep.document.models;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

/**
 * Tracks a single node execution within a pipeline run.
 * One NodeRun per node per PipelineRun.
 */
@Document(collection = "node_runs")
@CompoundIndexes({
        @CompoundIndex(name = "idx_node_run_pipeline_node", def = "{'pipelineRunId': 1, 'nodeKey': 1}"),
        @CompoundIndex(name = "idx_node_run_status", def = "{'status': 1, 'startedAt': -1}")
})
public class NodeRun {

    @Id
    private String id;

    @Indexed
    private String pipelineRunId;

    private String documentId;

    private String nodeKey;
    private String nodeType;
    private String executionCategory;

    @Indexed
    private NodeRunStatus status;

    /** For async nodes: the job correlation ID */
    @Indexed(unique = true, sparse = true)
    private String jobId;

    /** For safe retries: prevents duplicate processing */
    private String idempotencyKey;

    /** Snapshot of inputs to this node */
    private Map<String, Object> input;

    /** Results produced by this node */
    private Map<String, Object> output;

    private Instant startedAt;
    private Instant completedAt;
    private long durationMs;

    private String error;
    private int retryCount;

    // ── Constructors ────────────────────────────────────

    public NodeRun() {
    }

    // ── Getters and setters ─────────────────────────────

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getPipelineRunId() {
        return pipelineRunId;
    }

    public void setPipelineRunId(String pipelineRunId) {
        this.pipelineRunId = pipelineRunId;
    }

    public String getDocumentId() {
        return documentId;
    }

    public void setDocumentId(String documentId) {
        this.documentId = documentId;
    }

    public String getNodeKey() {
        return nodeKey;
    }

    public void setNodeKey(String nodeKey) {
        this.nodeKey = nodeKey;
    }

    public String getNodeType() {
        return nodeType;
    }

    public void setNodeType(String nodeType) {
        this.nodeType = nodeType;
    }

    public String getExecutionCategory() {
        return executionCategory;
    }

    public void setExecutionCategory(String executionCategory) {
        this.executionCategory = executionCategory;
    }

    public NodeRunStatus getStatus() {
        return status;
    }

    public void setStatus(NodeRunStatus status) {
        this.status = status;
    }

    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public Map<String, Object> getInput() {
        return input;
    }

    public void setInput(Map<String, Object> input) {
        this.input = input;
    }

    public Map<String, Object> getOutput() {
        return output;
    }

    public void setOutput(Map<String, Object> output) {
        this.output = output;
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

    public long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }
}
