package co.uk.wolfnotsheep.infrastructure.services.pipeline.accelerators;

import co.uk.wolfnotsheep.document.models.DocumentModel;
import co.uk.wolfnotsheep.governance.models.TemplateFingerprint;
import co.uk.wolfnotsheep.governance.repositories.TemplateFingerprintRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Accelerator: hashes document structure (headings, layout patterns) and
 * checks against known templates. If a match is found above the confidence
 * threshold, the LLM classification is skipped.
 */
@Service
public class TemplateFingerprintService implements AcceleratorHandler {

    @Override
    public String getNodeTypeKey() { return "templateFingerprint"; }

    private static final Logger log = LoggerFactory.getLogger(TemplateFingerprintService.class);

    private static final Pattern HEADING_PATTERN = Pattern.compile(
            "^\\s*(#{1,6}\\s+.+|[A-Z][A-Z\\s]{2,}:?|\\d+\\.\\s+[A-Z].+)$",
            Pattern.MULTILINE
    );

    private final TemplateFingerprintRepository fingerprintRepo;

    public TemplateFingerprintService(TemplateFingerprintRepository fingerprintRepo) {
        this.fingerprintRepo = fingerprintRepo;
    }

    /**
     * Check if the document matches a known template fingerprint.
     *
     * @param doc       the document with extracted text
     * @param nodeConfig accelerator node configuration (threshold, etc.)
     * @return AcceleratorResult — matched if a fingerprint is found above threshold
     */
    public AcceleratorResult evaluate(DocumentModel doc, Map<String, Object> nodeConfig) {
        if (doc.getExtractedText() == null || doc.getExtractedText().isBlank()) {
            return AcceleratorResult.miss();
        }

        double threshold = toDouble(nodeConfig.get("threshold"), 0.85);

        String structure = extractStructure(doc.getExtractedText());
        String hash = hashStructure(structure);

        var match = fingerprintRepo.findByFingerprint(hash);
        if (match.isPresent()) {
            TemplateFingerprint fp = match.get();
            if (fp.getConfidence() >= threshold) {
                fp.setMatchCount(fp.getMatchCount() + 1);
                fp.setLastMatchedAt(Instant.now());
                fingerprintRepo.save(fp);

                log.info("[TemplateFP] Match found for doc {} — template '{}', confidence {}",
                        doc.getId(), fp.getCategoryName(), fp.getConfidence());

                return AcceleratorResult.hit(
                        fp.getCategoryId(), fp.getCategoryName(),
                        fp.getSensitivityLabel(), fp.getTags(),
                        fp.getRetentionScheduleId(), fp.getConfidence(),
                        "Matched template fingerprint (hash: " + hash.substring(0, 8) + "...)",
                        "templateFingerprint"
                );
            }
        }

        return AcceleratorResult.miss();
    }

    /**
     * Learn a fingerprint from a classified document so future similar documents
     * can be auto-classified.
     */
    public TemplateFingerprint learnFromDocument(DocumentModel doc) {
        if (doc.getExtractedText() == null || doc.getCategoryId() == null) {
            return null;
        }

        String structure = extractStructure(doc.getExtractedText());
        String hash = hashStructure(structure);

        var existing = fingerprintRepo.findByFingerprint(hash);
        if (existing.isPresent()) {
            return existing.get();
        }

        TemplateFingerprint fp = new TemplateFingerprint();
        fp.setFingerprint(hash);
        fp.setCategoryId(doc.getCategoryId());
        fp.setCategoryName(doc.getCategoryName());
        fp.setSensitivityLabel(doc.getSensitivityLabel());
        fp.setTags(doc.getTags());
        fp.setRetentionScheduleId(doc.getRetentionScheduleId());
        fp.setConfidence(0.90);
        fp.setMatchCount(0);
        fp.setLearnedFromDocumentId(doc.getId());
        fp.setMimeType(doc.getMimeType());
        fp.setCreatedAt(Instant.now());

        return fingerprintRepo.save(fp);
    }

    /**
     * Extract structural features: headings, section patterns, line counts.
     */
    String extractStructure(String text) {
        StringBuilder sb = new StringBuilder();
        Matcher m = HEADING_PATTERN.matcher(text);
        while (m.find()) {
            sb.append(m.group().trim().replaceAll("\\s+", " ").toUpperCase()).append("\n");
        }
        // Include line count bucket as a structural hint
        long lineCount = text.lines().count();
        sb.append("LINES:").append(lineCount / 10 * 10);
        return sb.toString();
    }

    String hashStructure(String structure) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(structure.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to hash structure", e);
        }
    }

    private static double toDouble(Object val, double defaultVal) {
        if (val instanceof Number n) return n.doubleValue();
        if (val instanceof String s) { try { return Double.parseDouble(s); } catch (Exception e) { return defaultVal; } }
        return defaultVal;
    }
}
