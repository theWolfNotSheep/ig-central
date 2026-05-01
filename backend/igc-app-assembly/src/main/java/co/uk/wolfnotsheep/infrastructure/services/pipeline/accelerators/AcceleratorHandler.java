package co.uk.wolfnotsheep.infrastructure.services.pipeline.accelerators;

import co.uk.wolfnotsheep.document.models.DocumentModel;

import java.util.Map;

/**
 * Interface for pipeline accelerator nodes that can short-circuit the LLM.
 * Implementations are discovered by Spring and collected into the
 * PipelineNodeHandlerRegistry.
 */
public interface AcceleratorHandler {

    /**
     * The node type key this handler serves (e.g. "bertClassifier", "templateFingerprint").
     */
    String getNodeTypeKey();

    /**
     * Evaluate whether this accelerator can classify the document.
     * Returns {@link AcceleratorResult#hit} if confident, {@link AcceleratorResult#miss} otherwise.
     */
    AcceleratorResult evaluate(DocumentModel doc, Map<String, Object> nodeConfig);
}
