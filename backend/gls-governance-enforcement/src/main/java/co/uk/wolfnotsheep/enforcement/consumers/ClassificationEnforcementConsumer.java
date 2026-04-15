package co.uk.wolfnotsheep.enforcement.consumers;

import co.uk.wolfnotsheep.enforcement.config.RabbitMqConfig;
import co.uk.wolfnotsheep.enforcement.services.EnforcementService;
import co.uk.wolfnotsheep.document.events.DocumentClassifiedEvent;
import co.uk.wolfnotsheep.document.models.DocumentModel;
import co.uk.wolfnotsheep.document.models.DocumentStatus;
import co.uk.wolfnotsheep.document.services.DocumentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Consumes document.classified events and triggers governance enforcement.
 *
 * Disabled when the pipeline execution engine is active
 * (pipeline.execution-engine.enabled=true in gls-app-assembly).
 */
@Component
@ConditionalOnProperty(name = "pipeline.execution-engine.enabled", havingValue = "false", matchIfMissing = true)
public class ClassificationEnforcementConsumer {

    private static final Logger log = LoggerFactory.getLogger(ClassificationEnforcementConsumer.class);

    private final EnforcementService enforcementService;
    private final DocumentService documentService;
    private final co.uk.wolfnotsheep.document.services.PipelineStatusNotifier statusNotifier;
    private final co.uk.wolfnotsheep.document.repositories.SystemErrorRepository systemErrorRepo;

    public ClassificationEnforcementConsumer(EnforcementService enforcementService,
                                             DocumentService documentService,
                                             co.uk.wolfnotsheep.document.services.PipelineStatusNotifier statusNotifier,
                                             co.uk.wolfnotsheep.document.repositories.SystemErrorRepository systemErrorRepo) {
        this.enforcementService = enforcementService;
        this.documentService = documentService;
        this.statusNotifier = statusNotifier;
        this.systemErrorRepo = systemErrorRepo;
    }

    @RabbitListener(queues = RabbitMqConfig.QUEUE_CLASSIFIED)
    public void onDocumentClassified(DocumentClassifiedEvent event) {
        log.info("Enforcing governance for document: {} (category: {}, sensitivity: {})",
                event.documentId(), event.categoryName(), event.sensitivityLabel());

        try {
            // Status guard: skip if document was cancelled or is no longer at CLASSIFIED
            DocumentModel current = documentService.getById(event.documentId());
            if (current == null || (current.getStatus() != DocumentStatus.CLASSIFIED
                    && current.getStatus() != DocumentStatus.REVIEW_REQUIRED)) {
                log.info("Skipping enforcement for {} — status is {} (expected CLASSIFIED or REVIEW_REQUIRED)",
                        event.documentId(), current != null ? current.getStatus() : "NOT_FOUND");
                return;
            }

            long enforceStart = System.currentTimeMillis();
            statusNotifier.emitLog(event.documentId(), "", "ENFORCEMENT", "INFO",
                    "Applying governance: " + event.categoryName() + " / " + event.sensitivityLabel(), null);

            DocumentModel doc = enforcementService.enforce(event);

            // Status routing — legacy consumer owns this decision
            if (doc != null) {
                // Force review if PII caused a sensitivity escalation beyond original
                boolean piiEscalated = doc.getSensitivityLabel() != null
                        && event.sensitivityLabel() != null
                        && doc.getSensitivityLabel().ordinal() > event.sensitivityLabel().ordinal();

                if (event.requiresHumanReview() || piiEscalated) {
                    doc.setStatus(DocumentStatus.REVIEW_REQUIRED);
                    log.info("Document {} flagged for human review (confidence: {}, piiEscalated: {})",
                            event.documentId(), event.confidence(), piiEscalated);
                } else {
                    doc.setStatus(DocumentStatus.INBOX);
                }
                documentService.save(doc);
            }

            statusNotifier.emitLog(event.documentId(), "", "ENFORCEMENT", "INFO",
                    "Governance applied", System.currentTimeMillis() - enforceStart);
            log.info("Governance enforcement complete for document: {}", event.documentId());
        } catch (Exception e) {
            log.error("Governance enforcement failed for document {}: {}",
                    event.documentId(), e.getMessage(), e);
            try {
                documentService.setError(event.documentId(), DocumentStatus.ENFORCEMENT_FAILED,
                        "ENFORCEMENT", e.getMessage());
            } catch (Exception inner) {
                log.error("Failed to set error status for {}: {}", event.documentId(), inner.getMessage());
                try {
                    var sysError = co.uk.wolfnotsheep.document.models.SystemError.of(
                            "CRITICAL", "ENFORCEMENT",
                            "Enforcement failed for document " + event.documentId() + ": " + e.getMessage());
                    sysError.setDocumentId(event.documentId());
                    sysError.setService("governance-enforcer");
                    systemErrorRepo.save(sysError);
                } catch (Exception sysErr) {
                    log.error("Failed to persist SystemError for {}: {}", event.documentId(), sysErr.getMessage());
                }
            }
        }
    }
}
