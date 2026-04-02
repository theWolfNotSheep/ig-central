package co.uk.wolfnotsheep.docprocessing.extraction;

import co.uk.wolfnotsheep.docprocessing.config.RabbitMqConfig;
import co.uk.wolfnotsheep.document.events.DocumentIngestedEvent;
import co.uk.wolfnotsheep.document.models.DocumentModel;
import co.uk.wolfnotsheep.document.models.DocumentStatus;
import co.uk.wolfnotsheep.document.services.DocumentService;
import co.uk.wolfnotsheep.document.services.ObjectStorageService;
import co.uk.wolfnotsheep.document.events.DocumentProcessedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.Instant;

/**
 * Consumes document.ingested events, extracts text using Tika,
 * updates the document record, and publishes document.processed.
 */
@Service
public class DocumentProcessingPipeline {

    private static final Logger log = LoggerFactory.getLogger(DocumentProcessingPipeline.class);

    private final TextExtractionService textExtractionService;
    private final DocumentService documentService;
    private final ObjectStorageService objectStorage;
    private final RabbitTemplate rabbitTemplate;

    public DocumentProcessingPipeline(TextExtractionService textExtractionService,
                                      DocumentService documentService,
                                      ObjectStorageService objectStorage,
                                      RabbitTemplate rabbitTemplate) {
        this.textExtractionService = textExtractionService;
        this.documentService = documentService;
        this.objectStorage = objectStorage;
        this.rabbitTemplate = rabbitTemplate;
    }

    @RabbitListener(queues = RabbitMqConfig.QUEUE_INGESTED)
    public void onDocumentIngested(DocumentIngestedEvent event) {
        log.info("Processing document: {} ({})", event.documentId(), event.fileName());

        try {
            // Update status to PROCESSING
            documentService.updateStatus(event.documentId(), DocumentStatus.PROCESSING, "SYSTEM");

            // Download from object storage and extract text
            InputStream fileStream = objectStorage.download(event.storageBucket(), event.storageKey());
            TextExtractionService.ExtractionResult result =
                    textExtractionService.extract(fileStream, event.fileName());

            // Update document with extracted text
            DocumentModel doc = documentService.getById(event.documentId());
            doc.setExtractedText(result.text());
            doc.setPageCount(result.pageCount());
            documentService.save(doc);

            // Update status to PROCESSED
            documentService.updateStatus(event.documentId(), DocumentStatus.PROCESSED, "SYSTEM");

            // Publish document.processed event for LLM classification
            var processedEvent = new DocumentProcessedEvent(
                    event.documentId(),
                    event.fileName(),
                    event.mimeType(),
                    event.fileSizeBytes(),
                    result.text(),
                    event.storageBucket() + "/" + event.storageKey(),
                    event.uploadedBy(),
                    Instant.now()
            );

            rabbitTemplate.convertAndSend(
                    RabbitMqConfig.EXCHANGE,
                    RabbitMqConfig.ROUTING_PROCESSED,
                    processedEvent
            );

            log.info("Document {} processed successfully. Extracted {} chars, {} pages.",
                    event.documentId(), result.text().length(), result.pageCount());

        } catch (Exception e) {
            log.error("Document processing failed for {}: {}", event.documentId(), e.getMessage(), e);
            throw new RuntimeException("Processing failed", e);
        }
    }
}
