package co.uk.wolfnotsheep.infrastructure.services.pipeline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Iterates the {@code metadataSchemaIds[]} of a resolved POLICY block,
 * dispatches each through {@link ScanRouterClient} against the seeded
 * {@code extract-metadata-${schemaId}} PROMPT block, and returns the
 * aggregated {@link MetadataExtractionResult}s. Phase 1.9 PR3 / CSV #36.
 *
 * <p>Reuses {@link ScanRouterClient} for transport — the cascade router
 * doesn't care whether the PROMPT is a SCAN or METADATA_EXTRACTION
 * block; only the result-shape interpretation differs. The naming
 * "ScanRouter" is historical (PR2 introduced it); a future rename to
 * {@code PromptRouterClient} or similar would clarify the dual use.
 *
 * <p>Behaviour is observe-only at this phase: the dispatcher records
 * the result but does not gate the pipeline. PR4 wires persistence
 * onto the document + enforcement handoff.
 */
@Service
public class MetadataExtractionDispatcher {

    private static final Logger log = LoggerFactory.getLogger(MetadataExtractionDispatcher.class);

    private static final String BLOCK_NAME_PREFIX = "extract-metadata-";

    private final ObjectProvider<ScanRouterClient> clientProvider;

    public MetadataExtractionDispatcher(ObjectProvider<ScanRouterClient> clientProvider) {
        this.clientProvider = clientProvider;
    }

    /**
     * Run each metadata schema's extraction prompt against the cascade
     * router. Synchronous — blocks until every schema completes (or
     * fails). The order of returned results matches the input order.
     *
     * @param pipelineRunId   pipeline-run id; combined with the schema
     *                        id to form an idempotency key per CSV #16.
     * @param metadataSchemaIds the resolved {@code metadataSchemaIds[]}
     *                        from the POLICY block.
     * @param extractedText   document text the cascade evaluates.
     */
    public List<MetadataExtractionResult> dispatch(
            String pipelineRunId,
            List<String> metadataSchemaIds,
            String extractedText) {

        if (metadataSchemaIds == null || metadataSchemaIds.isEmpty()) return List.of();
        ScanRouterClient client = clientProvider.getIfAvailable();
        List<MetadataExtractionResult> out = new ArrayList<>(metadataSchemaIds.size());

        for (String schemaId : metadataSchemaIds) {
            if (schemaId == null || schemaId.isBlank()) {
                out.add(new MetadataExtractionResult(
                        schemaId, null, false, null, null, null,
                        "schemaId missing or blank", -1));
                continue;
            }
            String blockRef = BLOCK_NAME_PREFIX + schemaId;

            if (client == null) {
                log.debug("[MetadataExtractionDispatcher] no ScanRouterClient bean — recording schema {} as not-dispatched",
                        schemaId);
                out.add(new MetadataExtractionResult(
                        schemaId, blockRef, false, null, null, null,
                        "ScanRouterClient bean unavailable (pipeline.scan-router.enabled=false)",
                        -1));
                continue;
            }

            String nodeRunId = pipelineRunId == null
                    ? "extract-" + schemaId
                    : pipelineRunId + "-extract-" + schemaId;

            ScanRouterClient.RouterScanOutcome outcome;
            try {
                outcome = client.dispatch(nodeRunId, blockRef, null, extractedText, nodeRunId);
            } catch (RuntimeException e) {
                log.warn("[MetadataExtractionDispatcher] schema {} dispatch threw: {}", schemaId, e.getMessage());
                out.add(new MetadataExtractionResult(
                        schemaId, blockRef, true, null, null, null,
                        "dispatch exception: " + e.getMessage(), -1));
                continue;
            }

            out.add(new MetadataExtractionResult(
                    schemaId, blockRef, true,
                    outcome.tierOfDecision(),
                    outcome.confidence(),
                    outcome.result(),
                    outcome.success() ? null : outcome.error(),
                    outcome.durationMs()));
        }

        long failures = out.stream().filter(r -> !r.success()).count();
        if (failures > 0) {
            log.warn("[MetadataExtractionDispatcher] {} extraction failure(s) out of {} — observe-only at Phase 1.9 PR3",
                    failures, out.size());
        } else {
            log.info("[MetadataExtractionDispatcher] dispatched {} extraction(s); 0 failures", out.size());
        }
        return out;
    }
}
