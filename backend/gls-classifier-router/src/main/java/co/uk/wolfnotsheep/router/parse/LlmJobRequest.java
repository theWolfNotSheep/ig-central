package co.uk.wolfnotsheep.router.parse;

/**
 * Wire shape of {@code LlmJobRequestedEvent} as published to
 * {@code gls.pipeline} (routing key {@code pipeline.llm.requested}).
 *
 * <p>Mirrors {@code co.uk.wolfnotsheep.document.events.LlmJobRequestedEvent}
 * in {@code gls-document} — duplicated here to avoid pulling the
 * full document module into the router. The wire serialisation must
 * match field-for-field; both producer and consumer use Jackson with
 * default field-name discovery.
 */
public record LlmJobRequest(
        String jobId,
        String pipelineRunId,
        String nodeRunId,
        String documentId,
        String nodeKey,
        String mode,
        String blockId,
        Integer blockVersion,
        String pipelineId,
        String extractedText,
        String fileName,
        String mimeType,
        long fileSizeBytes,
        String uploadedBy,
        String idempotencyKey
) {
}
