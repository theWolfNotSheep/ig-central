package co.uk.wolfnotsheep.infrastructure.services.pipeline;

/**
 * Interface for pipeline node handlers that execute as part of the graph.
 * Implementations are discovered by Spring and collected into the
 * PipelineNodeHandlerRegistry.
 */
public interface PipelineNodeHandler {

    /**
     * The node type key this handler serves (e.g. "smartTruncation").
     */
    String getNodeTypeKey();

    /**
     * Execute this node's logic.
     */
    void handle(NodeHandlerContext ctx);
}
