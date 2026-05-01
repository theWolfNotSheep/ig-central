package co.uk.wolfnotsheep.llm.api;

import co.uk.wolfnotsheep.governance.models.SensitivityLabel;

import java.util.List;
import java.util.Map;

/**
 * Response DTO for the synchronous classification endpoint.
 */
public record ClassifyResponse(
        boolean success,
        String error,
        // CLASSIFICATION mode fields
        String classificationResultId,
        String categoryId,
        String categoryName,
        SensitivityLabel sensitivityLabel,
        List<String> tags,
        double confidence,
        boolean needsReview,
        String retentionScheduleId,
        List<String> applicablePolicyIds,
        // CUSTOM_PROMPT mode fields
        String llmResponse,
        Map<String, Object> parsedResult
) {
    public static ClassifyResponse classification(String resultId, String categoryId, String categoryName,
                                                    SensitivityLabel sensitivity, List<String> tags,
                                                    double confidence, boolean needsReview,
                                                    String retentionScheduleId, List<String> policyIds) {
        return new ClassifyResponse(true, null, resultId, categoryId, categoryName, sensitivity,
                tags, confidence, needsReview, retentionScheduleId, policyIds, null, null);
    }

    public static ClassifyResponse customPrompt(String llmResponse, Map<String, Object> parsed) {
        return new ClassifyResponse(true, null, null, null, null, null,
                null, 0, false, null, null, llmResponse, parsed);
    }

    public static ClassifyResponse error(String message) {
        return new ClassifyResponse(false, message, null, null, null, null,
                null, 0, false, null, null, null, null);
    }
}
