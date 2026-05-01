package co.uk.wolfnotsheep.infrastructure.services;

import co.uk.wolfnotsheep.document.models.DocumentStatus;
import co.uk.wolfnotsheep.document.repositories.DocumentRepository;
import co.uk.wolfnotsheep.platform.config.services.AppConfigService;
import org.springframework.stereotype.Service;

/**
 * Enforces pipeline throughput limits based on admin-configured thresholds.
 * Prevents overwhelming the LLM by rejecting new submissions when too many
 * documents are already in-flight.
 */
@Service
public class PipelineThrottleService {

    private static final int DEFAULT_MAX_IN_FLIGHT = 50;
    private static final int DEFAULT_MAX_BATCH_SIZE = 20;

    private final DocumentRepository documentRepo;
    private final AppConfigService configService;

    public PipelineThrottleService(DocumentRepository documentRepo,
                                    AppConfigService configService) {
        this.documentRepo = documentRepo;
        this.configService = configService;
    }

    public int getMaxInFlight() {
        Object val = configService.getValue("pipeline.throttle.max_in_flight", DEFAULT_MAX_IN_FLIGHT);
        return val instanceof Number n ? n.intValue() : DEFAULT_MAX_IN_FLIGHT;
    }

    public int getMaxBatchSize() {
        Object val = configService.getValue("pipeline.throttle.max_batch_size", DEFAULT_MAX_BATCH_SIZE);
        return val instanceof Number n ? n.intValue() : DEFAULT_MAX_BATCH_SIZE;
    }

    public long getInFlightCount() {
        return documentRepo.countByStatus(DocumentStatus.PROCESSING)
                + documentRepo.countByStatus(DocumentStatus.PROCESSED)
                + documentRepo.countByStatus(DocumentStatus.CLASSIFYING);
    }

    public int getAvailableSlots() {
        long inFlight = getInFlightCount();
        int max = getMaxInFlight();
        return Math.max(0, (int) (max - inFlight));
    }

    /**
     * Check if a batch of the given size can be accepted.
     * Returns null if OK, or an error message if throttled.
     */
    public String checkThrottle(int batchSize) {
        int maxBatch = getMaxBatchSize();
        if (batchSize > maxBatch) {
            return "Batch size " + batchSize + " exceeds maximum of " + maxBatch +
                    ". Submit fewer documents at a time.";
        }

        int available = getAvailableSlots();
        if (available <= 0) {
            return "Pipeline is at capacity (" + getMaxInFlight() +
                    " documents in-flight). Wait for current documents to finish processing.";
        }

        if (batchSize > available) {
            return "Only " + available + " slots available (pipeline limit: " + getMaxInFlight() +
                    "). Reduce batch to " + available + " documents or wait.";
        }

        return null; // OK
    }

    /**
     * Return current throttle status for the monitoring/settings UI.
     */
    public java.util.Map<String, Object> getStatus() {
        long inFlight = getInFlightCount();
        int max = getMaxInFlight();
        return java.util.Map.of(
                "inFlight", inFlight,
                "maxInFlight", max,
                "available", Math.max(0, max - inFlight),
                "maxBatchSize", getMaxBatchSize(),
                "throttled", inFlight >= max
        );
    }
}
