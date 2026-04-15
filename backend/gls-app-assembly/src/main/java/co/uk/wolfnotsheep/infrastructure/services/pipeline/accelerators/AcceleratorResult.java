package co.uk.wolfnotsheep.infrastructure.services.pipeline.accelerators;

import co.uk.wolfnotsheep.governance.models.SensitivityLabel;

import java.util.List;

/**
 * Result from an accelerator node. If {@code matched} is true, the accelerator
 * has produced a classification and the LLM can be skipped.
 */
public record AcceleratorResult(
        boolean matched,
        String categoryId,
        String categoryName,
        SensitivityLabel sensitivityLabel,
        List<String> tags,
        String retentionScheduleId,
        double confidence,
        String reasoning,
        String acceleratorType
) {
    public static AcceleratorResult miss() {
        return new AcceleratorResult(false, null, null, null, null, null, 0, null, null);
    }

    public static AcceleratorResult hit(String categoryId, String categoryName,
                                         SensitivityLabel sensitivityLabel, List<String> tags,
                                         String retentionScheduleId, double confidence,
                                         String reasoning, String acceleratorType) {
        return new AcceleratorResult(true, categoryId, categoryName, sensitivityLabel,
                tags, retentionScheduleId, confidence, reasoning, acceleratorType);
    }
}
