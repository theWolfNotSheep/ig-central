package co.uk.wolfnotsheep.infrastructure;

import co.uk.wolfnotsheep.document.events.LlmJobCompletedEvent;
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
import java.util.Map;
import java.util.UUID;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Integration test for the pipeline resume flow.
 *
 * Simulates the scenario where a PipelineRun is paused in WAITING state
 * (because the LLM classification node dispatched an async job), and
 * an LlmJobCompletedEvent arrives to resume it.
 */
class PipelineResumeIT extends BaseIntegrationTest {

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
    void resumePipelineOnSuccessfulLlmCompletion() {
        // Arrange: create a document in a mid-pipeline state
        DocumentModel doc = new DocumentModel();
        doc.setFileName("resume-test.pdf");
        doc.setOriginalFileName("resume-test.pdf");
        doc.setMimeType("application/pdf");
        doc.setFileSizeBytes(2048);
        doc.setStorageBucket("test-bucket");
        doc.setStorageKey("uploads/resume-test.pdf");
        doc.setStatus(DocumentStatus.PROCESSING);
        doc.setUploadedBy("test-user");
        doc.setOrganisationId("test-org");
        doc.setExtractedText("This is a sample employment contract for John Smith.");
        doc.setCreatedAt(Instant.now());
        doc.setUpdatedAt(Instant.now());
        doc = documentRepository.save(doc);

        String docId = doc.getId();
        String jobId = UUID.randomUUID().toString();
        String correlationId = UUID.randomUUID().toString();

        // Create a PipelineRun in WAITING state
        PipelineRun run = new PipelineRun();
        run.setDocumentId(docId);
        run.setOrganisationId("test-org");
        run.setPipelineId("default-pipeline");
        run.setPipelineVersion(0);
        run.setStatus(PipelineRunStatus.WAITING);
        run.setCorrelationId(correlationId);
        run.setExecutionPlan(List.of("textExtraction", "piiScan", "llmClassification", "enforcement"));
        run.setCurrentNodeIndex(2); // paused at llmClassification
        run.setCurrentNodeKey("llmClassification");
        run.setSharedContext(Map.of(
                "extractedText", "This is a sample employment contract for John Smith.",
                "piiFindings", List.of()
        ));
        run.setStartedAt(Instant.now().minusSeconds(30));
        run.setCreatedAt(Instant.now().minusSeconds(30));
        run.setUpdatedAt(Instant.now().minusSeconds(5));
        run = pipelineRunRepository.save(run);

        String pipelineRunId = run.getId();

        // Create a NodeRun in WAITING state with the jobId
        NodeRun nodeRun = new NodeRun();
        nodeRun.setPipelineRunId(pipelineRunId);
        nodeRun.setDocumentId(docId);
        nodeRun.setNodeKey("llmClassification");
        nodeRun.setNodeType("llmClassification");
        nodeRun.setExecutionCategory("LLM");
        nodeRun.setStatus(NodeRunStatus.WAITING);
        nodeRun.setJobId(jobId);
        nodeRun.setStartedAt(Instant.now().minusSeconds(10));
        nodeRun = nodeRunRepository.save(nodeRun);

        String nodeRunId = nodeRun.getId();

        // Act: publish a successful LlmJobCompletedEvent
        LlmJobCompletedEvent completedEvent = new LlmJobCompletedEvent(
                jobId,
                pipelineRunId,
                nodeRunId,
                true,                          // success
                "classification-result-123",    // classificationResultId
                "cat-hr-contracts",             // categoryId
                "HR > Employment Contracts",    // categoryName
                "INTERNAL",                     // sensitivityLabel
                List.of("hr", "contract"),      // tags
                0.92,                           // confidence
                false,                          // requiresHumanReview
                null,                           // retentionScheduleId
                List.of(),                      // applicablePolicyIds
                Map.of("employee_name", "John Smith"), // extractedMetadata
                null,                           // customResult
                null,                           // error
                Instant.now()
        );

        rabbitTemplate.convertAndSend(
                RabbitMqConfig.PIPELINE_EXCHANGE,
                RabbitMqConfig.ROUTING_LLM_JOB_COMPLETED,
                completedEvent
        );

        // Assert: PipelineRun status should change from WAITING
        await().atMost(15, SECONDS).untilAsserted(() -> {
            PipelineRun updatedRun = pipelineRunRepository.findById(pipelineRunId).orElse(null);
            assertThat(updatedRun).isNotNull();
            assertThat(updatedRun.getStatus()).isNotEqualTo(PipelineRunStatus.WAITING);
        });

        // Assert: NodeRun should be updated with the classification result
        await().atMost(15, SECONDS).untilAsserted(() -> {
            NodeRun updatedNodeRun = nodeRunRepository.findById(nodeRunId).orElse(null);
            assertThat(updatedNodeRun).isNotNull();
            assertThat(updatedNodeRun.getStatus()).isEqualTo(NodeRunStatus.SUCCEEDED);
            assertThat(updatedNodeRun.getCompletedAt()).isNotNull();
            assertThat(updatedNodeRun.getDurationMs()).isGreaterThan(0);

            // The output should contain the classification data
            Map<String, Object> output = updatedNodeRun.getOutput();
            assertThat(output).isNotNull();
            assertThat(output).containsEntry("classificationResultId", "classification-result-123");
            assertThat(output).containsEntry("categoryId", "cat-hr-contracts");
            assertThat(output).containsEntry("categoryName", "HR > Employment Contracts");
            assertThat(output).containsEntry("confidence", 0.92);
        });
    }

    @Test
    void resumePipelineOnFailedLlmCompletion() {
        // Arrange: create a document and a waiting pipeline run
        DocumentModel doc = new DocumentModel();
        doc.setFileName("fail-resume-test.pdf");
        doc.setOriginalFileName("fail-resume-test.pdf");
        doc.setMimeType("application/pdf");
        doc.setFileSizeBytes(1024);
        doc.setStorageBucket("test-bucket");
        doc.setStorageKey("uploads/fail-resume-test.pdf");
        doc.setStatus(DocumentStatus.PROCESSING);
        doc.setUploadedBy("test-user");
        doc.setOrganisationId("test-org");
        doc.setCreatedAt(Instant.now());
        doc.setUpdatedAt(Instant.now());
        doc = documentRepository.save(doc);

        String docId = doc.getId();
        String jobId = UUID.randomUUID().toString();
        String correlationId = UUID.randomUUID().toString();

        PipelineRun run = new PipelineRun();
        run.setDocumentId(docId);
        run.setOrganisationId("test-org");
        run.setPipelineId("default-pipeline");
        run.setPipelineVersion(0);
        run.setStatus(PipelineRunStatus.WAITING);
        run.setCorrelationId(correlationId);
        run.setExecutionPlan(List.of("textExtraction", "piiScan", "llmClassification", "enforcement"));
        run.setCurrentNodeIndex(2);
        run.setCurrentNodeKey("llmClassification");
        run.setSharedContext(Map.of());
        run.setStartedAt(Instant.now().minusSeconds(30));
        run.setCreatedAt(Instant.now().minusSeconds(30));
        run.setUpdatedAt(Instant.now().minusSeconds(5));
        run = pipelineRunRepository.save(run);

        String pipelineRunId = run.getId();

        NodeRun nodeRun = new NodeRun();
        nodeRun.setPipelineRunId(pipelineRunId);
        nodeRun.setDocumentId(docId);
        nodeRun.setNodeKey("llmClassification");
        nodeRun.setNodeType("llmClassification");
        nodeRun.setExecutionCategory("LLM");
        nodeRun.setStatus(NodeRunStatus.WAITING);
        nodeRun.setJobId(jobId);
        nodeRun.setStartedAt(Instant.now().minusSeconds(10));
        nodeRun = nodeRunRepository.save(nodeRun);

        String nodeRunId = nodeRun.getId();

        // Act: publish a FAILED LlmJobCompletedEvent
        LlmJobCompletedEvent failedEvent = LlmJobCompletedEvent.failure(
                jobId, pipelineRunId, nodeRunId,
                "LLM rate limit exceeded"
        );

        rabbitTemplate.convertAndSend(
                RabbitMqConfig.PIPELINE_EXCHANGE,
                RabbitMqConfig.ROUTING_LLM_JOB_COMPLETED,
                failedEvent
        );

        // Assert: PipelineRun should be FAILED
        await().atMost(15, SECONDS).untilAsserted(() -> {
            PipelineRun updatedRun = pipelineRunRepository.findById(pipelineRunId).orElse(null);
            assertThat(updatedRun).isNotNull();
            assertThat(updatedRun.getStatus()).isEqualTo(PipelineRunStatus.FAILED);
            assertThat(updatedRun.getError()).contains("LLM rate limit exceeded");
        });

        // Assert: NodeRun should be FAILED
        await().atMost(15, SECONDS).untilAsserted(() -> {
            NodeRun updatedNodeRun = nodeRunRepository.findById(nodeRunId).orElse(null);
            assertThat(updatedNodeRun).isNotNull();
            assertThat(updatedNodeRun.getStatus()).isEqualTo(NodeRunStatus.FAILED);
            assertThat(updatedNodeRun.getError()).contains("LLM rate limit exceeded");
        });

        // Assert: Document should have error status
        await().atMost(15, SECONDS).untilAsserted(() -> {
            DocumentModel updatedDoc = documentRepository.findById(docId).orElse(null);
            assertThat(updatedDoc).isNotNull();
            assertThat(updatedDoc.getStatus()).isEqualTo(DocumentStatus.CLASSIFICATION_FAILED);
        });
    }
}
