package co.uk.wolfnotsheep.document.services;

import co.uk.wolfnotsheep.document.models.DocumentModel;
import co.uk.wolfnotsheep.document.models.PiiEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

/**
 * Service for redacting PII from document text.
 * Produces a redacted copy of extractedText with PII replaced by [REDACTED:TYPE] markers.
 * Also supports generating a redacted text download.
 */
@Service
public class PiiRedactionService {

    private static final Logger log = LoggerFactory.getLogger(PiiRedactionService.class);

    private final DocumentService documentService;

    public PiiRedactionService(DocumentService documentService) {
        this.documentService = documentService;
    }

    /**
     * Generate redacted text for a document. Replaces all active (non-dismissed) PII
     * findings with [REDACTED:TYPE] markers at their original offsets.
     *
     * @return redacted text, or null if no text or no PII
     */
    public String redactText(DocumentModel doc) {
        if (doc.getExtractedText() == null) return null;
        if (doc.getPiiFindings() == null || doc.getPiiFindings().isEmpty()) {
            return doc.getExtractedText();
        }

        String text = doc.getExtractedText();
        List<PiiEntity> activeFindings = doc.getPiiFindings().stream()
                .filter(p -> !p.isDismissed())
                .filter(p -> p.getOffset() >= 0 && p.getMatchedText() != null)
                .sorted(Comparator.comparingInt(PiiEntity::getOffset).reversed())
                .toList();

        StringBuilder sb = new StringBuilder(text);
        for (PiiEntity pii : activeFindings) {
            int start = pii.getOffset();
            int end = start + pii.getMatchedText().length();
            if (start >= 0 && end <= sb.length()) {
                sb.replace(start, end, "[REDACTED:" + pii.getType() + "]");
            }
        }
        return sb.toString();
    }

    /**
     * Apply redaction to a document: replaces extractedText with redacted version,
     * clears matched text from PII findings (keeping type/redacted markers), and
     * updates piiStatus to REDACTED.
     *
     * @return the updated document
     */
    public DocumentModel applyRedaction(String documentId) {
        DocumentModel doc = documentService.getById(documentId);
        if (doc == null) return null;

        String redacted = redactText(doc);
        if (redacted != null) {
            doc.setExtractedText(redacted);
        }

        // Clear raw matched text from findings — keep type and redacted text
        if (doc.getPiiFindings() != null) {
            for (PiiEntity pii : doc.getPiiFindings()) {
                if (!pii.isDismissed()) {
                    pii.setMatchedText("[REDACTED]");
                }
            }
        }

        doc.setPiiStatus("REDACTED");
        return documentService.save(doc);
    }
}
