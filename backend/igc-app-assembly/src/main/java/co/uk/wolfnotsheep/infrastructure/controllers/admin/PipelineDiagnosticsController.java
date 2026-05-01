package co.uk.wolfnotsheep.infrastructure.controllers.admin;

import co.uk.wolfnotsheep.document.models.DocumentStatus;
import co.uk.wolfnotsheep.document.repositories.DocumentRepository;
import co.uk.wolfnotsheep.document.repositories.SystemErrorRepository;
import co.uk.wolfnotsheep.governance.models.PipelineBlock;
import co.uk.wolfnotsheep.governance.models.PipelineDefinition;
import co.uk.wolfnotsheep.governance.repositories.PipelineBlockRepository;
import co.uk.wolfnotsheep.governance.repositories.PipelineDefinitionRepository;
import co.uk.wolfnotsheep.platform.config.services.AppConfigService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Runs automated diagnostics on the AI/pipeline configuration and returns
 * actionable issues and recommendations for the admin UI.
 */
@RestController
@RequestMapping("/api/admin/ai/diagnostics")
public class PipelineDiagnosticsController {

    private final AppConfigService configService;
    private final PipelineDefinitionRepository pipelineRepo;
    private final PipelineBlockRepository blockRepo;
    private final DocumentRepository documentRepo;
    private final SystemErrorRepository systemErrorRepo;
    private final HttpClient httpClient;
    private final boolean executionEngineEnabled;

    public PipelineDiagnosticsController(AppConfigService configService,
                                          PipelineDefinitionRepository pipelineRepo,
                                          PipelineBlockRepository blockRepo,
                                          DocumentRepository documentRepo,
                                          SystemErrorRepository systemErrorRepo,
                                          @Value("${pipeline.execution-engine.enabled:false}") boolean executionEngineEnabled) {
        this.configService = configService;
        this.pipelineRepo = pipelineRepo;
        this.blockRepo = blockRepo;
        this.documentRepo = documentRepo;
        this.systemErrorRepo = systemErrorRepo;
        this.executionEngineEnabled = executionEngineEnabled;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
    }

    public record DiagnosticItem(String severity, String category, String title, String detail, String action) {}

    @GetMapping
    public ResponseEntity<Map<String, Object>> runDiagnostics() {
        List<DiagnosticItem> items = new ArrayList<>();

        checkPipelineDefinitions(items);
        checkLlmProvider(items);
        checkPipelineBlocks(items);
        checkStuckDocuments(items);
        checkRecentErrors(items);
        checkExecutionEngine(items);

        long errors = items.stream().filter(i -> "error".equals(i.severity())).count();
        long warnings = items.stream().filter(i -> "warning".equals(i.severity())).count();
        long infos = items.stream().filter(i -> "info".equals(i.severity())).count();

        String overallStatus = errors > 0 ? "error" : warnings > 0 ? "warning" : "healthy";

        return ResponseEntity.ok(Map.of(
                "status", overallStatus,
                "items", items,
                "counts", Map.of("errors", errors, "warnings", warnings, "info", infos),
                "checkedAt", Instant.now().toString()
        ));
    }

    private void checkPipelineDefinitions(List<DiagnosticItem> items) {
        List<PipelineDefinition> pipelines = pipelineRepo.findAll();

        if (pipelines.isEmpty()) {
            items.add(new DiagnosticItem("error", "pipeline",
                    "No pipeline definitions found",
                    "No pipelines exist in the system. Documents cannot be processed without a pipeline.",
                    "Go to AI > Pipelines and create a pipeline, or import one from the Governance Hub."));
            return;
        }

        for (PipelineDefinition pipeline : pipelines) {
            if (!pipeline.isActive()) continue;

            boolean hasVisualNodes = pipeline.getVisualNodes() != null && !pipeline.getVisualNodes().isEmpty();
            boolean hasSteps = pipeline.getSteps() != null && !pipeline.getSteps().isEmpty();

            if (executionEngineEnabled && !hasVisualNodes) {
                items.add(new DiagnosticItem("error", "pipeline",
                        "Pipeline '" + pipeline.getName() + "' has no visual nodes",
                        "The pipeline execution engine is enabled but this pipeline has no visual graph nodes configured. " +
                                "Documents assigned to this pipeline will not be processed.",
                        "Open the pipeline in AI > Pipelines and configure the visual node editor, " +
                                "or disable the execution engine to use the legacy step-based pipeline."));
            }

            if (!executionEngineEnabled && !hasSteps) {
                items.add(new DiagnosticItem("error", "pipeline",
                        "Pipeline '" + pipeline.getName() + "' has no processing steps",
                        "The legacy pipeline is active but this pipeline has no steps configured.",
                        "Open the pipeline in AI > Pipelines and add processing steps."));
            }

            if (executionEngineEnabled && hasVisualNodes) {
                // Check for required node types
                Set<String> nodeTypes = new HashSet<>();
                for (var node : pipeline.getVisualNodes()) {
                    nodeTypes.add(node.type());
                }

                if (!nodeTypes.contains("trigger")) {
                    items.add(new DiagnosticItem("warning", "pipeline",
                            "Pipeline '" + pipeline.getName() + "' missing trigger node",
                            "A trigger node is the entry point for the pipeline. Without it, execution may not start correctly.",
                            "Add a Trigger node to the pipeline graph."));
                }
                if (!nodeTypes.contains("textExtraction")) {
                    items.add(new DiagnosticItem("warning", "pipeline",
                            "Pipeline '" + pipeline.getName() + "' missing text extraction",
                            "Without text extraction, documents cannot be read or classified.",
                            "Add a Text Extraction node to the pipeline graph."));
                }
                if (!nodeTypes.contains("aiClassification")) {
                    items.add(new DiagnosticItem("warning", "pipeline",
                            "Pipeline '" + pipeline.getName() + "' missing AI classification",
                            "Without an AI Classification node, documents will be extracted but not classified.",
                            "Add an AI Classification node to the pipeline graph."));
                }
            }
        }
    }

    private void checkLlmProvider(List<DiagnosticItem> items) {
        String provider = String.valueOf(configService.getValue("llm.provider", "anthropic"));

        if ("anthropic".equalsIgnoreCase(provider)) {
            String apiKey = String.valueOf(configService.getValue("llm.anthropic.api_key", ""));
            if (apiKey.isBlank()) {
                items.add(new DiagnosticItem("error", "llm",
                        "No Anthropic API key configured",
                        "The LLM provider is set to Anthropic but no API key is set. Classification will fail.",
                        "Go to AI > Settings and enter your Anthropic API key."));
            }
        } else if ("ollama".equalsIgnoreCase(provider)) {
            String ollamaUrl = getOllamaBaseUrl();
            boolean reachable = checkOllamaReachable(ollamaUrl);
            if (!reachable) {
                items.add(new DiagnosticItem("error", "llm",
                        "Ollama is not reachable",
                        "The LLM provider is set to Ollama but the server at " + ollamaUrl + " is not responding.",
                        "Start Ollama on your machine, or update the Ollama URL in AI > Settings."));
            }

            String model = String.valueOf(configService.getValue("llm.ollama.model", ""));
            if (model.isBlank()) {
                items.add(new DiagnosticItem("warning", "llm",
                        "No Ollama model selected",
                        "Ollama is configured as the provider but no model is selected.",
                        "Go to AI > Settings and select or pull an Ollama model."));
            }
        }
    }

    private void checkPipelineBlocks(List<DiagnosticItem> items) {
        // Check for required block types
        Map<PipelineBlock.BlockType, Long> blockCounts = new EnumMap<>(PipelineBlock.BlockType.class);
        for (PipelineBlock.BlockType type : PipelineBlock.BlockType.values()) {
            blockCounts.put(type, blockRepo.countByTypeAndActiveTrue(type));
        }

        if (blockCounts.getOrDefault(PipelineBlock.BlockType.PROMPT, 0L) == 0) {
            items.add(new DiagnosticItem("warning", "blocks",
                    "No active PROMPT blocks",
                    "There are no active prompt blocks. The LLM classification step needs a prompt block to define how documents are classified.",
                    "Go to AI > Blocks and create or activate a PROMPT block."));
        }

        if (blockCounts.getOrDefault(PipelineBlock.BlockType.REGEX_SET, 0L) == 0) {
            items.add(new DiagnosticItem("info", "blocks",
                    "No active REGEX_SET blocks",
                    "No regex pattern sets are active. PII detection relies on regex patterns to find sensitive data.",
                    "Go to AI > Blocks and create or activate a REGEX_SET block for PII detection."));
        }
    }

    private void checkStuckDocuments(List<DiagnosticItem> items) {
        Instant tenMinAgo = Instant.now().minus(10, ChronoUnit.MINUTES);

        long stuckProcessing = documentRepo.countByStatusAndUpdatedAtBefore(DocumentStatus.PROCESSING, tenMinAgo);
        long stuckClassifying = documentRepo.countByStatusAndUpdatedAtBefore(DocumentStatus.CLASSIFYING, tenMinAgo);
        long failedProcessing = documentRepo.countByStatus(DocumentStatus.PROCESSING_FAILED);
        long failedClassification = documentRepo.countByStatus(DocumentStatus.CLASSIFICATION_FAILED);
        long failedEnforcement = documentRepo.countByStatus(DocumentStatus.ENFORCEMENT_FAILED);

        long stuckTotal = stuckProcessing + stuckClassifying;
        long failedTotal = failedProcessing + failedClassification + failedEnforcement;

        if (stuckTotal > 0) {
            items.add(new DiagnosticItem("warning", "documents",
                    stuckTotal + " document(s) stuck in processing",
                    stuckProcessing + " in PROCESSING, " + stuckClassifying + " in CLASSIFYING for over 10 minutes.",
                    "Go to Monitoring and use 'Reset Stale' to re-queue them, or check the pipeline service logs."));
        }

        if (failedTotal > 0) {
            items.add(new DiagnosticItem("warning", "documents",
                    failedTotal + " document(s) in failed state",
                    failedProcessing + " PROCESSING_FAILED, " + failedClassification + " CLASSIFICATION_FAILED, " +
                            failedEnforcement + " ENFORCEMENT_FAILED.",
                    "Go to Monitoring to review errors and retry failed documents."));
        }

        // Check for documents stuck at UPLOADED (never picked up)
        Instant thirtyMinAgo = Instant.now().minus(30, ChronoUnit.MINUTES);
        long stuckUploaded = documentRepo.countByStatusAndUpdatedAtBefore(DocumentStatus.UPLOADED, thirtyMinAgo);
        if (stuckUploaded > 0) {
            items.add(new DiagnosticItem("warning", "documents",
                    stuckUploaded + " document(s) stuck at UPLOADED",
                    "These documents were uploaded over 30 minutes ago but never entered the processing pipeline.",
                    "This may indicate a pipeline configuration issue or that the message queue is not delivering. Check the pipeline diagnostics above."));
        }
    }

    private void checkRecentErrors(List<DiagnosticItem> items) {
        long unresolvedErrors = systemErrorRepo.countByResolvedFalse();
        long criticalErrors = systemErrorRepo.countByResolvedFalseAndSeverity("CRITICAL");

        if (criticalErrors > 0) {
            items.add(new DiagnosticItem("error", "system",
                    criticalErrors + " unresolved critical error(s)",
                    "There are critical system errors that need attention.",
                    "Go to Monitoring > Errors to review and resolve them."));
        } else if (unresolvedErrors > 5) {
            items.add(new DiagnosticItem("warning", "system",
                    unresolvedErrors + " unresolved system errors",
                    "There are accumulated system errors that should be reviewed.",
                    "Go to Monitoring > Errors to review and resolve them."));
        }
    }

    private void checkExecutionEngine(List<DiagnosticItem> items) {
        items.add(new DiagnosticItem("info", "pipeline",
                "Pipeline mode: " + (executionEngineEnabled ? "Execution Engine (visual graph)" : "Legacy (step-based)"),
                executionEngineEnabled
                        ? "The visual pipeline execution engine is active. Pipelines must have visual nodes configured."
                        : "The legacy step-based pipeline is active. Processing uses the ordered steps list.",
                executionEngineEnabled
                        ? "Configure pipelines using the visual node editor in AI > Pipelines."
                        : "To use the visual pipeline editor, enable the execution engine in the application config."));
    }

    private String getOllamaBaseUrl() {
        String envUrl = System.getenv("OLLAMA_BASE_URL");
        String fallback = envUrl != null ? envUrl : "http://localhost:11434";
        return String.valueOf(configService.getValue("llm.ollama.base_url", fallback));
    }

    private boolean checkOllamaReachable(String baseUrl) {
        try {
            HttpResponse<String> resp = httpClient.send(
                    HttpRequest.newBuilder().uri(URI.create(baseUrl + "/api/tags"))
                            .timeout(Duration.ofSeconds(3)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            return resp.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }
}
