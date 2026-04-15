package co.uk.wolfnotsheep.mcp.tools;

import co.uk.wolfnotsheep.governance.services.GovernanceService;
import co.uk.wolfnotsheep.mcp.ToolCallLogger;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;

/**
 * MCP tool that provides the LLM with past human corrections for similar documents.
 * This is the core feedback mechanism — the LLM learns from records manager decisions
 * without any fine-tuning, by reading correction history at inference time.
 */
@Service
public class CorrectionHistoryTool {

    private final GovernanceService governanceService;
    private final ToolCallLogger toolLog;

    public CorrectionHistoryTool(GovernanceService governanceService, ToolCallLogger toolLog) {
        this.governanceService = governanceService;
        this.toolLog = toolLog;
    }

    @McpTool(name = "get_correction_history",
            description = "Retrieve past human corrections to LLM classifications. " +
                    "ALWAYS call this BEFORE making a classification decision. " +
                    "Returns corrections relevant to the document's category and type, " +
                    "including PII types that the system previously missed. " +
                    "Use these corrections to avoid repeating the same mistakes.")
    public String getCorrectionHistory(
            @McpToolParam(description = "The category ID you are considering for this document", required = false)
            String categoryId,
            @McpToolParam(description = "The MIME type of the document being classified", required = false)
            String mimeType) {
        toolLog.logToolCall("", "get_correction_history", "Loading corrections for category=" + categoryId + " mimeType=" + mimeType);

        String summary = governanceService.getCorrectionsSummaryForLlm(categoryId, mimeType);

        if (summary.startsWith("No prior")) {
            return summary;
        }

        return "## Human Correction History\n\n" +
                "The following corrections were made by records managers to previous LLM classifications. " +
                "Use these to improve your classification accuracy. Pay special attention to:\n" +
                "- Categories that were frequently mis-classified\n" +
                "- Sensitivity levels that were set too low\n" +
                "- PII types that the system missed\n\n" +
                summary;
    }
}
