package co.uk.wolfnotsheep.infrastructure.services.pipeline.accelerators;

import co.uk.wolfnotsheep.document.models.DocumentModel;
import co.uk.wolfnotsheep.document.models.DocumentStatus;
import co.uk.wolfnotsheep.document.repositories.DocumentRepository;
import co.uk.wolfnotsheep.governance.models.SensitivityLabel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/**
 * Accelerator: computes a text similarity hash and checks against previously
 * classified documents. Uses a simple n-gram shingling approach to detect
 * near-duplicate documents that can reuse an existing classification.
 *
 * Future enhancement: integrate with Elasticsearch vector search or a
 * dedicated embedding model for semantic similarity.
 */
@Service
public class SimilarityCacheService implements AcceleratorHandler {

    @Override
    public String getNodeTypeKey() { return "similarityCache"; }

    private static final Logger log = LoggerFactory.getLogger(SimilarityCacheService.class);

    private static final int SHINGLE_SIZE = 5;
    private static final int MIN_SHINGLES = 10;

    private final DocumentRepository documentRepository;

    public SimilarityCacheService(DocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    /**
     * Check if a similar document has already been classified.
     */
    public AcceleratorResult evaluate(DocumentModel doc, Map<String, Object> nodeConfig) {
        String text = doc.getExtractedText();
        if (text == null || text.length() < 100) {
            return AcceleratorResult.miss();
        }

        double threshold = toDouble(nodeConfig.get("threshold"), 0.90);

        Set<String> docShingles = computeShingles(text);
        if (docShingles.size() < MIN_SHINGLES) {
            return AcceleratorResult.miss();
        }

        // Find recently classified documents with the same mime type
        List<DocumentModel> candidates = documentRepository
                .findByStatusInAndMimeType(
                        List.of(DocumentStatus.GOVERNANCE_APPLIED, DocumentStatus.INBOX, DocumentStatus.FILED),
                        doc.getMimeType());

        // Limit candidate scanning to avoid performance issues
        int maxCandidates = toInt(nodeConfig.get("maxCandidates"), 100);
        if (candidates.size() > maxCandidates) {
            candidates = candidates.subList(0, maxCandidates);
        }

        DocumentModel bestMatch = null;
        double bestSimilarity = 0;

        for (DocumentModel candidate : candidates) {
            if (candidate.getExtractedText() == null || candidate.getCategoryId() == null) continue;
            if (candidate.getId().equals(doc.getId())) continue;

            Set<String> candidateShingles = computeShingles(candidate.getExtractedText());
            double similarity = jaccardSimilarity(docShingles, candidateShingles);

            if (similarity > bestSimilarity) {
                bestSimilarity = similarity;
                bestMatch = candidate;
            }
        }

        if (bestMatch != null && bestSimilarity >= threshold) {
            log.info("[SimilarityCache] Match found for doc {} — similar to {} (similarity: {})",
                    doc.getId(), bestMatch.getId(), String.format("%.2f", bestSimilarity));

            return AcceleratorResult.hit(
                    bestMatch.getCategoryId(), bestMatch.getCategoryName(),
                    bestMatch.getSensitivityLabel(), bestMatch.getTags(),
                    bestMatch.getRetentionScheduleId(), bestSimilarity,
                    "Similar to document '" + bestMatch.getOriginalFileName()
                            + "' (similarity: " + String.format("%.2f", bestSimilarity) + ")",
                    "similarityCache"
            );
        }

        return AcceleratorResult.miss();
    }

    Set<String> computeShingles(String text) {
        // Normalize: lowercase, collapse whitespace
        String normalized = text.toLowerCase().replaceAll("\\s+", " ").trim();
        String[] words = normalized.split(" ");

        Set<String> shingles = new HashSet<>();
        for (int i = 0; i <= words.length - SHINGLE_SIZE; i++) {
            StringBuilder sb = new StringBuilder();
            for (int j = 0; j < SHINGLE_SIZE; j++) {
                if (j > 0) sb.append(" ");
                sb.append(words[i + j]);
            }
            shingles.add(sb.toString());
        }
        return shingles;
    }

    double jaccardSimilarity(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) return 0;

        Set<String> intersection = new HashSet<>(a);
        intersection.retainAll(b);

        Set<String> union = new HashSet<>(a);
        union.addAll(b);

        return (double) intersection.size() / union.size();
    }

    private static double toDouble(Object val, double defaultVal) {
        if (val instanceof Number n) return n.doubleValue();
        if (val instanceof String s) { try { return Double.parseDouble(s); } catch (Exception e) { return defaultVal; } }
        return defaultVal;
    }

    private static int toInt(Object val, int defaultVal) {
        if (val instanceof Number n) return n.intValue();
        if (val instanceof String s) { try { return Integer.parseInt(s); } catch (Exception e) { return defaultVal; } }
        return defaultVal;
    }
}
