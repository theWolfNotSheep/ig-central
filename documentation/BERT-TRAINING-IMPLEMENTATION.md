# BERT Training from LLM Output — Full Implementation Guide

This document breaks the BERT training pipeline into 8 small, independent implementation windows. Each window is self-contained with exact file paths, code, and verification steps.

---

## Window 1: Backfill Existing Classifications into Training Data

**Why:** 76 LLM classifications exist with 0.93 avg confidence, but `bert_training_data` has only 2 manual samples because auto-collection was disabled.

**New file:** `backend/gls-app-assembly/src/main/java/co/uk/wolfnotsheep/infrastructure/bootstrap/BertTrainingDataBackfillRunner.java`

```java
package co.uk.wolfnotsheep.infrastructure.bootstrap;

import co.uk.wolfnotsheep.document.services.DocumentService;
import co.uk.wolfnotsheep.governance.models.DocumentClassificationResult;
import co.uk.wolfnotsheep.governance.models.TrainingDataSample;
import co.uk.wolfnotsheep.governance.repositories.DocumentClassificationResultRepository;
import co.uk.wolfnotsheep.governance.repositories.TrainingDataSampleRepository;
import co.uk.wolfnotsheep.platform.config.services.AppConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@Order(210)
public class BertTrainingDataBackfillRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BertTrainingDataBackfillRunner.class);

    private final DocumentClassificationResultRepository classResultRepo;
    private final TrainingDataSampleRepository sampleRepo;
    private final DocumentService documentService;
    private final AppConfigService configService;

    public BertTrainingDataBackfillRunner(
            DocumentClassificationResultRepository classResultRepo,
            TrainingDataSampleRepository sampleRepo,
            DocumentService documentService,
            AppConfigService configService) {
        this.classResultRepo = classResultRepo;
        this.sampleRepo = sampleRepo;
        this.documentService = documentService;
        this.configService = configService;
    }

    @Override
    public void run(ApplicationArguments args) {
        long autoCollected = sampleRepo.countBySource("AUTO_COLLECTED");
        if (autoCollected >= 10) return; // Already populated

        log.info("Backfilling BERT training data from existing LLM classifications...");

        double minConfidence = configService.getValue(
                "bert.training.auto_collect_min_confidence", 0.8);
        int maxLen = configService.getValue(
                "bert.training.max_text_length", 2000);
        int created = 0;

        for (var cr : classResultRepo.findAll()) {
            if (cr.getConfidence() < minConfidence) continue;
            if (cr.getCategoryId() == null || cr.getCategoryName() == null) continue;
            if (sampleRepo.existsBySourceDocumentId(cr.getDocumentId())) continue;

            var doc = documentService.getById(cr.getDocumentId());
            if (doc == null || doc.getExtractedText() == null
                    || doc.getExtractedText().isBlank()) continue;

            String text = doc.getExtractedText();
            if (text.length() > maxLen) text = text.substring(0, maxLen);

            var sample = new TrainingDataSample();
            sample.setText(text);
            sample.setCategoryId(cr.getCategoryId());
            sample.setCategoryName(cr.getCategoryName());
            sample.setSensitivityLabel(cr.getSensitivityLabel() != null
                    ? cr.getSensitivityLabel().name() : "INTERNAL");
            sample.setSource("AUTO_COLLECTED");
            sample.setSourceDocumentId(cr.getDocumentId());
            sample.setConfidence(cr.getConfidence());
            sample.setVerified(false);
            sample.setFileName(doc.getOriginalFileName());
            sample.setCreatedAt(Instant.now());
            sample.setUpdatedAt(Instant.now());

            sampleRepo.save(sample);
            created++;
        }

        // Enable auto-collection for future classifications
        if (created > 0) {
            configService.setValue("bert.training.auto_collect_enabled", "true");
            log.info("Backfilled {} training samples. Auto-collection enabled.", created);
        }
    }
}
```

**Reuses:** `TrainingDataSample`, `TrainingDataSampleRepository`, `DocumentService`, `AppConfigService` — all existing. Same field mapping as `BertTrainingDataCollector.tryCollect()`.

**Verify:**
```bash
# After restart, check MongoDB:
db.bert_training_data.countDocuments()  # Should be ~70+
db.bert_training_data.aggregate([{$group: {_id: "$source", count: {$sum: 1}}}])
db.app_config.findOne({key: "bert.training.auto_collect_enabled"})  # Should be "true"
```

---

## Window 2: Feed Corrections into Training Data

**Why:** Human corrections are the highest-quality training signal but currently only feed the LLM via MCP, not BERT.

**Modify:** `backend/gls-governance/src/main/java/co/uk/wolfnotsheep/governance/services/GovernanceService.java`

The `saveCorrection()` method at line 254 currently just saves the correction. We need to also create/update a training sample.

Add `TrainingDataSampleRepository` and `DocumentService` as constructor dependencies (if not already injected), then modify `saveCorrection()`:

```java
// In GovernanceService — existing method at line 254:

public ClassificationCorrection saveCorrection(ClassificationCorrection correction) {
    correction.setCorrectedAt(Instant.now());
    ClassificationCorrection saved = correctionRepository.save(correction);

    // NEW: Feed correction into BERT training data
    collectCorrectionAsTrainingSample(saved);

    return saved;
}

// NEW method:
private void collectCorrectionAsTrainingSample(ClassificationCorrection correction) {
    try {
        // Only collect category/sensitivity corrections (not PII-only)
        if (correction.getCorrectionType() == null) return;
        String type = correction.getCorrectionType().name();
        if (!type.equals("CATEGORY_CHANGED") && !type.equals("SENSITIVITY_CHANGED")
                && !type.equals("BOTH_CHANGED") && !type.equals("APPROVED_CORRECT")) return;

        String docId = correction.getDocumentId();
        if (docId == null) return;

        var doc = documentService.getById(docId);
        if (doc == null || doc.getExtractedText() == null
                || doc.getExtractedText().isBlank()) return;

        // Determine the correct category (corrected if changed, original if approved)
        String categoryId = correction.getCorrectedCategoryId() != null
                ? correction.getCorrectedCategoryId() : correction.getOriginalCategoryId();
        String categoryName = correction.getCorrectedCategoryName() != null
                ? correction.getCorrectedCategoryName() : correction.getOriginalCategoryName();
        String sensitivity = correction.getCorrectedSensitivity() != null
                ? correction.getCorrectedSensitivity().name()
                : (correction.getOriginalSensitivity() != null
                        ? correction.getOriginalSensitivity().name() : "INTERNAL");

        int maxLen = 2000; // default, no config lookup needed here
        String text = doc.getExtractedText();
        if (text.length() > maxLen) text = text.substring(0, maxLen);

        // Upsert: update existing sample or create new
        var existing = trainingDataSampleRepo.findBySourceDocumentId(docId);
        TrainingDataSample sample;
        if (existing.isPresent()) {
            sample = existing.get();
        } else {
            sample = new TrainingDataSample();
            sample.setSourceDocumentId(docId);
            sample.setText(text);
            sample.setFileName(doc.getOriginalFileName());
            sample.setCreatedAt(Instant.now());
        }

        sample.setCategoryId(categoryId);
        sample.setCategoryName(categoryName);
        sample.setSensitivityLabel(sensitivity);
        sample.setSource("CORRECTION");
        sample.setConfidence(1.0); // human says this is correct
        sample.setVerified(true);
        sample.setUpdatedAt(Instant.now());

        trainingDataSampleRepo.save(sample);
    } catch (Exception e) {
        // Non-fatal — don't break the correction flow
        log.warn("Failed to collect correction as training sample: {}", e.getMessage());
    }
}
```

**Required:** Add `findBySourceDocumentId` to `TrainingDataSampleRepository`:

```java
// In TrainingDataSampleRepository.java — add this method:
Optional<TrainingDataSample> findBySourceDocumentId(String sourceDocumentId);
```

**Check GovernanceService constructor** — it may need `TrainingDataSampleRepository` and `DocumentService` injected. If `DocumentService` is in `gls-document` and `GovernanceService` is in `gls-governance`, you may need to add the dependency. If there's a circular dependency issue, move the training data collection to a separate listener or to the callers in `gls-app-assembly` instead.

**Alternative if circular dependency:** Instead of modifying `GovernanceService.saveCorrection()`, modify the 4 call sites in `gls-app-assembly`:
- `DocumentController.java` line 538
- `ReviewQueueController.java` lines 105, 195, 294

Add a `BertTrainingDataCollector.collectCorrection(correction)` method and call it after `governanceService.saveCorrection(correction)` at each site.

**Verify:**
```bash
# Correct a classification in the UI, then:
db.bert_training_data.find({source: "CORRECTION"}).count()  # Should be 1+
db.bert_training_data.findOne({source: "CORRECTION"})       # Check verified=true
```

---

## Window 3: Training Readiness Endpoint

**Why:** The admin needs to know when there's enough data to train, and which categories are under-represented.

**Modify:** `backend/gls-app-assembly/src/main/java/co/uk/wolfnotsheep/infrastructure/controllers/admin/BertModelController.java`

Add one endpoint:

```java
@GetMapping("/training-readiness")
public ResponseEntity<Map<String, Object>> trainingReadiness() {
    var samples = trainingDataSampleRepo.findAll();

    long total = samples.size();
    long verified = samples.stream().filter(TrainingDataSample::isVerified).count();
    long corrections = samples.stream()
            .filter(s -> "CORRECTION".equals(s.getSource())).count();
    long autoCollected = samples.stream()
            .filter(s -> "AUTO_COLLECTED".equals(s.getSource())).count();

    // Category distribution
    var categoryDist = new LinkedHashMap<String, Long>();
    samples.stream()
        .filter(s -> s.getCategoryName() != null)
        .collect(Collectors.groupingBy(
                TrainingDataSample::getCategoryName, Collectors.counting()))
        .entrySet().stream()
        .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
        .forEach(e -> categoryDist.put(e.getKey(), e.getValue()));

    var ready = new ArrayList<String>();
    var insufficient = new ArrayList<String>();
    categoryDist.forEach((cat, count) -> {
        if (count >= 10) ready.add(cat + " (" + count + ")");
        else insufficient.add(cat + " (" + count + ")");
    });

    String recommendation;
    if (total < 20) {
        recommendation = "NOT_ENOUGH_DATA";
    } else if (ready.isEmpty()) {
        recommendation = "INSUFFICIENT_PER_CATEGORY";
    } else if (!insufficient.isEmpty()) {
        recommendation = "PARTIAL_READY";
    } else {
        recommendation = "READY";
    }

    var result = new LinkedHashMap<String, Object>();
    result.put("totalSamples", total);
    result.put("verified", verified);
    result.put("corrections", corrections);
    result.put("autoCollected", autoCollected);
    result.put("categoryCount", categoryDist.size());
    result.put("categoryDistribution", categoryDist);
    result.put("readyCategories", ready);
    result.put("insufficientCategories", insufficient);
    result.put("recommendation", recommendation);

    return ResponseEntity.ok(result);
}
```

**Required:** Inject `TrainingDataSampleRepository` into `BertModelController` constructor.

**Verify:**
```
GET /api/admin/bert/training-readiness
→ {"totalSamples": 72, "verified": 1, "corrections": 1, "categoryCount": 10, ...}
```

---

## Window 4: BertTrainingJob Model and Repository

**Why:** Need to track training job lifecycle: who started it, when, what data, what metrics, which model version.

**New file:** `backend/gls-governance/src/main/java/co/uk/wolfnotsheep/governance/models/BertTrainingJob.java`

```java
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
    private String modelVersion;       // "v1", "v2", etc.
    private String baseModel;          // "answerdotai/ModernBERT-base"
    private Map<String, Object> trainingConfig;  // epochs, lr, batch_size, etc.
    private int sampleCount;
    private int categoryCount;
    private Map<String, Object> labelMap;
    private Map<String, Object> metrics;  // accuracy, loss, per-category F1
    private String modelPath;          // "/app/models/v2"
    private boolean promoted;
    private String startedBy;
    private Instant startedAt;
    private Instant completedAt;
    private String error;

    // Getters and setters for all fields
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
```

**New file:** `backend/gls-governance/src/main/java/co/uk/wolfnotsheep/governance/repositories/BertTrainingJobRepository.java`

```java
package co.uk.wolfnotsheep.governance.repositories;

import co.uk.wolfnotsheep.governance.models.BertTrainingJob;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;
import java.util.Optional;

public interface BertTrainingJobRepository extends MongoRepository<BertTrainingJob, String> {
    List<BertTrainingJob> findAllByOrderByStartedAtDesc();
    Optional<BertTrainingJob> findByModelVersion(String modelVersion);
    Optional<BertTrainingJob> findByPromotedTrue();
}
```

**Verify:** Compile — `./mvnw compile -DskipTests -pl gls-governance -am -q`

---

## Window 5: Training Job Controller (Java)

**Why:** Admin needs to start training, list jobs, check progress, and promote a model.

**New file:** `backend/gls-app-assembly/src/main/java/co/uk/wolfnotsheep/infrastructure/controllers/admin/BertTrainingJobController.java`

```java
package co.uk.wolfnotsheep.infrastructure.controllers.admin;

import co.uk.wolfnotsheep.governance.models.BertTrainingJob;
import co.uk.wolfnotsheep.governance.models.BertTrainingJob.JobStatus;
import co.uk.wolfnotsheep.governance.models.TrainingDataSample;
import co.uk.wolfnotsheep.governance.repositories.BertTrainingJobRepository;
import co.uk.wolfnotsheep.governance.repositories.TrainingDataSampleRepository;
import co.uk.wolfnotsheep.platform.config.services.AppConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/bert/training-jobs")
public class BertTrainingJobController {

    private static final Logger log = LoggerFactory.getLogger(BertTrainingJobController.class);

    private final BertTrainingJobRepository jobRepo;
    private final TrainingDataSampleRepository sampleRepo;
    private final AppConfigService configService;
    private final HttpClient httpClient;
    private final String defaultServiceUrl;

    public BertTrainingJobController(BertTrainingJobRepository jobRepo,
                                      TrainingDataSampleRepository sampleRepo,
                                      AppConfigService configService,
                                      @Value("${pipeline.bert.service-url:http://bert-classifier:8000}")
                                      String defaultServiceUrl) {
        this.jobRepo = jobRepo;
        this.sampleRepo = sampleRepo;
        this.configService = configService;
        this.defaultServiceUrl = defaultServiceUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10)).build();
    }

    @GetMapping
    public ResponseEntity<List<BertTrainingJob>> listJobs() {
        return ResponseEntity.ok(jobRepo.findAllByOrderByStartedAtDesc());
    }

    @GetMapping("/{id}")
    public ResponseEntity<BertTrainingJob> getJob(@PathVariable String id) {
        return jobRepo.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<?> startTraining(
            @RequestBody(required = false) Map<String, Object> config,
            Authentication auth) {
        try {
            // Build training data from samples
            var samples = sampleRepo.findAll();
            var usable = samples.stream()
                    .filter(s -> s.getText() != null && !s.getText().isBlank())
                    .filter(s -> s.getCategoryId() != null)
                    .toList();

            if (usable.size() < 10) {
                return ResponseEntity.badRequest().body(
                        Map.of("error", "Not enough training data. Need at least 10 samples, have " + usable.size()));
            }

            // Build label map from unique categories
            var categories = usable.stream()
                    .collect(Collectors.toMap(
                            TrainingDataSample::getCategoryId,
                            s -> Map.of("category_id", s.getCategoryId(),
                                    "category_name", s.getCategoryName(),
                                    "sensitivity_label", s.getSensitivityLabel()),
                            (a, b) -> a, LinkedHashMap::new));

            var labelMap = new LinkedHashMap<String, Object>();
            int idx = 0;
            var catIdToIdx = new HashMap<String, Integer>();
            for (var entry : categories.entrySet()) {
                labelMap.put(String.valueOf(idx), entry.getValue());
                catIdToIdx.put(entry.getKey(), idx);
                idx++;
            }

            // Build sample list with label indices
            // Oversample corrections 3x
            var trainingList = new ArrayList<Map<String, Object>>();
            for (var s : usable) {
                Integer labelIdx = catIdToIdx.get(s.getCategoryId());
                if (labelIdx == null) continue;
                var entry = Map.<String, Object>of("text", s.getText(), "label", labelIdx);
                trainingList.add(entry);
                if ("CORRECTION".equals(s.getSource())) {
                    trainingList.add(entry); // 2nd copy
                    trainingList.add(entry); // 3rd copy (3x total)
                }
            }

            // Determine model version
            long jobCount = jobRepo.count();
            String modelVersion = "v" + (jobCount + 1);

            // Default training config
            var trainingConfig = new LinkedHashMap<String, Object>();
            trainingConfig.put("base_model",
                    config != null ? config.getOrDefault("base_model", "answerdotai/ModernBERT-base")
                            : "answerdotai/ModernBERT-base");
            trainingConfig.put("epochs", config != null ? config.getOrDefault("epochs", 3) : 3);
            trainingConfig.put("batch_size", config != null ? config.getOrDefault("batch_size", 16) : 16);
            trainingConfig.put("learning_rate", config != null ? config.getOrDefault("learning_rate", 2e-5) : 2e-5);
            trainingConfig.put("max_length", config != null ? config.getOrDefault("max_length", 512) : 512);
            trainingConfig.put("val_split", config != null ? config.getOrDefault("val_split", 0.2) : 0.2);
            trainingConfig.put("model_version", modelVersion);

            // Create job record
            var job = new BertTrainingJob();
            job.setStatus(JobStatus.TRAINING);
            job.setModelVersion(modelVersion);
            job.setBaseModel((String) trainingConfig.get("base_model"));
            job.setTrainingConfig(trainingConfig);
            job.setSampleCount(trainingList.size());
            job.setCategoryCount(categories.size());
            job.setLabelMap(labelMap);
            job.setStartedBy(auth != null ? auth.getName() : "admin");
            job.setStartedAt(Instant.now());
            job = jobRepo.save(job);

            // Call Python /train endpoint
            String serviceUrl = configService.getValue(
                    "pipeline.bert.service-url", defaultServiceUrl);
            var payload = Map.of(
                    "samples", trainingList,
                    "label_map", labelMap,
                    "config", trainingConfig);

            String body = new tools.jackson.databind.json.JsonMapper()
                    .writeValueAsString(payload);

            final String jobId = job.getId();

            // Fire async — don't block the request
            Thread.startVirtualThread(() -> executeTraining(jobId, serviceUrl, body));

            return ResponseEntity.ok(Map.of(
                    "jobId", jobId,
                    "modelVersion", modelVersion,
                    "sampleCount", trainingList.size(),
                    "categoryCount", categories.size(),
                    "status", "TRAINING"));

        } catch (Exception e) {
            log.error("Failed to start training: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    private void executeTraining(String jobId, String serviceUrl, String body) {
        try {
            var req = HttpRequest.newBuilder()
                    .uri(URI.create(serviceUrl + "/train"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofMinutes(30))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            var resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

            var job = jobRepo.findById(jobId).orElse(null);
            if (job == null) return;

            if (resp.statusCode() == 200) {
                @SuppressWarnings("unchecked")
                var result = new tools.jackson.databind.json.JsonMapper()
                        .readValue(resp.body(), Map.class);
                job.setStatus(JobStatus.COMPLETED);
                job.setMetrics(
                        result.containsKey("metrics") ? (Map<String, Object>) result.get("metrics") : result);
                job.setModelPath((String) result.get("model_path"));
                job.setCompletedAt(Instant.now());
                log.info("Training job {} completed: {}", jobId, result);
            } else {
                job.setStatus(JobStatus.FAILED);
                job.setError("HTTP " + resp.statusCode() + ": " + resp.body());
                job.setCompletedAt(Instant.now());
                log.error("Training job {} failed: {}", jobId, resp.body());
            }
            jobRepo.save(job);
        } catch (Exception e) {
            log.error("Training job {} error: {}", jobId, e.getMessage());
            jobRepo.findById(jobId).ifPresent(job -> {
                job.setStatus(JobStatus.FAILED);
                job.setError(e.getMessage());
                job.setCompletedAt(Instant.now());
                jobRepo.save(job);
            });
        }
    }

    @PostMapping("/{id}/promote")
    public ResponseEntity<?> promote(@PathVariable String id) {
        var job = jobRepo.findById(id).orElse(null);
        if (job == null) return ResponseEntity.notFound().build();
        if (job.getStatus() != JobStatus.COMPLETED) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Can only promote COMPLETED jobs"));
        }

        try {
            // Call Python /models/activate to hot-swap
            String serviceUrl = configService.getValue(
                    "pipeline.bert.service-url", defaultServiceUrl);
            String body = "{\"model_dir\":\"" + job.getModelPath() + "\"}";

            var req = HttpRequest.newBuilder()
                    .uri(URI.create(serviceUrl + "/models/activate"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            var resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() == 200) {
                // Demote any previously promoted job
                jobRepo.findByPromotedTrue().ifPresent(prev -> {
                    prev.setPromoted(false);
                    prev.setStatus(JobStatus.COMPLETED);
                    jobRepo.save(prev);
                });

                job.setPromoted(true);
                job.setStatus(JobStatus.PROMOTED);
                jobRepo.save(job);

                return ResponseEntity.ok(Map.of(
                        "promoted", true,
                        "modelVersion", job.getModelVersion(),
                        "modelPath", job.getModelPath()));
            } else {
                return ResponseEntity.internalServerError()
                        .body(Map.of("error", "Model activation failed: " + resp.body()));
            }
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }
}
```

**Verify:** Compile — `./mvnw compile -DskipTests -pl gls-app-assembly -am -q`

---

## Window 6: Python Fine-Tuning Endpoint

**Why:** The `/train` endpoint is currently a stub. This implements actual ModernBERT fine-tuning.

**Modify:** `bert-classifier/requirements.txt` — add training dependencies:

```
fastapi==0.115.12
uvicorn[standard]==0.34.2
onnxruntime==1.22.0
transformers==4.52.3
tokenizers==0.21.1
numpy>=1.26,<2.0
pydantic==2.11.3
torch>=2.2.0
datasets>=2.18.0
scikit-learn>=1.4.0
optimum[onnxruntime]>=1.17.0
accelerate>=0.27.0
```

**Modify:** `bert-classifier/main.py` — add after the existing `/models` endpoint:

```python
# ── Training ────────────────────────────────────────────────────────────

import uuid
import threading
from collections import Counter

training_jobs: dict[str, dict] = {}


class TrainRequest(BaseModel):
    samples: list[dict]  # [{"text": "...", "label": 0}, ...]
    label_map: dict[str, dict]  # {"0": {"category_id": ..., "category_name": ..., ...}}
    config: dict = {}


@app.post("/train")
async def train(request: TrainRequest):
    """Start a fine-tuning job. Returns immediately with a job ID."""
    job_id = str(uuid.uuid4())[:8]
    training_jobs[job_id] = {
        "status": "TRAINING",
        "progress": 0,
        "started_at": time.time(),
    }

    # Run training in background thread
    thread = threading.Thread(
        target=_run_training,
        args=(job_id, request.samples, request.label_map, request.config),
        daemon=True,
    )
    thread.start()

    return {"job_id": job_id, "status": "TRAINING"}


@app.get("/train/{job_id}/status")
async def train_status(job_id: str):
    """Check training job status."""
    job = training_jobs.get(job_id)
    if not job:
        raise HTTPException(status_code=404, detail="Job not found")
    return job


def _run_training(job_id: str, samples: list, label_map_raw: dict, config: dict):
    """Background fine-tuning job."""
    job = training_jobs[job_id]
    try:
        import torch
        from transformers import (
            AutoTokenizer as TrainTokenizer,
            AutoModelForSequenceClassification,
            TrainingArguments,
            Trainer,
        )
        from sklearn.model_selection import train_test_split
        from sklearn.metrics import accuracy_score, f1_score

        base_model = config.get("base_model", "answerdotai/ModernBERT-base")
        epochs = int(config.get("epochs", 3))
        batch_size = int(config.get("batch_size", 16))
        lr = float(config.get("learning_rate", 2e-5))
        max_len = int(config.get("max_length", 512))
        val_split = float(config.get("val_split", 0.2))
        model_version = config.get("model_version", f"v{job_id}")
        output_dir = f"/app/models/{model_version}"

        num_labels = len(label_map_raw)
        texts = [s["text"] for s in samples]
        labels = [int(s["label"]) for s in samples]

        logger.info("Training %s: %d samples, %d labels, %d epochs",
                     base_model, len(texts), num_labels, epochs)
        job["progress"] = 5

        # Train/val split
        train_texts, val_texts, train_labels, val_labels = train_test_split(
            texts, labels, test_size=val_split, stratify=labels, random_state=42
        )
        job["progress"] = 10

        # Load tokenizer and model
        tok = TrainTokenizer.from_pretrained(base_model)
        model = AutoModelForSequenceClassification.from_pretrained(
            base_model, num_labels=num_labels,
            ignore_mismatched_sizes=True,
        )
        job["progress"] = 20

        # Tokenize
        train_enc = tok(train_texts, truncation=True, padding=True, max_length=max_len)
        val_enc = tok(val_texts, truncation=True, padding=True, max_length=max_len)

        class SimpleDataset(torch.utils.data.Dataset):
            def __init__(self, encodings, labels):
                self.encodings = encodings
                self.labels = labels
            def __len__(self):
                return len(self.labels)
            def __getitem__(self, idx):
                item = {k: torch.tensor(v[idx]) for k, v in self.encodings.items()}
                item["labels"] = torch.tensor(self.labels[idx])
                return item

        train_ds = SimpleDataset(train_enc, train_labels)
        val_ds = SimpleDataset(val_enc, val_labels)
        job["progress"] = 30

        # Training args
        args = TrainingArguments(
            output_dir=output_dir,
            num_train_epochs=epochs,
            per_device_train_batch_size=batch_size,
            per_device_eval_batch_size=batch_size,
            learning_rate=lr,
            eval_strategy="epoch",
            save_strategy="epoch",
            load_best_model_at_end=True,
            metric_for_best_model="accuracy",
            logging_steps=10,
            report_to="none",
            seed=42,
        )

        def compute_metrics(eval_pred):
            preds = eval_pred.predictions.argmax(-1)
            acc = accuracy_score(eval_pred.label_ids, preds)
            f1 = f1_score(eval_pred.label_ids, preds, average="weighted", zero_division=0)
            return {"accuracy": acc, "f1": f1}

        trainer = Trainer(
            model=model,
            args=args,
            train_dataset=train_ds,
            eval_dataset=val_ds,
            compute_metrics=compute_metrics,
        )

        job["progress"] = 40
        trainer.train()
        job["progress"] = 80

        # Evaluate
        eval_result = trainer.evaluate()
        logger.info("Eval results: %s", eval_result)

        # Save model + tokenizer
        trainer.save_model(output_dir)
        tok.save_pretrained(output_dir)

        # Save label map
        label_map_path = Path(output_dir) / "label_map.json"
        with open(label_map_path, "w") as f:
            json.dump(label_map_raw, f, indent=2)

        job["progress"] = 90

        # Export to ONNX
        try:
            from optimum.onnxruntime import ORTModelForSequenceClassification
            ort_model = ORTModelForSequenceClassification.from_pretrained(
                output_dir, export=True
            )
            ort_model.save_pretrained(output_dir)
            job["onnx_exported"] = True
            logger.info("ONNX export complete: %s/model.onnx", output_dir)
        except Exception as e:
            logger.warning("ONNX export failed (model still usable via transformers): %s", e)
            job["onnx_exported"] = False

        job["progress"] = 100
        job["status"] = "COMPLETED"
        job["model_path"] = output_dir
        job["metrics"] = {
            "accuracy": round(eval_result.get("eval_accuracy", 0), 4),
            "f1": round(eval_result.get("eval_f1", 0), 4),
            "loss": round(eval_result.get("eval_loss", 0), 4),
            "train_samples": len(train_texts),
            "val_samples": len(val_texts),
            "epochs": epochs,
            "label_distribution": dict(Counter(labels)),
        }
        job["completed_at"] = time.time()
        job["duration_seconds"] = int(job["completed_at"] - job["started_at"])

        logger.info("Training complete: %s — accuracy=%.3f, f1=%.3f",
                     model_version, job["metrics"]["accuracy"], job["metrics"]["f1"])

    except Exception as e:
        logger.error("Training failed: %s", e, exc_info=True)
        job["status"] = "FAILED"
        job["error"] = str(e)
        job["completed_at"] = time.time()
```

**Verify:** Rebuild the Python container — `docker compose up --build -d bert-classifier`

---

## Window 7: Model Hot-Swap Endpoint (Python)

**Why:** After promoting a trained model, the sidecar needs to load the new weights without restarting.

**Add to:** `bert-classifier/main.py` — after the training code:

```python
class ActivateRequest(BaseModel):
    model_dir: str


@app.post("/models/activate")
async def activate_model(request: ActivateRequest):
    """Hot-swap the active model to a new directory."""
    global tokenizer, onnx_session, hf_pipeline, label_map, MODEL_DIR

    model_path = Path(request.model_dir)
    if not model_path.exists():
        raise HTTPException(status_code=404,
                            detail=f"Model directory not found: {request.model_dir}")

    # Load new label map
    lm_path = model_path / "label_map.json"
    if lm_path.exists():
        with open(lm_path) as f:
            label_map = {int(k): v for k, v in json.load(f).items()}
    else:
        raise HTTPException(status_code=400,
                            detail="No label_map.json in model directory")

    # Reset current model state
    onnx_session = None
    hf_pipeline = None

    # Load new tokenizer
    from transformers import AutoTokenizer as SwapTokenizer
    tokenizer = SwapTokenizer.from_pretrained(str(model_path))

    # Try ONNX first
    onnx_path = model_path / "model.onnx"
    if USE_ONNX and onnx_path.exists():
        import onnxruntime as ort
        onnx_session = ort.InferenceSession(
            str(onnx_path), providers=["CPUExecutionProvider"])
        MODEL_DIR = str(model_path)
        logger.info("Hot-swapped to ONNX model at %s (%d labels)",
                     model_path, len(label_map))
        return {"status": "activated", "mode": "onnx",
                "model_dir": str(model_path), "label_count": len(label_map)}

    # Fall back to HuggingFace
    hf_config = model_path / "config.json"
    if hf_config.exists():
        from transformers import pipeline
        hf_pipeline = pipeline("text-classification",
                                model=str(model_path), tokenizer=tokenizer)
        MODEL_DIR = str(model_path)
        logger.info("Hot-swapped to HuggingFace model at %s (%d labels)",
                     model_path, len(label_map))
        return {"status": "activated", "mode": "transformers",
                "model_dir": str(model_path), "label_count": len(label_map)}

    raise HTTPException(status_code=400,
                        detail="No model.onnx or config.json found in directory")
```

**Verify:** `curl -X POST http://localhost:8000/models/activate -H 'Content-Type: application/json' -d '{"model_dir": "/app/models/v1"}'`

---

## Window 8: Dockerfile Update for Training Dependencies

**Why:** The current Dockerfile only installs inference dependencies. Training needs PyTorch, datasets, scikit-learn, optimum.

**Modify:** `bert-classifier/Dockerfile`:

```dockerfile
FROM python:3.12-slim

WORKDIR /app

# Install system dependencies for torch
RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential && rm -rf /var/lib/apt/lists/*

# Install Python dependencies
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy application
COPY . .

# Download default tokenizer on build
RUN python -c "from transformers import AutoTokenizer; AutoTokenizer.from_pretrained('distilbert-base-uncased')"

EXPOSE 8000

CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]
```

**Note:** The image will be larger (~2-3GB with PyTorch). For production, consider a multi-stage build or separate training and inference containers. For dev, this is fine.

**Verify:** `docker compose up --build -d bert-classifier && docker logs -f gls-bert-classifier`

---

## End-to-End Flow After All Windows

```
Document uploaded
    ↓
Pipeline: text extraction → PII scan → BERT accelerator
    ↓ (BERT miss — no trained model yet, or low confidence)
LLM classifies via MCP tools
    ↓
save_classification_result (MCP tool)
    ↓
BertTrainingDataCollector.tryCollect() → TrainingDataSample created
    ↓
Human corrects classification (optional)
    ↓
GovernanceService.saveCorrection() → TrainingDataSample updated (verified=true)
    ↓
Admin checks /api/admin/bert/training-readiness → "READY"
    ↓
Admin triggers POST /api/admin/bert/training-jobs
    ↓
Java exports samples + label_map → Python /train
    ↓
ModernBERT fine-tuned → ONNX export → /app/models/v1/
    ↓
Admin promotes → POST /training-jobs/{id}/promote
    ↓
Python /models/activate → hot-swap model
    ↓
Next document: BERT accelerator hits → LLM skipped
    ↓
Cost drops, latency drops, LLM only handles novel/low-confidence docs
```

## Implementation Order

| Order | Window | Dependencies | Estimated Size |
|-------|--------|-------------|----------------|
| 1 | Window 1: Backfill runner | None | ~80 lines Java |
| 2 | Window 2: Corrections → training | Window 1 (model exists) | ~50 lines Java |
| 3 | Window 3: Readiness endpoint | Window 1 (data exists) | ~40 lines Java |
| 4 | Window 4: Training job model | None | ~60 lines Java |
| 5 | Window 8: Dockerfile + deps | None | ~10 lines config |
| 6 | Window 6: Python /train | Window 8 (deps installed) | ~120 lines Python |
| 7 | Window 7: Python hot-swap | None | ~40 lines Python |
| 8 | Window 5: Training job controller | Windows 4, 6, 7 | ~150 lines Java |
