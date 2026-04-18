package co.uk.wolfnotsheep.governance.models;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

@Document(collection = "bert_training_jobs")
public class BertTrainingJob {

    public enum JobStatus { PENDING, TRAINING, COMPLETED, FAILED, PROMOTED }

    @Id private String id;
    private JobStatus status;
    private String modelVersion;
    private String baseModel;
    private Map<String, Object> trainingConfig;
    private int sampleCount;
    private int categoryCount;
    private Map<String, Object> labelMap;
    private Map<String, Object> metrics;
    private String modelPath;
    private boolean promoted;
    private String startedBy;
    private Instant startedAt;
    private Instant completedAt;
    private String error;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public JobStatus getStatus() { return status; }
    public void setStatus(JobStatus status) { this.status = status; }
    public String getModelVersion() { return modelVersion; }
    public void setModelVersion(String modelVersion) { this.modelVersion = modelVersion; }
    public String getBaseModel() { return baseModel; }
    public void setBaseModel(String baseModel) { this.baseModel = baseModel; }
    public Map<String, Object> getTrainingConfig() { return trainingConfig; }
    public void setTrainingConfig(Map<String, Object> trainingConfig) { this.trainingConfig = trainingConfig; }
    public int getSampleCount() { return sampleCount; }
    public void setSampleCount(int sampleCount) { this.sampleCount = sampleCount; }
    public int getCategoryCount() { return categoryCount; }
    public void setCategoryCount(int categoryCount) { this.categoryCount = categoryCount; }
    public Map<String, Object> getLabelMap() { return labelMap; }
    public void setLabelMap(Map<String, Object> labelMap) { this.labelMap = labelMap; }
    public Map<String, Object> getMetrics() { return metrics; }
    public void setMetrics(Map<String, Object> metrics) { this.metrics = metrics; }
    public String getModelPath() { return modelPath; }
    public void setModelPath(String modelPath) { this.modelPath = modelPath; }
    public boolean isPromoted() { return promoted; }
    public void setPromoted(boolean promoted) { this.promoted = promoted; }
    public String getStartedBy() { return startedBy; }
    public void setStartedBy(String startedBy) { this.startedBy = startedBy; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
}
