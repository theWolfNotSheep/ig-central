package co.uk.wolfnotsheep.docprocessing.extraction;

import co.uk.wolfnotsheep.docprocessing.config.RabbitMqConfig;
import co.uk.wolfnotsheep.document.events.DocumentIngestedEvent;
import co.uk.wolfnotsheep.document.models.DocumentModel;
import co.uk.wolfnotsheep.document.models.DocumentStatus;
import co.uk.wolfnotsheep.document.models.PiiEntity;
import co.uk.wolfnotsheep.document.services.DocumentService;
import co.uk.wolfnotsheep.document.services.ObjectStorageService;
import co.uk.wolfnotsheep.document.services.PiiPatternScanner;
import co.uk.wolfnotsheep.governance.models.ClassificationCorrection;
import co.uk.wolfnotsheep.governance.models.PipelineBlock;
import co.uk.wolfnotsheep.governance.repositories.PipelineBlockRepository;
import co.uk.wolfnotsheep.governance.services.GovernanceService;
import co.uk.wolfnotsheep.document.events.DocumentProcessedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Consumes document.ingested events, extracts text using Tika,
 * updates the document record, and publishes document.processed.
 *
 * Disabled when the pipeline execution engine is active
 * (pipeline.execution-engine.enabled=true in gls-app-assembly).
 */
@Service
@ConditionalOnProperty(name = "pipeline.execution-engine.enabled", havingValue = "false", matchIfMissing = true)
public class DocumentProcessingPipeline {

    private static final Logger log = LoggerFactory.getLogger(DocumentProcessingPipeline.class);

    private final TextExtractionService textExtractionService;
    private final DocumentService documentService;
    private final ObjectStorageService objectStorage;
    private final RabbitTemplate rabbitTemplate;
    private final PiiPatternScanner piiScanner;
    private final GovernanceService governanceService;
    private final PipelineBlockRepository blockRepo;
    private final co.uk.wolfnotsheep.document.services.PipelineStatusNotifier statusNotifier;

    public DocumentProcessingPipeline(TextExtractionService textExtractionService,
                                      DocumentService documentService,
                                      ObjectStorageService objectStorage,
                                      RabbitTemplate rabbitTemplate,
                                      PiiPatternScanner piiScanner,
                                      GovernanceService governanceService,
                                      PipelineBlockRepository blockRepo,
                                      co.uk.wolfnotsheep.document.services.PipelineStatusNotifier statusNotifier) {
        this.textExtractionService = textExtractionService;
        this.documentService = documentService;
        this.objectStorage = objectStorage;
        this.rabbitTemplate = rabbitTemplate;
        this.piiScanner = piiScanner;
        this.governanceService = governanceService;
        this.blockRepo = blockRepo;
        this.statusNotifier = statusNotifier;
    }

    @RabbitListener(queues = RabbitMqConfig.QUEUE_INGESTED)
    public void onDocumentIngested(DocumentIngestedEvent event) {
        log.info("Processing document: {} ({})", event.documentId(), event.fileName());

        try {
            // Status guard: skip if document was cancelled or is in an unexpected state
            DocumentModel current = documentService.getById(event.documentId());
            if (current == null) {
                log.warn("Skipping document {} — not found in database", event.documentId());
                return;
            }
            // If cancelled after this message was queued, skip
            if (current.getCancelledAt() != null && event.ingestedAt() != null
                    && current.getCancelledAt().isAfter(event.ingestedAt())) {
                log.info("Skipping document {} — cancelled at {} (message queued at {})",
                        event.documentId(), current.getCancelledAt(), event.ingestedAt());
                return;
            }
            if (current.getStatus() != DocumentStatus.UPLOADED) {
                log.info("Skipping document {} — status is {} (expected UPLOADED)",
                        event.documentId(), current.getStatus());
                return;
            }

            // Update status to PROCESSING
            documentService.updateStatus(event.documentId(), DocumentStatus.PROCESSING, "SYSTEM");
            long extractStart = System.currentTimeMillis();
            statusNotifier.emitLog(event.documentId(), event.fileName(), "EXTRACTION", "INFO",
                    "Starting text extraction", null);

            // Load EXTRACTOR block config (if available)
            int maxTextLength = 500_000;
            boolean extractDublinCore = true;
            boolean extractMetadata = true;
            List<PipelineBlock> extractorBlocks = blockRepo.findByTypeAndActiveTrueOrderByNameAsc(
                    PipelineBlock.BlockType.EXTRACTOR);
            if (!extractorBlocks.isEmpty()) {
                Map<String, Object> blockContent = extractorBlocks.getFirst().getActiveContent();
                if (blockContent != null) {
                    maxTextLength = toInt(blockContent.get("maxTextLength"), maxTextLength);
                    extractDublinCore = toBool(blockContent.get("extractDublinCore"), true);
                    extractMetadata = toBool(blockContent.get("extractMetadata"), true);
                }
            }

            // Download from object storage and extract text
            InputStream fileStream = objectStorage.download(event.storageBucket(), event.storageKey());
            TextExtractionService.ExtractionResult result =
                    textExtractionService.extract(fileStream, event.fileName(),
                            maxTextLength, extractDublinCore, extractMetadata);
            long extractMs = System.currentTimeMillis() - extractStart;
            statusNotifier.emitLog(event.documentId(), event.fileName(), "EXTRACTION", "INFO",
                    "Extracted " + result.text().length() + " chars, " + result.pageCount() + " pages", extractMs);

            // Update document with extracted text and Dublin Core metadata
            // Note: Tika file metadata goes into dublinCore, NOT extractedMetadata.
            // extractedMetadata is reserved for LLM-extracted schema fields (invoice number, parties, etc.)
            DocumentModel doc = documentService.getById(event.documentId());
            doc.setExtractedText(result.text());
            doc.setPageCount(result.pageCount());
            doc.setDublinCore(result.dublinCore());
            // Don't overwrite extractedMetadata — it's populated later by the LLM via enforcement

            // Tier 1 PII scan (pattern-based, zero cost)
            // Build dismissal context from: (1) existing findings on doc, (2) corrections DB
            java.util.List<PiiEntity> previousFindings = new java.util.ArrayList<>();
            if (doc.getPiiFindings() != null) {
                previousFindings.addAll(doc.getPiiFindings());
            }
            // Also build synthetic dismissed entities from the corrections collection
            // so dismissals survive even if the document's findings were overwritten
            for (ClassificationCorrection c : governanceService.getCorrectionsByDocumentId(event.documentId())) {
                if (c.getCorrectionType() == ClassificationCorrection.CorrectionType.PII_DISMISSED
                        && c.getPiiCorrections() != null) {
                    for (var pc : c.getPiiCorrections()) {
                        PiiEntity synthetic = new PiiEntity();
                        synthetic.setType(pc.type());
                        synthetic.setMatchedText(pc.context()); // context holds redactedText
                        synthetic.setDismissed(true);
                        synthetic.setDismissedBy(c.getCorrectedBy());
                        synthetic.setDismissalReason(c.getReason());
                        previousFindings.add(synthetic);
                    }
                }
            }
            // Also check global dismissals (from any document) for the same PII type patterns
            for (ClassificationCorrection c : governanceService.getPiiDismissals()) {
                if (c.getPiiCorrections() != null) {
                    for (var pc : c.getPiiCorrections()) {
                        PiiEntity synthetic = new PiiEntity();
                        synthetic.setType(pc.type());
                        synthetic.setMatchedText(pc.context());
                        synthetic.setDismissed(true);
                        synthetic.setDismissedBy(c.getCorrectedBy());
                        synthetic.setDismissalReason(c.getReason());
                        previousFindings.add(synthetic);
                    }
                }
            }
            long piiStart = System.currentTimeMillis();
            java.util.List<PiiEntity> piiFindings = piiScanner.scan(result.text(), previousFindings);
            doc.setPiiFindings(piiFindings);
            doc.setPiiScannedAt(java.time.Instant.now());
            long activePii = piiFindings.stream().filter(p -> !p.isDismissed()).count();
            doc.setPiiStatus(activePii > 0 ? "DETECTED" : "NONE");
            long piiMs = System.currentTimeMillis() - piiStart;

            documentService.save(doc);

            statusNotifier.emitLog(event.documentId(), event.fileName(), "PII_SCAN", "INFO",
                    activePii > 0 ? activePii + " PII entities detected" : "No PII detected", piiMs);

            // Update status to PROCESSED
            documentService.updateStatus(event.documentId(), DocumentStatus.PROCESSED, "SYSTEM");
            statusNotifier.emitLog(event.documentId(), event.fileName(), "EXTRACTION", "INFO",
                    "Document processed — queued for classification", System.currentTimeMillis() - extractStart);

            // Publish document.processed event for LLM classification
            // Include active PII findings so the LLM can validate/extend them
            var piiSummaries = piiFindings.stream()
                    .filter(p -> !p.isDismissed())
                    .map(p -> new DocumentProcessedEvent.PiiSummaryEntry(
                            p.getType(), p.getRedactedText(), p.getConfidence(),
                            p.getMethod() != null ? p.getMethod().name() : "PATTERN"))
                    .toList();

            var processedEvent = new DocumentProcessedEvent(
                    event.documentId(),
                    event.fileName(),
                    event.mimeType(),
                    event.fileSizeBytes(),
                    result.text(),
                    event.storageBucket() + "/" + event.storageKey(),
                    event.uploadedBy(),
                    Instant.now(),
                    piiSummaries
            );

            try {
                rabbitTemplate.convertAndSend(
                        RabbitMqConfig.EXCHANGE,
                        RabbitMqConfig.ROUTING_PROCESSED,
                        processedEvent
                );
            } catch (Exception queueErr) {
                log.error("Failed to publish processed event for {}: {}", event.documentId(), queueErr.getMessage());
                documentService.setError(event.documentId(), DocumentStatus.PROCESSING_FAILED,
                        "QUEUE", "Text extracted but failed to publish to classification queue: " + queueErr.getMessage());
                return;
            }

            log.info("Document {} processed successfully. Extracted {} chars, {} pages.",
                    event.documentId(), result.text().length(), result.pageCount());

        } catch (Exception e) {
            log.error("Document processing failed for {}: {}", event.documentId(), e.getMessage(), e);
            try {
                documentService.setError(event.documentId(), DocumentStatus.PROCESSING_FAILED,
                        "EXTRACTION", e.getMessage());
            } catch (Exception inner) {
                log.error("Failed to set error status for {}: {}", event.documentId(), inner.getMessage());
            }
        }
    }

    private static int toInt(Object val, int defaultVal) {
        if (val instanceof Number n) return n.intValue();
        if (val instanceof String s) { try { return Integer.parseInt(s); } catch (Exception e) { return defaultVal; } }
        return defaultVal;
    }

    private static boolean toBool(Object val, boolean defaultVal) {
        if (val instanceof Boolean b) return b;
        if (val instanceof String s) return Boolean.parseBoolean(s);
        return defaultVal;
    }
}
