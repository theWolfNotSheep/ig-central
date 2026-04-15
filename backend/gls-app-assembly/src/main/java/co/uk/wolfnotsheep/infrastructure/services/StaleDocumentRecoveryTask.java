package co.uk.wolfnotsheep.infrastructure.services;

import co.uk.wolfnotsheep.document.events.DocumentProcessedEvent;
import co.uk.wolfnotsheep.document.models.DocumentModel;
import co.uk.wolfnotsheep.document.models.DocumentStatus;
import co.uk.wolfnotsheep.document.models.SystemError;
import co.uk.wolfnotsheep.document.repositories.SystemErrorRepository;
import co.uk.wolfnotsheep.document.services.DocumentService;
import co.uk.wolfnotsheep.infrastructure.config.RabbitMqConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Scheduled task that detects and recovers documents stuck in intermediate
 * pipeline states. Runs every 5 minutes.
 *
 * <p>Handles two scenarios:
 * <ul>
 *   <li><b>PROCESSED stuck</b> — document extracted but classification never started
 *       (LLM worker was down or restarted). Re-publishes to classification queue.</li>
 *   <li><b>PROCESSING / CLASSIFYING stuck</b> — document in-flight but worker died.
 *       Resets to UPLOADED and re-queues from the start.</li>
 * </ul>
 */
@Component
public class StaleDocumentRecoveryTask {

    private static final Logger log = LoggerFactory.getLogger(StaleDocumentRecoveryTask.class);

    /** Minutes before a document is considered stale */
    private static final int STALE_THRESHOLD_MINUTES = 10;

    private final MongoTemplate mongoTemplate;
    private final DocumentService documentService;
    private final RabbitTemplate rabbitTemplate;
    private final SystemErrorRepository systemErrorRepo;

    public StaleDocumentRecoveryTask(MongoTemplate mongoTemplate,
                                     DocumentService documentService,
                                     RabbitTemplate rabbitTemplate,
                                     SystemErrorRepository systemErrorRepo) {
        this.mongoTemplate = mongoTemplate;
        this.documentService = documentService;
        this.rabbitTemplate = rabbitTemplate;
        this.systemErrorRepo = systemErrorRepo;
    }

    @Scheduled(fixedDelay = 300_000, initialDelay = 120_000) // every 5 min, start after 2 min
    public void recoverStaleDocuments() {
        try {
            Instant cutoff = Instant.now().minus(STALE_THRESHOLD_MINUTES, ChronoUnit.MINUTES);

            int recoveredProcessed = recoverProcessedDocuments(cutoff);
            int recoveredInFlight = recoverInFlightDocuments(cutoff);

            if (recoveredProcessed + recoveredInFlight > 0) {
                log.info("Stale document recovery: {} PROCESSED re-queued for classification, {} in-flight reset to UPLOADED",
                        recoveredProcessed, recoveredInFlight);
            }
        } catch (Exception e) {
            log.error("Stale document recovery task failed: {}", e.getMessage(), e);
            try {
                SystemError error = SystemError.of("ERROR", "PIPELINE",
                        "Stale document recovery task failed: " + e.getMessage());
                error.setService("api");
                systemErrorRepo.save(error);
            } catch (Exception ex) {
                log.error("Failed to persist recovery task error: {}", ex.getMessage());
            }
        }
    }

    /**
     * Documents stuck at PROCESSED — text extracted but never picked up for classification.
     * Re-publish the DocumentProcessedEvent so the LLM worker can classify them.
     */
    private int recoverProcessedDocuments(Instant cutoff) {
        List<DocumentModel> stuck = mongoTemplate.find(
                Query.query(Criteria.where("status").is(DocumentStatus.PROCESSED)
                        .and("updatedAt").lt(cutoff)),
                DocumentModel.class);

        int count = 0;
        for (DocumentModel doc : stuck) {
            try {
                rabbitTemplate.convertAndSend(
                        "gls.documents",
                        "document.processed",
                        new DocumentProcessedEvent(
                                doc.getId(),
                                doc.getOriginalFileName(),
                                doc.getMimeType(),
                                doc.getFileSizeBytes(),
                                doc.getExtractedText(),
                                doc.getStorageBucket() + "/" + doc.getStorageKey(),
                                doc.getUploadedBy(),
                                Instant.now()
                        )
                );
                count++;
                log.debug("Re-queued PROCESSED document {} for classification", doc.getId());
            } catch (Exception e) {
                log.error("Failed to re-queue PROCESSED document {}: {}", doc.getId(), e.getMessage());
                documentService.setError(doc.getId(), DocumentStatus.CLASSIFICATION_FAILED,
                        "STALE_RECOVERY", "Auto-recovery failed: " + e.getMessage());
            }
        }
        return count;
    }

    /**
     * Documents stuck at PROCESSING or CLASSIFYING — worker died mid-flight.
     * Reset to UPLOADED and re-queue from the start.
     */
    private int recoverInFlightDocuments(Instant cutoff) {
        List<DocumentModel> stuck = mongoTemplate.find(
                Query.query(Criteria.where("status").in(
                                DocumentStatus.PROCESSING, DocumentStatus.CLASSIFYING)
                        .and("updatedAt").lt(cutoff)),
                DocumentModel.class);

        int count = 0;
        for (DocumentModel doc : stuck) {
            try {
                doc.setStatus(DocumentStatus.UPLOADED);
                doc.setRetryCount(doc.getRetryCount() + 1);
                documentService.save(doc);

                rabbitTemplate.convertAndSend(
                        RabbitMqConfig.EXCHANGE,
                        RabbitMqConfig.ROUTING_INGESTED,
                        new co.uk.wolfnotsheep.document.events.DocumentIngestedEvent(
                                doc.getId(), doc.getOriginalFileName(), doc.getMimeType(),
                                doc.getFileSizeBytes(), doc.getStorageBucket(), doc.getStorageKey(),
                                doc.getUploadedBy(), Instant.now()
                        )
                );
                count++;
                log.debug("Reset in-flight document {} to UPLOADED for reprocessing", doc.getId());
            } catch (Exception e) {
                log.error("Failed to recover in-flight document {}: {}", doc.getId(), e.getMessage());
                try {
                    documentService.setError(doc.getId(), DocumentStatus.PROCESSING_FAILED,
                            "STALE_RECOVERY", "Auto-recovery failed: " + e.getMessage());
                } catch (Exception inner) {
                    log.error("Failed to set error status for stale document {}: {}", doc.getId(), inner.getMessage());
                }
            }
        }
        return count;
    }
}
