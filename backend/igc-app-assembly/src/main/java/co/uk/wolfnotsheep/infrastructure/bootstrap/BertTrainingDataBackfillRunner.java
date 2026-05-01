package co.uk.wolfnotsheep.infrastructure.bootstrap;

import co.uk.wolfnotsheep.document.services.DocumentService;
import co.uk.wolfnotsheep.governance.models.DocumentClassificationResult;
import co.uk.wolfnotsheep.governance.models.TrainingDataSample;
import co.uk.wolfnotsheep.governance.repositories.DocumentClassificationResultRepository;
import co.uk.wolfnotsheep.governance.repositories.TrainingDataSampleRepository;
import co.uk.wolfnotsheep.platform.config.services.AppConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * One-time backfill: harvests existing LLM classification results into
 * BERT training samples. Also enables auto-collection for future classifications.
 */
@Component
@Order(210)
public class BertTrainingDataBackfillRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BertTrainingDataBackfillRunner.class);

    private final DocumentClassificationResultRepository classResultRepo;
    private final TrainingDataSampleRepository sampleRepo;
    private final DocumentService documentService;
    private final AppConfigService configService;

    public BertTrainingDataBackfillRunner(
            DocumentClassificationResultRepository classResultRepo,
            TrainingDataSampleRepository sampleRepo,
            DocumentService documentService,
            AppConfigService configService) {
        this.classResultRepo = classResultRepo;
        this.sampleRepo = sampleRepo;
        this.documentService = documentService;
        this.configService = configService;
    }

    @Override
    public void run(ApplicationArguments args) {
        long autoCollected = sampleRepo.countBySource("AUTO_COLLECTED");
        if (autoCollected >= 10) return;

        log.info("Backfilling BERT training data from existing LLM classifications...");

        double minConfidence = configService.getValue("bert.training.auto_collect_min_confidence", 0.8);
        int maxLen = configService.getValue("bert.training.max_text_length", 2000);
        int created = 0;

        for (var cr : classResultRepo.findAll()) {
            if (cr.getConfidence() < minConfidence) continue;
            if (cr.getCategoryId() == null || cr.getCategoryName() == null) continue;
            if (sampleRepo.existsBySourceDocumentId(cr.getDocumentId())) continue;

            var doc = documentService.getById(cr.getDocumentId());
            if (doc == null || doc.getExtractedText() == null || doc.getExtractedText().isBlank()) continue;

            String text = doc.getExtractedText();
            if (text.length() > maxLen) text = text.substring(0, maxLen);

            var sample = new TrainingDataSample();
            sample.setText(text);
            sample.setCategoryId(cr.getCategoryId());
            sample.setCategoryName(cr.getCategoryName());
            sample.setSensitivityLabel(cr.getSensitivityLabel() != null
                    ? cr.getSensitivityLabel().name() : "INTERNAL");
            sample.setSource("AUTO_COLLECTED");
            sample.setSourceDocumentId(cr.getDocumentId());
            sample.setConfidence(cr.getConfidence());
            sample.setVerified(false);
            sample.setFileName(doc.getOriginalFileName());
            sample.setCreatedAt(Instant.now());
            sample.setUpdatedAt(Instant.now());

            sampleRepo.save(sample);
            created++;
        }

        if (created > 0) {
            configService.save("bert.training.auto_collect_enabled", "bert", "true",
                    "Auto-collect BERT training data from LLM classifications");
            log.info("Backfilled {} BERT training samples. Auto-collection enabled.", created);
        }
    }
}
