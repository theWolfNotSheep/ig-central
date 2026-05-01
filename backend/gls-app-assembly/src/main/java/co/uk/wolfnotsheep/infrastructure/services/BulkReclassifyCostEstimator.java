package co.uk.wolfnotsheep.infrastructure.services;

import co.uk.wolfnotsheep.document.models.AiUsageLog;
import co.uk.wolfnotsheep.document.repositories.AiUsageLogRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Phase 3 PR4 — cost estimation for bulk reclassify.
 *
 * <p>The app records LLM cost per interaction in {@code AiUsageLog.estimatedCost}
 * (USD), populated by the worker after each call. There's no per-model
 * pricing table baked into the application — we trust the post-hoc
 * recorded value. To estimate the cost of a bulk reclassify we take the
 * average over the most recent {@code CLASSIFY}-usage logs and multiply
 * by the document count.
 *
 * <p>If no recent CLASSIFY usage exists (cold start, no recent classifications)
 * the estimator returns a {@link Estimate#empty()} marker so the UI can
 * show "estimate unavailable" rather than zero.
 */
@Service
public class BulkReclassifyCostEstimator {

    private static final int DEFAULT_SAMPLE_SIZE = 100;
    private static final String CLASSIFY_USAGE_TYPE = "CLASSIFY";

    private final AiUsageLogRepository repo;

    public BulkReclassifyCostEstimator(AiUsageLogRepository repo) {
        this.repo = repo;
    }

    /** Estimate cost for {@code documentCount} documents using the last {@value #DEFAULT_SAMPLE_SIZE} CLASSIFY logs. */
    public Estimate estimateForCount(int documentCount) {
        return estimateForCount(documentCount, DEFAULT_SAMPLE_SIZE);
    }

    public Estimate estimateForCount(int documentCount, int sampleSize) {
        if (documentCount <= 0) return Estimate.empty();
        List<AiUsageLog> sample = repo.findByUsageTypeOrderByTimestampDesc(
                CLASSIFY_USAGE_TYPE, PageRequest.of(0, Math.max(1, sampleSize))).getContent();
        if (sample.isEmpty()) return Estimate.empty();

        double sumCost = 0.0;
        int sumInputTokens = 0;
        int sumOutputTokens = 0;
        int validCostSamples = 0;
        for (AiUsageLog log : sample) {
            if (log.getEstimatedCost() > 0) {
                sumCost += log.getEstimatedCost();
                validCostSamples++;
            }
            sumInputTokens += log.getInputTokens();
            sumOutputTokens += log.getOutputTokens();
        }

        double avgCost = validCostSamples > 0 ? sumCost / validCostSamples : 0.0;
        double avgInputTokens = (double) sumInputTokens / sample.size();
        double avgOutputTokens = (double) sumOutputTokens / sample.size();

        return new Estimate(
                documentCount,
                sample.size(),
                avgCost,
                avgCost * documentCount,
                avgInputTokens,
                avgOutputTokens,
                avgInputTokens * documentCount,
                avgOutputTokens * documentCount);
    }

    public record Estimate(
            int documentCount,
            int sampleSize,
            double averageCostPerDocumentUsd,
            double estimatedTotalCostUsd,
            double averageInputTokens,
            double averageOutputTokens,
            double estimatedTotalInputTokens,
            double estimatedTotalOutputTokens) {

        public static Estimate empty() {
            return new Estimate(0, 0, 0, 0, 0, 0, 0, 0);
        }
    }
}
