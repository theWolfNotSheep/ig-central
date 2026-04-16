package co.uk.wolfnotsheep.infrastructure.services;

import co.uk.wolfnotsheep.document.models.DocumentModel;
import co.uk.wolfnotsheep.document.services.DocumentService;
import co.uk.wolfnotsheep.governance.models.DocumentClassificationResult;
import co.uk.wolfnotsheep.governance.models.TrainingDataSample;
import co.uk.wolfnotsheep.governance.repositories.TrainingDataSampleRepository;
import co.uk.wolfnotsheep.platform.config.services.AppConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Automatically collects training data from classified documents.
 * Called after a document is successfully classified.
 * All settings are configurable via the UI (AppConfigService).
 */
@Service
public class BertTrainingDataCollector {

    private static final Logger log = LoggerFactory.getLogger(BertTrainingDataCollector.class);

    private final TrainingDataSampleRepository sampleRepo;
    private final DocumentService documentService;
    private final AppConfigService configService;

    public BertTrainingDataCollector(TrainingDataSampleRepository sampleRepo,
                                     DocumentService documentService,
                                     AppConfigService configService) {
        this.sampleRepo = sampleRepo;
        this.documentService = documentService;
        this.configService = configService;
    }

    /**
     * Attempt to collect a training sample from a classified document.
     * Checks all configurable conditions before creating the sample.
     */
    @SuppressWarnings("unchecked")
    public void tryCollect(DocumentClassificationResult classification) {
        if (classification == null) return;

        // Check if auto-collection is enabled
        boolean enabled = configService.getValue("bert.training.auto_collect_enabled", false);
        if (!enabled) return;

        // Check confidence threshold
        double minConfidence = configService.getValue("bert.training.auto_collect_min_confidence", 0.8);
        if (classification.getConfidence() < minConfidence) {
            log.debug("Skipping auto-collect for doc {} — confidence {} < {}",
                    classification.getDocumentId(), classification.getConfidence(), minConfidence);
            return;
        }

        // Check category filter
        List<String> allowedCategories = configService.getValue("bert.training.auto_collect_categories", List.of());
        if (allowedCategories != null && !allowedCategories.isEmpty()) {
            if (classification.getCategoryId() != null && !allowedCategories.contains(classification.getCategoryId())) {
                log.debug("Skipping auto-collect for doc {} — category {} not in allowed list",
                        classification.getDocumentId(), classification.getCategoryName());
                return;
            }
        }

        // Check dedupe
        if (sampleRepo.existsBySourceDocumentId(classification.getDocumentId())) {
            log.debug("Skipping auto-collect for doc {} — already collected", classification.getDocumentId());
            return;
        }

        // Get the document text
        DocumentModel doc = documentService.getById(classification.getDocumentId());
        if (doc == null || doc.getExtractedText() == null || doc.getExtractedText().isBlank()) {
            log.debug("Skipping auto-collect for doc {} — no extracted text", classification.getDocumentId());
            return;
        }

        // Truncate text
        int maxLen = configService.getValue("bert.training.max_text_length", 2000);
        String text = doc.getExtractedText();
        if (text.length() > maxLen) text = text.substring(0, maxLen);

        // Create sample
        var sample = new TrainingDataSample();
        sample.setText(text);
        sample.setCategoryId(classification.getCategoryId());
        sample.setCategoryName(classification.getCategoryName());
        sample.setSensitivityLabel(classification.getSensitivityLabel() != null
                ? classification.getSensitivityLabel().name() : "INTERNAL");
        sample.setSource("AUTO_COLLECTED");
        sample.setSourceDocumentId(classification.getDocumentId());
        sample.setConfidence(classification.getConfidence());
        sample.setVerified(false);
        sample.setFileName(doc.getOriginalFileName());
        sample.setCreatedAt(Instant.now());
        sample.setUpdatedAt(Instant.now());

        sampleRepo.save(sample);
        log.info("Auto-collected training sample for doc {} — category: {}, confidence: {}",
                classification.getDocumentId(), classification.getCategoryName(), classification.getConfidence());
    }
}
