package co.uk.wolfnotsheep.document.events;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Published by the LLM worker when an async LLM job finishes (success or failure).
 * Consumed by the pipeline resumer to continue pipeline execution.
 */
public record LlmJobCompletedEvent(
        String jobId,
        String pipelineRunId,
        String nodeRunId,
        boolean success,
        String classificationResultId,
        String categoryId,
        String categoryName,
        String sensitivityLabel,
        List<String> tags,
        double confidence,
        boolean requiresHumanReview,
        String retentionScheduleId,
        List<String> applicablePolicyIds,
        Map<String, Object> extractedMetadata,
        Map<String, Object> customResult,
        String error,
        Instant completedAt
) {
    public static LlmJobCompletedEvent failure(String jobId, String pipelineRunId,
                                                String nodeRunId, String error) {
        return new LlmJobCompletedEvent(
                jobId, pipelineRunId, nodeRunId, false,
                null, null, null, null, null, 0.0, false, null, null, null, null,
                error, Instant.now());
    }
}
