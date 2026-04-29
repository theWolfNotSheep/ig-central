package co.uk.wolfnotsheep.router.parse;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Wire shape of {@code LlmJobCompletedEvent} as published to
 * {@code gls.pipeline} (routing key {@code pipeline.llm.completed}).
 *
 * <p>Mirrors {@code co.uk.wolfnotsheep.document.events.LlmJobCompletedEvent}.
 * {@code @JsonIgnoreProperties(ignoreUnknown = true)} so future
 * additions to the wire format don't break this consumer.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LlmJobResult(
        String jobId,
        String pipelineRunId,
        String nodeRunId,
        boolean success,
        String classificationResultId,
        String categoryId,
        String categoryName,
        String sensitivityLabel,
        List<String> tags,
        Double confidence,
        Boolean requiresHumanReview,
        String retentionScheduleId,
        List<String> applicablePolicyIds,
        Map<String, Object> extractedMetadata,
        Map<String, Object> customResult,
        String error,
        Instant completedAt
) {
}
