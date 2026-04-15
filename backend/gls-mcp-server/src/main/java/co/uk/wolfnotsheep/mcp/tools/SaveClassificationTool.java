package co.uk.wolfnotsheep.mcp.tools;

import co.uk.wolfnotsheep.document.models.DocumentModel;
import co.uk.wolfnotsheep.document.models.PiiEntity;
import co.uk.wolfnotsheep.governance.models.ClassificationCategory;
import co.uk.wolfnotsheep.governance.models.DocumentClassificationResult;
import co.uk.wolfnotsheep.governance.models.SensitivityLabel;
import co.uk.wolfnotsheep.governance.repositories.ClassificationCategoryRepository;
import co.uk.wolfnotsheep.governance.services.GovernanceService;
import co.uk.wolfnotsheep.mcp.ToolCallLogger;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class SaveClassificationTool {

    private final GovernanceService governanceService;
    private final ClassificationCategoryRepository categoryRepo;
    private final MongoTemplate mongoTemplate;
    private final ToolCallLogger toolLog;

    public SaveClassificationTool(GovernanceService governanceService,
                                  ClassificationCategoryRepository categoryRepo,
                                  MongoTemplate mongoTemplate,
                                  ToolCallLogger toolLog) {
        this.governanceService = governanceService;
        this.categoryRepo = categoryRepo;
        this.mongoTemplate = mongoTemplate;
        this.toolLog = toolLog;
    }

    @McpTool(name = "save_classification_result",
            description = "Persist the classification result for a document. Call this after you have determined the " +
                    "category, sensitivity, tags, and applicable policies. This creates an immutable audit record.")
    public String saveClassification(
            @McpToolParam(description = "The ID of the document being classified")
            String documentId,
            @McpToolParam(description = "The classification category ID from the taxonomy")
            String categoryId,
            @McpToolParam(description = "The human-readable category name")
            String categoryName,
            @McpToolParam(description = "The sensitivity label: PUBLIC, INTERNAL, CONFIDENTIAL, or RESTRICTED")
            String sensitivityLabel,
            @McpToolParam(description = "Confidence score between 0.0 and 1.0")
            double confidence,
            @McpToolParam(description = "Explanation of why this classification was chosen")
            String reasoning,
            @McpToolParam(description = "Comma-separated tags extracted from the document", required = false)
            String tags,
            @McpToolParam(description = "The retention schedule ID to apply", required = false)
            String retentionScheduleId,
            @McpToolParam(description = "JSON string of extracted metadata key-value pairs from the metadata schema. " +
                    "E.g. {\"employee_name\":\"John Smith\",\"leave_type\":\"maternity\",\"start_date\":\"2026-06-01\"}", required = false)
            String extractedMetadata,
            @McpToolParam(description = "Comma-separated document traits detected (e.g. FINAL,INBOUND,ORIGINAL). " +
                    "Get available traits from get_document_traits tool.", required = false)
            String traits,
            @McpToolParam(description = "A brief 1-2 sentence summary of the document's content and purpose. " +
                    "This is shown to users in the document list and detail views.", required = false)
            String summary,
            @McpToolParam(description = "JSON array of PII entities found by the LLM that were NOT already detected by regex. " +
                    "Each entry: {\"type\":\"PERSON_NAME\",\"redactedText\":\"Joh***ith\",\"confidence\":0.9}. " +
                    "Include contextual PII like names, addresses, and role-specific identifiers that regex cannot catch.", required = false)
            String piiFindings) {
        toolLog.logToolCall(documentId, "save_classification_result", "Saving: " + categoryName + " / " + sensitivityLabel + " (confidence: " + confidence + ")");

        // Resolve category: if the LLM passed a name instead of a real ID, look it up
        String resolvedCategoryId = categoryId;
        String resolvedCategoryName = categoryName;

        Optional<ClassificationCategory> byId = categoryRepo.findById(categoryId);
        if (byId.isPresent()) {
            resolvedCategoryName = byId.get().getName();
        } else {
            // LLM likely passed the name as the ID — look up by name
            Optional<ClassificationCategory> byName = categoryRepo.findByNameIgnoreCase(categoryId);
            if (byName.isEmpty()) {
                byName = categoryRepo.findByNameIgnoreCase(categoryName);
            }
            if (byName.isPresent()) {
                resolvedCategoryId = byName.get().getId();
                resolvedCategoryName = byName.get().getName();
            }
        }

        DocumentClassificationResult result = new DocumentClassificationResult();
        result.setDocumentId(documentId);
        result.setCategoryId(resolvedCategoryId);
        result.setCategoryName(resolvedCategoryName);
        result.setSensitivityLabel(SensitivityLabel.valueOf(sensitivityLabel));
        result.setConfidence(confidence);
        result.setReasoning(reasoning);
        if (summary != null && !summary.isBlank()) {
            result.setSummary(summary);
        }

        if (tags != null && !tags.isBlank()) {
            result.setTags(List.of(tags.split("\\s*,\\s*")));
        }

        if (retentionScheduleId != null && !retentionScheduleId.isBlank()) {
            result.setRetentionScheduleId(retentionScheduleId);
        }

        if (extractedMetadata != null && !extractedMetadata.isBlank()) {
            try {
                // Parse JSON string into Map
                @SuppressWarnings("unchecked")
                Map<String, String> metadata = new java.util.HashMap<>();
                // Simple JSON parsing — handles {"key":"value","key2":"value2"} format
                String inner = extractedMetadata.trim();
                if (inner.startsWith("{")) inner = inner.substring(1);
                if (inner.endsWith("}")) inner = inner.substring(0, inner.length() - 1);
                for (String pair : inner.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)")) {
                    String[] kv = pair.split(":", 2);
                    if (kv.length == 2) {
                        String key = kv[0].trim().replaceAll("^\"|\"$", "");
                        String value = kv[1].trim().replaceAll("^\"|\"$", "");
                        if (!key.isEmpty()) metadata.put(key, value);
                    }
                }
                result.setExtractedMetadata(metadata);
            } catch (Exception e) {
                // If parsing fails, store the raw string
                result.setExtractedMetadata(Map.of("_raw", extractedMetadata));
            }
        }

        // Store traits in extractedMetadata under a special key so the pipeline can read them
        if (traits != null && !traits.isBlank()) {
            if (result.getExtractedMetadata() == null) {
                result.setExtractedMetadata(new java.util.HashMap<>());
            } else {
                result.setExtractedMetadata(new java.util.HashMap<>(result.getExtractedMetadata()));
            }
            result.getExtractedMetadata().put("_traits", traits);
        }

        DocumentClassificationResult saved = governanceService.saveClassificationResult(result);

        // Merge LLM-detected PII findings onto the document (Tier 2)
        int llmPiiCount = 0;
        if (piiFindings != null && !piiFindings.isBlank()) {
            llmPiiCount = mergeLlmPiiFindings(documentId, piiFindings);
        }

        return "Classification saved successfully. ID: " + saved.getId() +
                ", Category: " + resolvedCategoryName + " (" + resolvedCategoryId + ")" +
                ", Sensitivity: " + saved.getSensitivityLabel() +
                ", Confidence: " + saved.getConfidence() +
                (llmPiiCount > 0 ? ", LLM PII findings added: " + llmPiiCount : "");
    }

    /**
     * Parse LLM-reported PII findings and merge them onto the document.
     * Only adds findings that don't already exist (by type + redactedText).
     * Uses MongoTemplate directly since the MCP server doesn't have DocumentService.
     */
    private int mergeLlmPiiFindings(String documentId, String piiJson) {
        try {
            DocumentModel doc = mongoTemplate.findById(documentId, DocumentModel.class);
            if (doc == null) return 0;

            List<PiiEntity> existing = doc.getPiiFindings() != null
                    ? new ArrayList<>(doc.getPiiFindings()) : new ArrayList<>();

            // Build lookup of existing findings
            java.util.Set<String> existingKeys = new java.util.HashSet<>();
            for (PiiEntity e : existing) {
                existingKeys.add(e.getType() + "::" + (e.getRedactedText() != null ? e.getRedactedText() : ""));
            }

            // Parse the JSON array — simple parsing for [{"type":"X","redactedText":"Y","confidence":0.9}, ...]
            String inner = piiJson.trim();
            if (inner.startsWith("[")) inner = inner.substring(1);
            if (inner.endsWith("]")) inner = inner.substring(0, inner.length() - 1);

            int added = 0;
            // Split on },{
            for (String item : inner.split("\\}\\s*,\\s*\\{")) {
                String clean = item.replace("{", "").replace("}", "").trim();
                if (clean.isEmpty()) continue;

                String type = extractJsonValue(clean, "type");
                String redacted = extractJsonValue(clean, "redactedText");
                double conf = 0.8;
                try {
                    String confStr = extractJsonValue(clean, "confidence");
                    if (confStr != null) conf = Double.parseDouble(confStr);
                } catch (Exception ignored) {}

                if (type == null || type.isBlank()) continue;

                String key = type + "::" + (redacted != null ? redacted : "");
                if (existingKeys.contains(key)) continue;

                PiiEntity entity = new PiiEntity(type, redacted, redacted, 0, conf, PiiEntity.DetectionMethod.LLM);
                existing.add(entity);
                existingKeys.add(key);
                added++;
            }

            if (added > 0) {
                doc.setPiiFindings(existing);
                doc.setPiiScannedAt(Instant.now());
                long activeCount = existing.stream().filter(p -> !p.isDismissed()).count();
                doc.setPiiStatus(activeCount > 0 ? "DETECTED" : doc.getPiiStatus());
                doc.setUpdatedAt(Instant.now());
                mongoTemplate.save(doc);
            }

            return added;
        } catch (Exception e) {
            toolLog.logToolCall(documentId, "save_classification_result", "Failed to parse LLM PII findings: " + e.getMessage());
            return 0;
        }
    }

    private static String extractJsonValue(String json, String key) {
        int idx = json.indexOf("\"" + key + "\"");
        if (idx < 0) return null;
        String after = json.substring(idx + key.length() + 3).trim();
        if (after.startsWith(":")) after = after.substring(1).trim();
        if (after.startsWith("\"")) {
            int end = after.indexOf("\"", 1);
            return end > 0 ? after.substring(1, end) : null;
        }
        // Numeric value
        StringBuilder sb = new StringBuilder();
        for (char c : after.toCharArray()) {
            if (Character.isDigit(c) || c == '.' || c == '-') sb.append(c);
            else break;
        }
        return sb.length() > 0 ? sb.toString() : null;
    }
}
