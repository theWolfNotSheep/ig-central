package co.uk.wolfnotsheep.mcp.tools;

import co.uk.wolfnotsheep.governance.models.DocumentClassificationResult;
import co.uk.wolfnotsheep.governance.models.SensitivityLabel;
import co.uk.wolfnotsheep.governance.services.GovernanceService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class SaveClassificationTool {

    private final GovernanceService governanceService;

    public SaveClassificationTool(GovernanceService governanceService) {
        this.governanceService = governanceService;
    }

    @Tool(name = "save_classification_result",
            description = "Persist the classification result for a document. Call this after you have determined the " +
                    "category, sensitivity, tags, and applicable policies. This creates an immutable audit record.")
    public String saveClassification(
            @ToolParam(description = "The ID of the document being classified", required = true)
            String documentId,
            @ToolParam(description = "The classification category ID from the taxonomy", required = true)
            String categoryId,
            @ToolParam(description = "The human-readable category name", required = true)
            String categoryName,
            @ToolParam(description = "The sensitivity label: PUBLIC, INTERNAL, CONFIDENTIAL, or RESTRICTED", required = true)
            String sensitivityLabel,
            @ToolParam(description = "Confidence score between 0.0 and 1.0", required = true)
            double confidence,
            @ToolParam(description = "Explanation of why this classification was chosen", required = true)
            String reasoning,
            @ToolParam(description = "Comma-separated tags extracted from the document", required = false)
            String tags,
            @ToolParam(description = "The retention schedule ID to apply", required = false)
            String retentionScheduleId) {

        DocumentClassificationResult result = new DocumentClassificationResult();
        result.setDocumentId(documentId);
        result.setCategoryId(categoryId);
        result.setCategoryName(categoryName);
        result.setSensitivityLabel(SensitivityLabel.valueOf(sensitivityLabel));
        result.setConfidence(confidence);
        result.setReasoning(reasoning);

        if (tags != null && !tags.isBlank()) {
            result.setTags(List.of(tags.split("\\s*,\\s*")));
        }

        if (retentionScheduleId != null && !retentionScheduleId.isBlank()) {
            result.setRetentionScheduleId(retentionScheduleId);
        }

        DocumentClassificationResult saved = governanceService.saveClassificationResult(result);

        return "Classification saved successfully. ID: " + saved.getId() +
                ", Sensitivity: " + saved.getSensitivityLabel() +
                ", Confidence: " + saved.getConfidence();
    }
}
