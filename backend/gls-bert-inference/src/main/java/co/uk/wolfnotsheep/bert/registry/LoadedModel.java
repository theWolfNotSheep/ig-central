package co.uk.wolfnotsheep.bert.registry;

import java.time.Instant;
import java.util.List;

/**
 * View of one ONNX artefact this replica currently has in memory.
 * Surfaced on {@code GET /v1/models}.
 */
public record LoadedModel(
        String modelVersion,
        String bucket,
        String objectKey,
        List<String> labels,
        List<String> blockIds,
        Instant loadedAt
) {
}
