package co.uk.wolfnotsheep.infrastructure.services.pipeline;

import co.uk.wolfnotsheep.document.events.DocumentClassifiedEvent;
import co.uk.wolfnotsheep.document.events.DocumentIngestedEvent;
import co.uk.wolfnotsheep.document.models.DocumentModel;
import co.uk.wolfnotsheep.governance.models.PipelineDefinition.VisualNode;

import java.util.Map;

/**
 * Context passed to {@link PipelineNodeHandler} implementations during pipeline execution.
 */
public record NodeHandlerContext(
        DocumentModel document,
        VisualNode node,
        Map<String, Object> mergedConfig,
        DocumentIngestedEvent ingestedEvent,       // null in Phase 2
        DocumentClassifiedEvent classifiedEvent     // null in Phase 1
) {}
