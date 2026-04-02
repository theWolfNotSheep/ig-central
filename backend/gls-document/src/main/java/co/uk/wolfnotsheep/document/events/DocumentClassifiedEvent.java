package co.uk.wolfnotsheep.document.events;

import co.uk.wolfnotsheep.governance.models.SensitivityLabel;

import java.time.Instant;
import java.util.List;

/**
 * Shared event record: published by llm-orchestration worker,
 * consumed by governance-enforcement worker.
 */
public record DocumentClassifiedEvent(
        String documentId,
        String classificationResultId,
        String categoryId,
        String categoryName,
        SensitivityLabel sensitivityLabel,
        List<String> tags,
        List<String> applicablePolicyIds,
        String retentionScheduleId,
        double confidence,
        boolean requiresHumanReview,
        Instant classifiedAt
) {}
