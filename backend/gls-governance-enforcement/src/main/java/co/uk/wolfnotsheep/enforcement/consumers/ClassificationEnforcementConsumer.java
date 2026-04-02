package co.uk.wolfnotsheep.enforcement.consumers;

import co.uk.wolfnotsheep.enforcement.config.RabbitMqConfig;
import co.uk.wolfnotsheep.enforcement.services.EnforcementService;
import co.uk.wolfnotsheep.document.events.DocumentClassifiedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Consumes document.classified events and triggers governance enforcement:
 * - Apply retention schedule (set expiry date)
 * - Migrate to correct storage tier based on sensitivity
 * - Apply policy enforcement actions
 * - Flag for human review if needed
 */
@Component
public class ClassificationEnforcementConsumer {

    private static final Logger log = LoggerFactory.getLogger(ClassificationEnforcementConsumer.class);

    private final EnforcementService enforcementService;

    public ClassificationEnforcementConsumer(EnforcementService enforcementService) {
        this.enforcementService = enforcementService;
    }

    @RabbitListener(queues = RabbitMqConfig.QUEUE_CLASSIFIED)
    public void onDocumentClassified(DocumentClassifiedEvent event) {
        log.info("Enforcing governance for document: {} (category: {}, sensitivity: {})",
                event.documentId(), event.categoryName(), event.sensitivityLabel());

        try {
            enforcementService.enforce(event);
            log.info("Governance enforcement complete for document: {}", event.documentId());
        } catch (Exception e) {
            log.error("Governance enforcement failed for document {}: {}",
                    event.documentId(), e.getMessage(), e);
            throw new RuntimeException("Enforcement failed", e);
        }
    }
}
