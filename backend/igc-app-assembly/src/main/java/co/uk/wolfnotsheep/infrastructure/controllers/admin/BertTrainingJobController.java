package co.uk.wolfnotsheep.infrastructure.controllers.admin;

import co.uk.wolfnotsheep.document.services.DocumentService;
import co.uk.wolfnotsheep.governance.models.BertTrainingJob;
import co.uk.wolfnotsheep.governance.models.BertTrainingJob.JobStatus;
import co.uk.wolfnotsheep.governance.models.DocumentClassificationResult;
import co.uk.wolfnotsheep.governance.models.TrainingDataSample;
import co.uk.wolfnotsheep.governance.repositories.BertTrainingJobRepository;
import co.uk.wolfnotsheep.governance.repositories.DocumentClassificationResultRepository;
import co.uk.wolfnotsheep.governance.repositories.TrainingDataSampleRepository;
import co.uk.wolfnotsheep.platform.config.services.AppConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    private static final String OTHER_CATEGORY_ID = "_OTHER";
    private static final String OTHER_CATEGORY_NAME = "_Other";

    private final BertTrainingJobRepository jobRepo;
    private final TrainingDataSampleRepository sampleRepo;
    private final DocumentClassificationResultRepository classificationRepo;
    private final DocumentService documentService;
    private final AppConfigService configService;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String defaultServiceUrl;

    public BertTrainingJobController(BertTrainingJobRepository jobRepo,
                                      TrainingDataSampleRepository sampleRepo,
                                      DocumentClassificationResultRepository classificationRepo,
                                      DocumentService documentService,
                                      AppConfigService configService,
                                      @Value("${pipeline.bert.service-url:http://bert-classifier:8000}")
                                      String defaultServiceUrl) {
        this.jobRepo = jobRepo;
        this.sampleRepo = sampleRepo;
        this.classificationRepo = classificationRepo;
        this.documentService = documentService;
        this.configService = configService;
        this.objectMapper = new ObjectMapper();
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
            // If specific sample IDs provided, use only those; otherwise use all
            @SuppressWarnings("unchecked")
            List<String> selectedIds = config != null && config.containsKey("selectedSampleIds")
                    ? (List<String>) config.get("selectedSampleIds") : null;

            List<TrainingDataSample> samples;
            if (selectedIds != null && !selectedIds.isEmpty()) {
                samples = sampleRepo.findAllById(selectedIds).stream().toList();
            } else {
                samples = sampleRepo.findAll();
            }
            var usable = samples.stream()
                    .filter(s -> s.getText() != null && !s.getText().isBlank())
                    .filter(s -> s.getCategoryId() != null)
                    .toList();

            if (usable.size() < 10) {
                return ResponseEntity.badRequest().body(
                        Map.of("error", "Not enough training data. Need at least 10 samples, have " + usable.size()));
            }

            // Graduation threshold: categories need this many samples to become a real BERT class.
            // Below this, their samples become _OTHER training data instead.
            int minSamplesPerCategory = (int) getOrDefault(config, "min_samples_per_category", 5);

            // Count samples per category
            var categorySampleCounts = usable.stream()
                    .collect(Collectors.groupingBy(TrainingDataSample::getCategoryId, Collectors.counting()));

            // Split into graduated (enough samples) and demoted (become _OTHER)
            var graduatedCategoryIds = new HashSet<String>();
            var demotedSamples = new ArrayList<TrainingDataSample>();
            for (var s : usable) {
                long count = categorySampleCounts.getOrDefault(s.getCategoryId(), 0L);
                if (count >= minSamplesPerCategory) {
                    graduatedCategoryIds.add(s.getCategoryId());
                } else {
                    demotedSamples.add(s);
                }
            }

            log.info("Category graduation (min {} samples): {} categories graduated, {} demoted to _OTHER ({} samples)",
                    minSamplesPerCategory, graduatedCategoryIds.size(),
                    categorySampleCounts.size() - graduatedCategoryIds.size(), demotedSamples.size());

            // Build label map from graduated categories only
            var categories = usable.stream()
                    .filter(s -> graduatedCategoryIds.contains(s.getCategoryId()))
                    .collect(Collectors.toMap(
                            TrainingDataSample::getCategoryId,
                            s -> Map.<String, Object>of(
                                    "category_id", s.getCategoryId(),
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

            // Add _OTHER category — teaches BERT to say "I don't know, send to LLM"
            int otherIdx = idx;
            labelMap.put(String.valueOf(otherIdx), Map.<String, Object>of(
                    "category_id", OTHER_CATEGORY_ID,
                    "category_name", OTHER_CATEGORY_NAME,
                    "sensitivity_label", "INTERNAL"));

            // Build sample list — only graduated categories get real labels
            var trainingList = new ArrayList<Map<String, Object>>();
            for (var s : usable) {
                Integer labelIdx = catIdToIdx.get(s.getCategoryId());
                if (labelIdx != null) {
                    // Graduated category — real label
                    var entry = Map.<String, Object>of("text", s.getText(), "label", labelIdx);
                    trainingList.add(entry);
                    if ("CORRECTION".equals(s.getSource())) {
                        trainingList.add(entry);
                        trainingList.add(entry);
                    }
                } else {
                    // Demoted category — becomes _OTHER training data
                    trainingList.add(Map.<String, Object>of("text", s.getText(), "label", otherIdx));
                }
            }

            // Also gather _OTHER samples from classified documents not in training data at all
            var allTrainingCategoryIds = categorySampleCounts.keySet();
            int externalOtherNeeded = Math.max(trainingList.size() / 4, 5);
            int otherAdded = 0;
            for (var cr : classificationRepo.findAll()) {
                if (otherAdded >= externalOtherNeeded) break;
                if (cr.getCategoryId() == null) continue;
                if (allTrainingCategoryIds.contains(cr.getCategoryId())) continue;
                var doc = documentService.getById(cr.getDocumentId());
                if (doc == null || doc.getExtractedText() == null || doc.getExtractedText().isBlank()) continue;
                String otherText = doc.getExtractedText();
                if (otherText.length() > 2000) otherText = otherText.substring(0, 2000);
                trainingList.add(Map.<String, Object>of("text", otherText, "label", otherIdx));
                otherAdded++;
            }

            long otherTotal = demotedSamples.size() + otherAdded;
            log.info("Training set: {} graduated classes + _OTHER ({} demoted + {} external = {} total _OTHER samples)",
                    categories.size(), demotedSamples.size(), otherAdded, otherTotal);

            String modelVersion = "v" + (jobRepo.count() + 1);

            // Default to fine-tuning from promoted model if one exists (incremental learning)
            String defaultBaseModel = "distilbert-base-uncased";
            var promoted = jobRepo.findByPromotedTrue();
            if (promoted.isPresent() && promoted.get().getModelPath() != null) {
                defaultBaseModel = promoted.get().getModelPath();
                log.info("Incremental training: using promoted model {} as base", defaultBaseModel);
            }

            var trainingConfig = new LinkedHashMap<String, Object>();
            trainingConfig.put("base_model", getOrDefault(config, "base_model", defaultBaseModel));
            trainingConfig.put("epochs", getOrDefault(config, "epochs", 3));
            trainingConfig.put("batch_size", getOrDefault(config, "batch_size", 4));
            trainingConfig.put("gradient_accumulation_steps", getOrDefault(config, "gradient_accumulation_steps", 4));
            trainingConfig.put("learning_rate", getOrDefault(config, "learning_rate", 2e-5));
            trainingConfig.put("max_length", getOrDefault(config, "max_length", 256));
            trainingConfig.put("val_split", getOrDefault(config, "val_split", 0.2));
            trainingConfig.put("model_version", modelVersion);

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

            String serviceUrl = configService.getValue("pipeline.bert.service-url", defaultServiceUrl);
            var payload = new LinkedHashMap<String, Object>();
            payload.put("samples", trainingList);
            payload.put("label_map", labelMap);
            payload.put("config", trainingConfig);
            String body = objectMapper.writeValueAsString(payload);
            log.info("Training payload: {} bytes, starts with: {}", body.length(),
                    body.substring(0, Math.min(200, body.length())));
            final String jobId = job.getId();

            // Run training synchronously in a virtual thread and wait for result
            final String finalJobId = jobId;
            Thread.startVirtualThread(() -> executeTraining(finalJobId, serviceUrl, body));

            return ResponseEntity.ok(Map.of(
                    "jobId", jobId,
                    "modelVersion", modelVersion,
                    "sampleCount", trainingList.size(),
                    "categoryCount", categories.size(),
                    "status", "TRAINING"));

        } catch (Exception e) {
            log.error("Failed to start training: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @SuppressWarnings("unchecked")
    private void executeTraining(String jobId, String serviceUrl, String body) {
        try {
            log.info("Sending training request to {}/train ({} bytes)", serviceUrl, body.length());

            // Force HTTP/1.1 — Uvicorn doesn't support HTTP/2 upgrade
            var trainClient = HttpClient.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1)
                    .connectTimeout(Duration.ofSeconds(30)).build();
            var req = HttpRequest.newBuilder()
                    .uri(URI.create(serviceUrl + "/train"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofMinutes(5))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            var resp = trainClient.send(req, HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() != 200) {
                failJob(jobId, "HTTP " + resp.statusCode() + ": " + resp.body());
                return;
            }

            var startResult = objectMapper.readValue(resp.body(), Map.class);
            String pythonJobId = (String) startResult.get("job_id");
            if (pythonJobId == null) {
                failJob(jobId, "No job_id returned from training service");
                return;
            }

            log.info("Training job {} started on Python side as {}", jobId, pythonJobId);

            // Poll for completion
            while (true) {
                Thread.sleep(5000);

                var pollReq = HttpRequest.newBuilder()
                        .uri(URI.create(serviceUrl + "/train/" + pythonJobId + "/status"))
                        .header("Content-Type", "application/json")
                        .timeout(Duration.ofSeconds(30))
                        .GET().build();

                var pollResp = trainClient.send(pollReq, HttpResponse.BodyHandlers.ofString());
                if (pollResp.statusCode() != 200) continue;

                var status = objectMapper.readValue(pollResp.body(), Map.class);
                String trainStatus = (String) status.get("status");

                if ("COMPLETED".equals(trainStatus)) {
                    var job = jobRepo.findById(jobId).orElse(null);
                    if (job == null) return;
                    job.setStatus(JobStatus.COMPLETED);
                    job.setMetrics(status.containsKey("metrics") ? (Map<String, Object>) status.get("metrics") : Map.of());
                    job.setModelPath((String) status.get("model_path"));
                    job.setCompletedAt(Instant.now());
                    jobRepo.save(job);
                    log.info("Training job {} completed: {}", jobId, status.get("metrics"));
                    return;
                } else if ("FAILED".equals(trainStatus)) {
                    failJob(jobId, (String) status.getOrDefault("error", "Training failed"));
                    return;
                }
                // Still TRAINING — continue polling
            }
        } catch (Exception e) {
            log.error("Training job {} error: {}", jobId, e.getMessage());
            failJob(jobId, e.getMessage());
        }
    }

    private void failJob(String jobId, String error) {
        log.error("Training job {} failed: {}", jobId, error);
        jobRepo.findById(jobId).ifPresent(job -> {
            job.setStatus(JobStatus.FAILED);
            job.setError(error);
            job.setCompletedAt(Instant.now());
            jobRepo.save(job);
        });
    }

    @PostMapping("/{id}/promote")
    public ResponseEntity<?> promote(@PathVariable String id) {
        var job = jobRepo.findById(id).orElse(null);
        if (job == null) return ResponseEntity.notFound().build();
        if (job.getStatus() != JobStatus.COMPLETED) {
            return ResponseEntity.badRequest().body(Map.of("error", "Can only promote COMPLETED jobs"));
        }

        // Gate: reject models below minimum accuracy threshold
        double minAccuracy = 0.50;
        if (job.getMetrics() != null) {
            Object accObj = job.getMetrics().get("accuracy");
            double accuracy = accObj instanceof Number ? ((Number) accObj).doubleValue() : 0;
            if (accuracy < minAccuracy) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", String.format("Model accuracy %.1f%% is below the minimum %.0f%% required for promotion. Add more training data and retrain.",
                                accuracy * 100, minAccuracy * 100),
                        "accuracy", accuracy,
                        "minRequired", minAccuracy));
            }
        }

        try {
            String serviceUrl = configService.getValue("pipeline.bert.service-url", defaultServiceUrl);
            String body = objectMapper.writeValueAsString(Map.of("model_dir", job.getModelPath()));

            var promoteClient = HttpClient.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1)
                    .connectTimeout(Duration.ofSeconds(30)).build();
            var req = HttpRequest.newBuilder()
                    .uri(URI.create(serviceUrl + "/models/activate"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            var resp = promoteClient.send(req, HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() == 200) {
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
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    private static Object getOrDefault(Map<String, Object> config, String key, Object defaultVal) {
        if (config == null) return defaultVal;
        return config.getOrDefault(key, defaultVal);
    }
}
