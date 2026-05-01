package co.uk.wolfnotsheep.enforcement.services;

import co.uk.wolfnotsheep.document.models.DocumentModel;
import co.uk.wolfnotsheep.document.repositories.DocumentRepository;
import co.uk.wolfnotsheep.governance.models.ClassificationCategory;
import co.uk.wolfnotsheep.governance.repositories.ClassificationCategoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Applies ISO 15489 record codes from the taxonomy to documents.
 * Can be wired into the pipeline or called standalone for backfilling.
 *
 * The record code IS the classificationCode from the ClassificationCategory
 * (e.g., "FIN-AP-PAY"). This service reads the code from the category
 * and stamps it onto the document along with related ISO 15489 fields.
 */
@Service
public class RecordCodeService {

    private static final Logger log = LoggerFactory.getLogger(RecordCodeService.class);

    private final DocumentRepository documentRepo;
    private final ClassificationCategoryRepository categoryRepo;
    private final MongoTemplate mongoTemplate;

    public RecordCodeService(DocumentRepository documentRepo,
                             ClassificationCategoryRepository categoryRepo,
                             MongoTemplate mongoTemplate) {
        this.documentRepo = documentRepo;
        this.categoryRepo = categoryRepo;
        this.mongoTemplate = mongoTemplate;
    }

    /**
     * Apply the record code from the taxonomy to a single document.
     * Reads the document's categoryId, looks up the ClassificationCategory,
     * and copies classificationCode, classificationPath, classificationLevel,
     * jurisdiction, legalCitation, personalDataFlag, vitalRecordFlag.
     *
     * @return the updated document, or null if not found / no category
     */
    public DocumentModel applyRecordCode(String documentId) {
        DocumentModel doc = documentRepo.findById(documentId).orElse(null);
        if (doc == null) return null;

        String categoryId = doc.getCategoryId();
        if (categoryId == null || categoryId.isBlank()) {
            log.debug("Document {} has no categoryId — skipping record code", documentId);
            return doc;
        }

        ClassificationCategory cat = categoryRepo.findById(categoryId).orElse(null);
        if (cat == null) {
            log.warn("Category {} not found for document {} — skipping record code", categoryId, documentId);
            return doc;
        }

        doc.setClassificationCode(cat.getClassificationCode());
        doc.setClassificationPath(cat.getPath());
        doc.setClassificationLevel(cat.getLevel());
        doc.setJurisdiction(cat.getJurisdiction());
        doc.setLegalCitation(cat.getLegalCitation());
        doc.setCategoryPersonalData(cat.isPersonalDataFlag());
        doc.setVitalRecord(cat.isVitalRecordFlag());
        doc.setTaxonomyVersion(cat.getVersion());
        doc.setRetentionTrigger(cat.getRetentionTrigger());
        doc.setRetentionPeriodText(cat.getRetentionPeriodText());

        documentRepo.save(doc);
        log.info("Applied record code {} to document {}", cat.getClassificationCode(), documentId);
        return doc;
    }

    /**
     * Backfill: find all documents with a categoryId but no classificationCode
     * and apply the record code from the taxonomy.
     *
     * @return number of documents updated
     */
    public int backfillRecordCodes() {
        Query query = Query.query(new Criteria().andOperator(
                Criteria.where("categoryId").ne(null),
                new Criteria().orOperator(
                        Criteria.where("classificationCode").is(null),
                        Criteria.where("classificationCode").is("")
                )
        ));

        List<DocumentModel> docs = mongoTemplate.find(query, DocumentModel.class);
        int updated = 0;

        for (DocumentModel doc : docs) {
            try {
                DocumentModel result = applyRecordCode(doc.getId());
                if (result != null && result.getClassificationCode() != null) {
                    updated++;
                }
            } catch (Exception e) {
                log.warn("Failed to backfill record code for document {}: {}", doc.getId(), e.getMessage());
            }
        }

        log.info("Record code backfill complete: {} documents updated", updated);
        return updated;
    }
}
