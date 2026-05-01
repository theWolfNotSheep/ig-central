package co.uk.wolfnotsheep.infrastructure.services;

import co.uk.wolfnotsheep.governance.models.BertTrainingJob;
import co.uk.wolfnotsheep.governance.repositories.BertTrainingJobRepository;
import co.uk.wolfnotsheep.governance.repositories.TrainingDataSampleRepository;
import co.uk.wolfnotsheep.platform.config.services.AppConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

/**
 * Daily check: evaluates whether BERT retraining is warranted based on
 * new corrections, sample growth, and model staleness. Logs recommendations
 * and exposes status via an endpoint (BertModelController).
 */
@Service
public class BertRetrainingAdvisor {

    private static final Logger log = LoggerFactory.getLogger(BertRetrainingAdvisor.class);

    private final TrainingDataSampleRepository sampleRepo;
    private final BertTrainingJobRepository jobRepo;
    private final AppConfigService configService;

    private volatile Map<String, Object> lastAdvice = Map.of();

    public BertRetrainingAdvisor(TrainingDataSampleRepository sampleRepo,
                                  BertTrainingJobRepository jobRepo,
                                  AppConfigService configService) {
        this.sampleRepo = sampleRepo;
        this.jobRepo = jobRepo;
        this.configService = configService;
    }

    /**
     * Runs daily at 06:00. Checks whether retraining would improve the model.
     */
    @Scheduled(cron = "0 0 6 * * *")
    public void evaluate() {
        try {
            lastAdvice = buildAdvice();
            String recommendation = (String) lastAdvice.get("recommendation");
            List<?> reasons = (List<?>) lastAdvice.get("reasons");

            if ("RETRAIN_RECOMMENDED".equals(recommendation)) {
                log.warn("BERT retraining recommended: {}", reasons);
            } else {
                log.info("BERT retraining check: {} — {}", recommendation, reasons);
            }
        } catch (Exception e) {
            log.warn("Retraining advisor check failed: {}", e.getMessage());
        }
    }

    public Map<String, Object> getLastAdvice() {
        if (lastAdvice.isEmpty()) {
            lastAdvice = buildAdvice();
        }
        return lastAdvice;
    }

    private Map<String, Object> buildAdvice() {
        var result = new LinkedHashMap<String, Object>();
        var reasons = new ArrayList<String>();
        boolean shouldRetrain = false;

        long totalSamples = sampleRepo.count();
        long corrections = sampleRepo.countBySource("CORRECTION");

        // Find the most recent completed/promoted training job
        var lastJob = jobRepo.findAllByOrderByStartedAtDesc().stream()
                .filter(j -> j.getStatus() == BertTrainingJob.JobStatus.COMPLETED
                        || j.getStatus() == BertTrainingJob.JobStatus.PROMOTED)
                .findFirst();

        int samplesAtLastTraining = lastJob.map(BertTrainingJob::getSampleCount).orElse(0);
        Instant lastTrainedAt = lastJob.map(BertTrainingJob::getStartedAt).orElse(null);

        // Check 1: New samples since last training
        long newSamples = totalSamples - samplesAtLastTraining;
        if (newSamples >= 20) {
            reasons.add(String.format("%d new samples since last training", newSamples));
            shouldRetrain = true;
        }

        // Check 2: New corrections (high-value signal)
        long correctionsAtLastTraining = lastJob
                .map(j -> j.getMetrics() != null ? ((Number) j.getMetrics().getOrDefault("correction_count", 0L)).longValue() : 0L)
                .orElse(0L);
        long newCorrections = corrections - correctionsAtLastTraining;
        if (newCorrections >= 10) {
            reasons.add(String.format("%d new human corrections — high-value training signal", newCorrections));
            shouldRetrain = true;
        }

        // Check 3: Never trained
        if (lastJob.isEmpty() && totalSamples >= 20) {
            reasons.add(String.format("No model trained yet but %d samples available", totalSamples));
            shouldRetrain = true;
        }

        // Check 4: Model staleness (>14 days since last training)
        if (lastTrainedAt != null && lastTrainedAt.isBefore(Instant.now().minusSeconds(14 * 86400))) {
            reasons.add("Last training was over 14 days ago");
            if (newSamples >= 5) shouldRetrain = true;
        }

        if (reasons.isEmpty()) {
            reasons.add("Model is up to date");
        }

        result.put("recommendation", shouldRetrain ? "RETRAIN_RECOMMENDED" : "UP_TO_DATE");
        result.put("reasons", reasons);
        result.put("totalSamples", totalSamples);
        result.put("corrections", corrections);
        result.put("newSamplesSinceLastTraining", newSamples);
        result.put("lastTrainedAt", lastTrainedAt != null ? lastTrainedAt.toString() : null);
        result.put("checkedAt", Instant.now().toString());

        return result;
    }
}
