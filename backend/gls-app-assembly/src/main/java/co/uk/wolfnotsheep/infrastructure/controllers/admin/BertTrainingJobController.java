package co.uk.wolfnotsheep.infrastructure.controllers.admin;

import co.uk.wolfnotsheep.governance.models.BertTrainingJob;
import co.uk.wolfnotsheep.governance.models.BertTrainingJob.JobStatus;
import co.uk.wolfnotsheep.governance.models.TrainingDataSample;
import co.uk.wolfnotsheep.governance.repositories.BertTrainingJobRepository;
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

    private final BertTrainingJobRepository jobRepo;
    private final TrainingDataSampleRepository sampleRepo;
    private final AppConfigService configService;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String defaultServiceUrl;

    public BertTrainingJobController(BertTrainingJobRepository jobRepo,
                                      TrainingDataSampleRepository sampleRepo,
                                      AppConfigService configService,
                                      @Value("${pipeline.bert.service-url:http://bert-classifier:8000}")
                                      String defaultServiceUrl) {
        this.jobRepo = jobRepo;
        this.sampleRepo = sampleRepo;
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

            // Build label map from unique categories
            var categories = usable.stream()
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

            // Build sample list with label indices — oversample corrections 3x
            var trainingList = new ArrayList<Map<String, Object>>();
            for (var s : usable) {
                Integer labelIdx = catIdToIdx.get(s.getCategoryId());
                if (labelIdx == null) continue;
                var entry = Map.<String, Object>of("text", s.getText(), "label", labelIdx);
                trainingList.add(entry);
                if ("CORRECTION".equals(s.getSource())) {
                    trainingList.add(entry);
                    trainingList.add(entry);
                }
            }

            String modelVersion = "v" + (jobRepo.count() + 1);

            var trainingConfig = new LinkedHashMap<String, Object>();
            trainingConfig.put("base_model", getOrDefault(config, "base_model", "answerdotai/ModernBERT-base"));
            trainingConfig.put("epochs", getOrDefault(config, "epochs", 3));
            trainingConfig.put("batch_size", getOrDefault(config, "batch_size", 16));
            trainingConfig.put("learning_rate", getOrDefault(config, "learning_rate", 2e-5));
            trainingConfig.put("max_length", getOrDefault(config, "max_length", 512));
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
