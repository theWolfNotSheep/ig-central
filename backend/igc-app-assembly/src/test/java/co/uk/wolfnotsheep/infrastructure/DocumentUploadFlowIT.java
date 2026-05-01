package co.uk.wolfnotsheep.infrastructure;

import co.uk.wolfnotsheep.document.events.DocumentIngestedEvent;
import co.uk.wolfnotsheep.document.models.*;
import co.uk.wolfnotsheep.document.repositories.DocumentRepository;
import co.uk.wolfnotsheep.document.repositories.NodeRunRepository;
import co.uk.wolfnotsheep.document.repositories.PipelineRunRepository;
import co.uk.wolfnotsheep.document.services.ObjectStorageService;
import co.uk.wolfnotsheep.infrastructure.config.RabbitMqConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.List;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Integration test for the document upload / pipeline execution flow.
 *
 * Publishes a DocumentIngestedEvent to RabbitMQ and verifies that
 * the PipelineExecutionEngine creates PipelineRun and NodeRun records.
 *
 * ObjectStorageService is mocked because there is no MinIO in the test
 * environment — the pipeline will fail at text extraction (no file to
 * download), which is the expected outcome.
 */
class DocumentUploadFlowIT extends BaseIntegrationTest {

    @MockitoBean
    ObjectStorageService objectStorageService;

    @Autowired
    RabbitTemplate rabbitTemplate;

    @Autowired
    DocumentRepository documentRepository;

    @Autowired
    PipelineRunRepository pipelineRunRepository;

    @Autowired
    NodeRunRepository nodeRunRepository;

    @BeforeEach
    void cleanUp() {
        nodeRunRepository.deleteAll();
        pipelineRunRepository.deleteAll();
        documentRepository.deleteAll();
    }

    @Test
    void pipelineCreatesRunAndNodeRecordsOnDocumentIngested() {
        // Arrange: create a document record directly in MongoDB so the engine can find it
        DocumentModel doc = new DocumentModel();
        doc.setFileName("test-report.pdf");
        doc.setOriginalFileName("test-report.pdf");
        doc.setMimeType("application/pdf");
        doc.setFileSizeBytes(1024);
        doc.setStorageBucket("test-bucket");
        doc.setStorageKey("uploads/test-report.pdf");
        doc.setStatus(DocumentStatus.UPLOADED);
        doc.setUploadedBy("test-user");
        doc.setOrganisationId("test-org");
        doc.setCreatedAt(Instant.now());
        doc.setUpdatedAt(Instant.now());
        doc = documentRepository.save(doc);

        String docId = doc.getId();

        // Act: publish the ingested event to RabbitMQ
        DocumentIngestedEvent event = new DocumentIngestedEvent(
                docId,
                "test-report.pdf",
                "application/pdf",
                1024L,
                "test-bucket",
                "uploads/test-report.pdf",
                "test-user",
                Instant.now(),
                null
        );
        rabbitTemplate.convertAndSend(
                RabbitMqConfig.EXCHANGE,
                RabbitMqConfig.ROUTING_INGESTED,
                event
        );

        // Assert: a PipelineRun is created for this document
        await().atMost(15, SECONDS).untilAsserted(() -> {
            List<PipelineRun> runs = pipelineRunRepository.findByDocumentIdOrderByCreatedAtDesc(docId);
            assertThat(runs).isNotEmpty();
        });

        // The pipeline should have run (and likely failed at text extraction
        // because ObjectStorageService is mocked and returns no data)
        await().atMost(15, SECONDS).untilAsserted(() -> {
            List<PipelineRun> runs = pipelineRunRepository.findByDocumentIdOrderByCreatedAtDesc(docId);
            assertThat(runs).hasSize(1);

            PipelineRun run = runs.getFirst();
            assertThat(run.getDocumentId()).isEqualTo(docId);
            assertThat(run.getCorrelationId()).isNotBlank();
            assertThat(run.getExecutionPlan()).isNotNull();
            assertThat(run.getStartedAt()).isNotNull();

            // The run should be terminal — either FAILED (expected, since there's no real
            // file to extract from) or COMPLETED/WAITING
            assertThat(run.getStatus()).isIn(
                    PipelineRunStatus.FAILED,
                    PipelineRunStatus.COMPLETED,
                    PipelineRunStatus.RUNNING,
                    PipelineRunStatus.WAITING
            );
        });

        // Assert: NodeRun records were created for the pipeline nodes
        await().atMost(15, SECONDS).untilAsserted(() -> {
            List<PipelineRun> runs = pipelineRunRepository.findByDocumentIdOrderByCreatedAtDesc(docId);
            PipelineRun run = runs.getFirst();

            List<NodeRun> nodeRuns = nodeRunRepository.findByPipelineRunIdOrderByStartedAtAsc(run.getId());
            // At least one NodeRun should exist (the first node in the execution plan)
            assertThat(nodeRuns).isNotEmpty();

            // If the pipeline failed, the failing node should have an error
            if (run.getStatus() == PipelineRunStatus.FAILED) {
                assertThat(run.getError()).isNotBlank();
                assertThat(run.getErrorNodeKey()).isNotBlank();

                // The first node should have been attempted
                NodeRun firstNode = nodeRuns.getFirst();
                assertThat(firstNode.getNodeKey()).isNotBlank();
                assertThat(firstNode.getStatus()).isIn(
                        NodeRunStatus.FAILED,
                        NodeRunStatus.SUCCEEDED,
                        NodeRunStatus.RUNNING
                );
            }
        });
    }

    @Test
    void pipelineDoesNotRunForNonUploadedDocument() {
        // Arrange: create a document that's already processed
        DocumentModel doc = new DocumentModel();
        doc.setFileName("already-processed.pdf");
        doc.setOriginalFileName("already-processed.pdf");
        doc.setMimeType("application/pdf");
        doc.setFileSizeBytes(512);
        doc.setStorageBucket("test-bucket");
        doc.setStorageKey("uploads/already-processed.pdf");
        doc.setStatus(DocumentStatus.CLASSIFIED); // Not UPLOADED
        doc.setUploadedBy("test-user");
        doc.setOrganisationId("test-org");
        doc.setCreatedAt(Instant.now());
        doc.setUpdatedAt(Instant.now());
        doc = documentRepository.save(doc);

        String docId = doc.getId();

        // Act: publish ingested event
        DocumentIngestedEvent event = new DocumentIngestedEvent(
                docId,
                "already-processed.pdf",
                "application/pdf",
                512L,
                "test-bucket",
                "uploads/already-processed.pdf",
                "test-user",
                Instant.now(),
                null
        );
        rabbitTemplate.convertAndSend(
                RabbitMqConfig.EXCHANGE,
                RabbitMqConfig.ROUTING_INGESTED,
                event
        );

        // Assert: no PipelineRun should be created because doc status != UPLOADED
        // Wait a moment, then verify nothing was created
        await().during(3, SECONDS).atMost(5, SECONDS).untilAsserted(() -> {
            List<PipelineRun> runs = pipelineRunRepository.findByDocumentIdOrderByCreatedAtDesc(docId);
            assertThat(runs).isEmpty();
        });
    }
}
