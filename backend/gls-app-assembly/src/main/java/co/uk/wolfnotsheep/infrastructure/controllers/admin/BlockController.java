package co.uk.wolfnotsheep.infrastructure.controllers.admin;

import co.uk.wolfnotsheep.governance.models.BlockFeedback;
import co.uk.wolfnotsheep.governance.models.PipelineBlock;
import co.uk.wolfnotsheep.governance.models.PipelineBlock.BlockType;
import co.uk.wolfnotsheep.governance.models.PipelineBlock.BlockVersion;
import co.uk.wolfnotsheep.governance.repositories.BlockFeedbackRepository;
import co.uk.wolfnotsheep.governance.repositories.PipelineBlockRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/blocks")
public class BlockController {

    private static final Logger log = LoggerFactory.getLogger(BlockController.class);

    private final PipelineBlockRepository blockRepo;
    private final BlockFeedbackRepository feedbackRepo;
    private final co.uk.wolfnotsheep.platform.config.services.AppConfigService configService;

    @org.springframework.beans.factory.annotation.Value("${spring.ai.anthropic.api-key:}")
    private String anthropicApiKey;

    public BlockController(PipelineBlockRepository blockRepo, BlockFeedbackRepository feedbackRepo,
                           co.uk.wolfnotsheep.platform.config.services.AppConfigService configService) {
        this.blockRepo = blockRepo;
        this.feedbackRepo = feedbackRepo;
        this.configService = configService;
    }

    // ── Block CRUD ───────────────────────────────────────

    @GetMapping
    public ResponseEntity<List<PipelineBlock>> listBlocks(
            @RequestParam(required = false) String type) {
        if (type != null && !type.isBlank()) {
            return ResponseEntity.ok(blockRepo.findByTypeAndActiveTrueOrderByNameAsc(BlockType.valueOf(type)));
        }
        return ResponseEntity.ok(blockRepo.findByActiveTrueOrderByNameAsc());
    }

    @GetMapping("/all")
    public ResponseEntity<List<PipelineBlock>> listAllBlocks() {
        return ResponseEntity.ok(blockRepo.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<PipelineBlock> getBlock(@PathVariable String id) {
        return blockRepo.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<PipelineBlock> createBlock(@RequestBody CreateBlockRequest request, Authentication auth) {
        PipelineBlock block = new PipelineBlock();
        block.setName(request.name());
        block.setDescription(request.description());
        block.setType(request.type());
        block.setActive(true);
        block.setActiveVersion(0);
        block.setVersions(new ArrayList<>());
        block.setCreatedAt(Instant.now());
        block.setUpdatedAt(Instant.now());
        block.setCreatedBy(auth != null ? auth.getName() : "SYSTEM");

        // If initial content provided, publish as v1
        if (request.content() != null && !request.content().isEmpty()) {
            block.getVersions().add(new BlockVersion(
                    1, request.content(), "Initial version",
                    auth != null ? auth.getName() : "SYSTEM", Instant.now()));
            block.setActiveVersion(1);
        }

        return ResponseEntity.ok(blockRepo.save(block));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PipelineBlock> updateBlock(@PathVariable String id, @RequestBody UpdateBlockRequest request) {
        return blockRepo.findById(id)
                .map(block -> {
                    if (request.name() != null) block.setName(request.name());
                    if (request.description() != null) block.setDescription(request.description());
                    block.setUpdatedAt(Instant.now());
                    return ResponseEntity.ok(blockRepo.save(block));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivateBlock(@PathVariable String id) {
        return blockRepo.findById(id)
                .map(block -> {
                    block.setActive(false);
                    block.setUpdatedAt(Instant.now());
                    blockRepo.save(block);
                    return ResponseEntity.ok().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ── Draft & Publishing ───────────────────────────────

    @PutMapping("/{id}/draft")
    public ResponseEntity<PipelineBlock> saveDraft(
            @PathVariable String id, @RequestBody DraftRequest request) {
        return blockRepo.findById(id)
                .map(block -> {
                    block.setDraftContent(request.content());
                    block.setDraftChangelog(request.changelog());
                    block.setUpdatedAt(Instant.now());
                    return ResponseEntity.ok(blockRepo.save(block));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/publish")
    public ResponseEntity<PipelineBlock> publishDraft(
            @PathVariable String id, Authentication auth) {
        return blockRepo.findById(id)
                .map(block -> {
                    Map<String, Object> content = block.getDraftContent();
                    if (content == null || content.isEmpty()) {
                        return ResponseEntity.badRequest().<PipelineBlock>build();
                    }

                    int nextVersion = block.getVersions().stream()
                            .mapToInt(BlockVersion::version).max().orElse(0) + 1;

                    List<BlockVersion> versions = new ArrayList<>(block.getVersions());
                    versions.add(new BlockVersion(
                            nextVersion, content,
                            block.getDraftChangelog() != null ? block.getDraftChangelog() : "Version " + nextVersion,
                            auth != null ? auth.getName() : "SYSTEM",
                            Instant.now()));

                    block.setVersions(versions);
                    block.setActiveVersion(nextVersion);
                    block.setDraftContent(null);
                    block.setDraftChangelog(null);
                    block.setUpdatedAt(Instant.now());

                    return ResponseEntity.ok(blockRepo.save(block));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/rollback/{version}")
    public ResponseEntity<PipelineBlock> rollback(
            @PathVariable String id, @PathVariable int version) {
        return blockRepo.findById(id)
                .map(block -> {
                    if (block.getVersionContent(version) == null) {
                        return ResponseEntity.badRequest().<PipelineBlock>build();
                    }
                    block.setActiveVersion(version);
                    block.setUpdatedAt(Instant.now());
                    return ResponseEntity.ok(blockRepo.save(block));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ── AI Improvement ────────────────────────────────────

    @PostMapping("/{id}/improve")
    public ResponseEntity<Map<String, Object>> improveWithAi(@PathVariable String id) {
        return blockRepo.findById(id).map(block -> {
            Map<String, Object> activeContent = block.getActiveContent();
            if (activeContent == null) {
                return ResponseEntity.badRequest().<Map<String, Object>>body(Map.of("error", "No active version"));
            }

            // Collect feedback since this version was published
            List<BlockFeedback> allFeedback = feedbackRepo.findByBlockIdAndBlockVersion(
                    id, block.getActiveVersion());

            if (allFeedback.isEmpty()) {
                return ResponseEntity.ok(Map.<String, Object>of(
                        "status", "no_feedback",
                        "message", "No feedback for the current version — nothing to improve on"
                ));
            }

            // Build the improvement prompt
            StringBuilder prompt = new StringBuilder();
            prompt.append("You are an expert at improving document processing configurations.\n\n");

            if (block.getType() == BlockType.PROMPT) {
                prompt.append("Below is a classification prompt used by an AI to classify documents. ");
                prompt.append("Users have provided corrections indicating where the prompt led to wrong results.\n\n");
                prompt.append("## Current Prompt Content\n\n");
                prompt.append("System prompt:\n```\n").append(activeContent.getOrDefault("systemPrompt", "")).append("\n```\n\n");
                prompt.append("User prompt template:\n```\n").append(activeContent.getOrDefault("userPromptTemplate", "")).append("\n```\n\n");
            } else if (block.getType() == BlockType.REGEX_SET) {
                prompt.append("Below are regex patterns used for PII detection. ");
                prompt.append("Users have flagged false positives and missed detections.\n\n");
                prompt.append("## Current Patterns\n\n```json\n").append(activeContent).append("\n```\n\n");
            } else {
                prompt.append("Below is a pipeline block configuration.\n\n");
                prompt.append("## Current Configuration\n\n```json\n").append(activeContent).append("\n```\n\n");
            }

            prompt.append("## User Feedback (").append(allFeedback.size()).append(" items)\n\n");
            for (BlockFeedback fb : allFeedback) {
                prompt.append("- [").append(fb.getType()).append("] ").append(fb.getDetails());
                if (fb.getOriginalValue() != null) prompt.append(" (was: ").append(fb.getOriginalValue()).append(")");
                if (fb.getCorrectedValue() != null) prompt.append(" (should be: ").append(fb.getCorrectedValue()).append(")");
                prompt.append("\n");
            }

            prompt.append("\n## Task\n\n");
            if (block.getType() == BlockType.PROMPT) {
                prompt.append("Suggest an improved version of the system prompt that addresses the user corrections. ");
                prompt.append("Return ONLY the improved system prompt text, nothing else. Keep the same structure and tool-calling workflow.");
            } else if (block.getType() == BlockType.REGEX_SET) {
                prompt.append("Suggest improvements to the regex patterns: add new patterns for missed detections, ");
                prompt.append("adjust confidence scores for false positives, or modify patterns. ");
                prompt.append("Return the improved patterns as a JSON array.");
            } else {
                prompt.append("Suggest an improved configuration. Return as JSON.");
            }

            // Call LLM
            try {
                String suggestion = callLlm(prompt.toString());

                // Save as draft
                Map<String, Object> draftContent;
                if (block.getType() == BlockType.PROMPT) {
                    draftContent = new java.util.HashMap<>(activeContent);
                    draftContent.put("systemPrompt", suggestion);
                } else {
                    draftContent = Map.of("_aiSuggestion", suggestion, "_originalContent", activeContent);
                }

                block.setDraftContent(draftContent);
                block.setDraftChangelog("AI-suggested improvement based on " + allFeedback.size() + " feedback items");
                block.setUpdatedAt(Instant.now());
                blockRepo.save(block);

                return ResponseEntity.ok(Map.<String, Object>of(
                        "status", "draft_created",
                        "feedbackUsed", allFeedback.size(),
                        "message", "AI suggestion saved as draft — review and publish when ready"
                ));

            } catch (Exception e) {
                log.error("AI improvement failed: {}", e.getMessage());
                return ResponseEntity.internalServerError().<Map<String, Object>>body(Map.of(
                        "error", "AI call failed: " + e.getMessage()
                ));
            }
        }).orElse(ResponseEntity.notFound().build());
    }

    private String callLlm(String prompt) throws Exception {
        String escapedPrompt = prompt.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");

        String provider = configService.getValue("llm.provider", "anthropic");
        if ("ollama".equalsIgnoreCase(provider)) {
            return callOllama(escapedPrompt);
        }

        String apiKey = configService.getValue("llm.anthropic.api_key", anthropicApiKey);
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("No LLM configured. Set Anthropic API key or switch to Ollama in Settings.");
        }
        String model = configService.getValue("llm.anthropic.model", "claude-sonnet-4-20250514");

        String requestBody = """
                {"model":"%s","max_tokens":4096,"messages":[{"role":"user","content":"%s"}]}
                """.formatted(model, escapedPrompt);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.anthropic.com/v1/messages"))
                .header("Content-Type", "application/json")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = java.net.http.HttpClient.newHttpClient()
                .send(request, java.net.http.HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("LLM API error: " + response.statusCode());
        }

        String body = response.body();
        int textStart = body.indexOf("\"text\":\"") + 8;
        int textEnd = body.indexOf("\"", textStart);
        if (textStart < 8 || textEnd < 0) return body;
        return body.substring(textStart, textEnd).replace("\\n", "\n").replace("\\\"", "\"");
    }

    private String callOllama(String escapedPrompt) throws Exception {
        String baseUrl = configService.getValue("llm.ollama.base_url", "http://localhost:11434");
        String model = configService.getValue("llm.ollama.model", "qwen2.5:32b");

        String requestBody = """
                {"model":"%s","prompt":"%s","stream":false}
                """.formatted(model, escapedPrompt);

        HttpRequest request = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create(baseUrl + "/api/generate"))
                .header("Content-Type", "application/json")
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = java.net.http.HttpClient.newHttpClient()
                .send(request, java.net.http.HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Ollama API error: " + response.statusCode());
        }

        String body = response.body();
        int start = body.indexOf("\"response\":\"") + 12;
        int end = body.indexOf("\",\"done\"");
        if (start >= 12 && end > start) {
            return body.substring(start, end).replace("\\n", "\n").replace("\\\"", "\"").replace("\\t", "\t");
        }
        return body;
    }

    // ── Version Comparison ───────────────────────────────

    @GetMapping("/{id}/compare/{v1}/{v2}")
    public ResponseEntity<Map<String, Object>> compareVersions(
            @PathVariable String id, @PathVariable int v1, @PathVariable int v2) {
        return blockRepo.findById(id).map(block -> {
            Map<String, Object> content1 = block.getVersionContent(v1);
            Map<String, Object> content2 = block.getVersionContent(v2);
            if (content1 == null || content2 == null) {
                return ResponseEntity.badRequest().<Map<String, Object>>body(Map.of("error", "Version not found"));
            }
            return ResponseEntity.ok(Map.<String, Object>of(
                    "version1", v1, "content1", content1,
                    "version2", v2, "content2", content2
            ));
        }).orElse(ResponseEntity.notFound().build());
    }

    // ── Feedback ─────────────────────────────────────────

    @GetMapping("/{id}/feedback")
    public ResponseEntity<Page<BlockFeedback>> getBlockFeedback(
            @PathVariable String id, Pageable pageable) {
        return ResponseEntity.ok(feedbackRepo.findByBlockIdOrderByTimestampDesc(id, pageable));
    }

    @PostMapping("/{id}/feedback")
    public ResponseEntity<BlockFeedback> addFeedback(
            @PathVariable String id, @RequestBody BlockFeedback feedback, Authentication auth) {
        feedback.setBlockId(id);
        if (auth != null) {
            feedback.setUserId(auth.getName());
            feedback.setUserEmail(auth.getName());
        }
        feedback.setTimestamp(Instant.now());

        BlockFeedback saved = feedbackRepo.save(feedback);

        // Update feedback count on block
        blockRepo.findById(id).ifPresent(block -> {
            block.setFeedbackCount(feedbackRepo.countByBlockId(id));
            blockRepo.save(block);
        });

        return ResponseEntity.ok(saved);
    }

    @GetMapping("/{id}/feedback/summary")
    public ResponseEntity<Map<String, Object>> getFeedbackSummary(@PathVariable String id) {
        long total = feedbackRepo.countByBlockId(id);
        List<BlockFeedback> recent = feedbackRepo.findByBlockIdOrderByTimestampDesc(id).stream()
                .limit(5).toList();
        long corrections = feedbackRepo.findByBlockIdAndType(id, BlockFeedback.FeedbackType.CORRECTION).size();
        long falsePositives = feedbackRepo.findByBlockIdAndType(id, BlockFeedback.FeedbackType.FALSE_POSITIVE).size();
        long missed = feedbackRepo.findByBlockIdAndType(id, BlockFeedback.FeedbackType.MISSED).size();

        return ResponseEntity.ok(Map.of(
                "total", total,
                "corrections", corrections,
                "falsePositives", falsePositives,
                "missed", missed,
                "recent", recent
        ));
    }

    // ── DTOs ─────────────────────────────────────────────

    record CreateBlockRequest(String name, String description, BlockType type, Map<String, Object> content) {}
    record UpdateBlockRequest(String name, String description) {}
    record DraftRequest(Map<String, Object> content, String changelog) {}
}
