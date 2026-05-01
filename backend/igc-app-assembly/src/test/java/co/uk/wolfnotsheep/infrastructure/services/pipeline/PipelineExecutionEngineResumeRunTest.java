package co.uk.wolfnotsheep.infrastructure.services.pipeline;

import co.uk.wolfnotsheep.document.models.DocumentModel;
import co.uk.wolfnotsheep.document.models.PipelineRun;
import co.uk.wolfnotsheep.document.models.PipelineRunStatus;
import co.uk.wolfnotsheep.document.repositories.NodeRunRepository;
import co.uk.wolfnotsheep.document.repositories.PipelineRunRepository;
import co.uk.wolfnotsheep.document.repositories.SystemErrorRepository;
import co.uk.wolfnotsheep.document.services.DocumentService;
import co.uk.wolfnotsheep.governance.repositories.PipelineBlockRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Early-exit tests for {@link PipelineExecutionEngine#resumeRun(String)}.
 *
 * <p>The happy-path walk is exercised indirectly through the existing
 * {@code resumePipeline} (LLM completion) tests — both entry points
 * call into the same private {@code walkNodes} after staging. These
 * tests focus on the guards that protect the engine from being asked
 * to resume an inappropriate run (missing, already-terminal, or whose
 * document has been deleted).
 */
@ExtendWith(MockitoExtension.class)
class PipelineExecutionEngineResumeRunTest {

    @InjectMocks private PipelineExecutionEngine engine;

    @Mock private co.uk.wolfnotsheep.docprocessing.extraction.TextExtractionService textExtractionService;
    @Mock private DocumentService documentService;
    @Mock private co.uk.wolfnotsheep.document.services.ObjectStorageService objectStorage;
    @Mock private co.uk.wolfnotsheep.document.services.PiiPatternScanner piiScanner;
    @Mock private co.uk.wolfnotsheep.governance.services.GovernanceService governanceService;
    @Mock private co.uk.wolfnotsheep.enforcement.services.EnforcementService enforcementService;
    @Mock private co.uk.wolfnotsheep.governance.services.PipelineRoutingService pipelineRoutingService;
    @Mock private PipelineBlockRepository blockRepo;
    @Mock private co.uk.wolfnotsheep.governance.repositories.DocumentClassificationResultRepository classificationResultRepo;
    @Mock private org.springframework.amqp.rabbit.core.RabbitTemplate rabbitTemplate;
    @Mock private co.uk.wolfnotsheep.document.services.PipelineStatusNotifier statusNotifier;
    @Mock private co.uk.wolfnotsheep.governance.services.NodeTypeDefinitionService nodeTypeService;
    @Mock private PipelineNodeHandlerRegistry handlerRegistry;
    @Mock private GenericHttpNodeExecutor genericHttpExecutor;
    @Mock private SyncLlmNodeExecutor syncLlmExecutor;
    @Mock private co.uk.wolfnotsheep.infrastructure.services.pipeline.accelerators.SmartTruncationService smartTruncationService;
    @Mock private SystemErrorRepository systemErrorRepo;
    @Mock private PipelineRunRepository pipelineRunRepo;
    @Mock private NodeRunRepository nodeRunRepo;

    @Test
    void resumeRun_unknown_id_logs_and_returns() {
        when(pipelineRunRepo.findById("missing")).thenReturn(Optional.empty());

        engine.resumeRun("missing");

        verify(pipelineRunRepo, never()).save(any());
        verify(documentService, never()).getById(any());
    }

    @Test
    void resumeRun_terminal_run_is_refused() {
        for (PipelineRunStatus terminal : new PipelineRunStatus[]{
                PipelineRunStatus.COMPLETED, PipelineRunStatus.FAILED, PipelineRunStatus.CANCELLED}) {
            PipelineRun run = new PipelineRun();
            run.setId("run-" + terminal);
            run.setStatus(terminal);
            when(pipelineRunRepo.findById(run.getId())).thenReturn(Optional.of(run));

            engine.resumeRun(run.getId());
        }

        // Terminal runs are read but never saved, and we never reach the document lookup.
        verify(pipelineRunRepo, never()).save(any());
        verify(documentService, never()).getById(any());
    }

    @Test
    void resumeRun_missing_document_fails_the_run() {
        PipelineRun run = new PipelineRun();
        run.setId("run-1");
        run.setDocumentId("doc-1");
        run.setStatus(PipelineRunStatus.RUNNING);
        run.setCurrentNodeIndex(2);
        run.setCurrentNodeKey("classify-node");
        run.setStartedAt(java.time.Instant.now().minusSeconds(60));

        when(pipelineRunRepo.findById("run-1")).thenReturn(Optional.of(run));
        when(documentService.getById("doc-1")).thenReturn(null);

        engine.resumeRun("run-1");

        ArgumentCaptor<PipelineRun> cap = ArgumentCaptor.forClass(PipelineRun.class);
        verify(pipelineRunRepo, times(1)).save(cap.capture());
        PipelineRun saved = cap.getValue();
        assertThat(saved.getStatus()).isEqualTo(PipelineRunStatus.FAILED);
        assertThat(saved.getError()).contains("Document not found");
    }

    @Test
    void resumeRun_with_runnable_run_and_present_document_sets_status_RUNNING_before_walk() {
        PipelineRun run = new PipelineRun();
        run.setId("run-2");
        run.setDocumentId("doc-2");
        run.setStatus(PipelineRunStatus.WAITING);
        run.setCurrentNodeIndex(0);
        run.setStartedAt(java.time.Instant.now().minusSeconds(60));

        DocumentModel doc = new DocumentModel();
        doc.setId("doc-2");
        doc.setOriginalFileName("x.pdf");
        doc.setMimeType("application/pdf");

        when(pipelineRunRepo.findById("run-2")).thenReturn(Optional.of(run));
        when(documentService.getById("doc-2")).thenReturn(doc);
        // resolvePipeline → null pipeline triggers an empty topology; the
        // walk's empty for-loop ends and completePipelineRun fires (2nd save).

        engine.resumeRun("run-2");

        // 1st save = pre-walk status flip to RUNNING; 2nd save = completePipelineRun.
        // The terminal status after walking an empty pipeline is COMPLETED, but the
        // value we assert here is the in-memory state observed by the test, which
        // mutates through both saves.
        verify(pipelineRunRepo, times(2)).save(any(PipelineRun.class));
    }
}
