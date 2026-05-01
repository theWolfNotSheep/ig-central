package co.uk.wolfnotsheep.infrastructure.services.pipeline;

import co.uk.wolfnotsheep.governance.models.PipelineDefinition;
import co.uk.wolfnotsheep.governance.models.PipelineDefinition.VisualEdge;
import co.uk.wolfnotsheep.governance.models.PipelineDefinition.VisualNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the PipelineExecutionEngine's graph compilation logic.
 * These test the pure functions (topological sort, condition evaluation)
 * without requiring Spring context or external dependencies.
 */
@ExtendWith(MockitoExtension.class)
class PipelineExecutionEngineTest {

    // We need to instantiate the engine for package-visible topologicalSort.
    // Since it has many dependencies, we mock them all and only test graph logic.
    @InjectMocks
    private PipelineExecutionEngine engine;

    // All required constructor dependencies (mocked, not used for graph tests)
    @Mock private co.uk.wolfnotsheep.docprocessing.extraction.TextExtractionService textExtractionService;
    @Mock private co.uk.wolfnotsheep.document.services.DocumentService documentService;
    @Mock private co.uk.wolfnotsheep.document.services.ObjectStorageService objectStorage;
    @Mock private co.uk.wolfnotsheep.document.services.PiiPatternScanner piiScanner;
    @Mock private co.uk.wolfnotsheep.governance.services.GovernanceService governanceService;
    @Mock private co.uk.wolfnotsheep.enforcement.services.EnforcementService enforcementService;
    @Mock private co.uk.wolfnotsheep.governance.services.PipelineRoutingService pipelineRoutingService;
    @Mock private co.uk.wolfnotsheep.governance.repositories.PipelineBlockRepository blockRepo;
    @Mock private co.uk.wolfnotsheep.governance.repositories.DocumentClassificationResultRepository classificationResultRepo;
    @Mock private org.springframework.amqp.rabbit.core.RabbitTemplate rabbitTemplate;
    @Mock private co.uk.wolfnotsheep.document.services.PipelineStatusNotifier statusNotifier;
    @Mock private co.uk.wolfnotsheep.governance.services.NodeTypeDefinitionService nodeTypeService;
    @Mock private PipelineNodeHandlerRegistry handlerRegistry;
    @Mock private GenericHttpNodeExecutor genericHttpExecutor;
    @Mock private SyncLlmNodeExecutor syncLlmExecutor;
    @Mock private co.uk.wolfnotsheep.infrastructure.services.pipeline.accelerators.SmartTruncationService smartTruncationService;
    @Mock private co.uk.wolfnotsheep.document.repositories.SystemErrorRepository systemErrorRepo;
    @Mock private co.uk.wolfnotsheep.document.repositories.PipelineRunRepository pipelineRunRepo;
    @Mock private co.uk.wolfnotsheep.document.repositories.NodeRunRepository nodeRunRepo;

    // ── Topological Sort Tests ──────────────────────────────────────

    @Test
    void topologicalSort_linearPipeline_returnsNodesInOrder() {
        var pipeline = buildPipeline(
                List.of(
                        node("1", "trigger"),
                        node("2", "textExtraction"),
                        node("3", "piiScanner"),
                        node("4", "aiClassification")
                ),
                List.of(
                        edge("1", "2"),
                        edge("2", "3"),
                        edge("3", "4")
                )
        );

        List<VisualNode> sorted = engine.topologicalSort(pipeline);

        assertThat(sorted).hasSize(4);
        assertThat(sorted.get(0).id()).isEqualTo("1");
        assertThat(sorted.get(1).id()).isEqualTo("2");
        assertThat(sorted.get(2).id()).isEqualTo("3");
        assertThat(sorted.get(3).id()).isEqualTo("4");
    }

    @Test
    void topologicalSort_withBranching_producesValidOrder() {
        // trigger → extraction → condition
        //                           ├── (true) → governance
        //                           └── (false) → humanReview
        var pipeline = buildPipeline(
                List.of(
                        node("1", "trigger"),
                        node("2", "textExtraction"),
                        node("3", "condition"),
                        node("4", "governance"),
                        node("5", "humanReview")
                ),
                List.of(
                        edge("1", "2"),
                        edge("2", "3"),
                        edgeWithHandle("3", "4", "true"),
                        edgeWithHandle("3", "5", "false")
                )
        );

        List<VisualNode> sorted = engine.topologicalSort(pipeline);

        assertThat(sorted).hasSize(5);
        // trigger must be first
        assertThat(sorted.get(0).id()).isEqualTo("1");
        // extraction before condition
        int extractIdx = indexOf(sorted, "2");
        int condIdx = indexOf(sorted, "3");
        assertThat(extractIdx).isLessThan(condIdx);
        // both branches after condition
        int govIdx = indexOf(sorted, "4");
        int reviewIdx = indexOf(sorted, "5");
        assertThat(condIdx).isLessThan(govIdx);
        assertThat(condIdx).isLessThan(reviewIdx);
    }

    @Test
    void topologicalSort_skipsErrorEdges() {
        var pipeline = buildPipeline(
                List.of(
                        node("1", "trigger"),
                        node("2", "textExtraction"),
                        node("3", "errorHandler")
                ),
                List.of(
                        edge("1", "2"),
                        // Error edge should be skipped — errorHandler stays disconnected
                        new VisualEdge("e2", "2", "3", "error", "errorInput", null)
                )
        );

        List<VisualNode> sorted = engine.topologicalSort(pipeline);

        assertThat(sorted).hasSize(3);
        // extraction must come after trigger (connected by normal edge)
        int triggerIdx = indexOf(sorted, "1");
        int extractIdx = indexOf(sorted, "2");
        assertThat(triggerIdx).isLessThan(extractIdx);
        // errorHandler has no non-error inbound edges, so it's a root node
        // Its position relative to trigger is unspecified — just verify it's present
        assertThat(sorted).extracting(VisualNode::id).contains("3");
    }

    @Test
    void topologicalSort_nullPipeline_returnsEmptyList() {
        assertThat(engine.topologicalSort(null)).isEmpty();
    }

    @Test
    void topologicalSort_emptyNodes_returnsEmptyList() {
        var pipeline = new PipelineDefinition();
        pipeline.setVisualNodes(List.of());
        assertThat(engine.topologicalSort(pipeline)).isEmpty();
    }

    @Test
    void topologicalSort_singleNode_returnsIt() {
        var pipeline = buildPipeline(
                List.of(node("1", "trigger")),
                List.of()
        );

        List<VisualNode> sorted = engine.topologicalSort(pipeline);
        assertThat(sorted).hasSize(1);
        assertThat(sorted.get(0).id()).isEqualTo("1");
    }

    @Test
    void topologicalSort_parallelAccelerators_allBeforeLlm() {
        // trigger → extraction → bert (accel)
        //                      → rules (accel)
        //                      → aiClassification
        // bert → aiClassification, rules → aiClassification
        var pipeline = buildPipeline(
                List.of(
                        node("1", "trigger"),
                        node("2", "textExtraction"),
                        node("3", "bertClassifier"),
                        node("4", "rulesEngine"),
                        node("5", "aiClassification")
                ),
                List.of(
                        edge("1", "2"),
                        edge("2", "3"),
                        edge("2", "4"),
                        edge("3", "5"),
                        edge("4", "5")
                )
        );

        List<VisualNode> sorted = engine.topologicalSort(pipeline);

        assertThat(sorted).hasSize(5);
        assertThat(sorted.get(0).id()).isEqualTo("1"); // trigger
        assertThat(sorted.get(1).id()).isEqualTo("2"); // extraction
        // Both accelerators before LLM
        int bertIdx = indexOf(sorted, "3");
        int rulesIdx = indexOf(sorted, "4");
        int llmIdx = indexOf(sorted, "5");
        assertThat(bertIdx).isLessThan(llmIdx);
        assertThat(rulesIdx).isLessThan(llmIdx);
    }

    // ── Helpers ─────────────────────────────────────────────────────

    private static VisualNode node(String id, String type) {
        return new VisualNode(id, type, type + "-label", 0, 0, null, "configured", null);
    }

    private static VisualEdge edge(String source, String target) {
        return new VisualEdge("e-" + source + "-" + target, source, target, null, null, null);
    }

    private static VisualEdge edgeWithHandle(String source, String target, String sourceHandle) {
        return new VisualEdge("e-" + source + "-" + target, source, target, sourceHandle, null, null);
    }

    private static PipelineDefinition buildPipeline(List<VisualNode> nodes, List<VisualEdge> edges) {
        var pipeline = new PipelineDefinition();
        pipeline.setVisualNodes(nodes);
        pipeline.setVisualEdges(edges);
        return pipeline;
    }

    private static int indexOf(List<VisualNode> nodes, String id) {
        for (int i = 0; i < nodes.size(); i++) {
            if (nodes.get(i).id().equals(id)) return i;
        }
        return -1;
    }
}
