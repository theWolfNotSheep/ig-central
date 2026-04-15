package co.uk.wolfnotsheep.mcp.tools;

import co.uk.wolfnotsheep.governance.models.ClassificationCorrection;
import co.uk.wolfnotsheep.governance.services.GovernanceService;
import co.uk.wolfnotsheep.mcp.ToolCallLogger;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * MCP tool that provides organisation-specific PII patterns derived
 * from human corrections. These are PII types that the regex scanner
 * and generic LLM knowledge missed, but records managers flagged.
 */
@Service
public class OrgPiiPatternsTool {

    private final GovernanceService governanceService;
    private final ToolCallLogger toolLog;

    public OrgPiiPatternsTool(GovernanceService governanceService, ToolCallLogger toolLog) {
        this.governanceService = governanceService;
        this.toolLog = toolLog;
    }

    @McpTool(name = "get_org_pii_patterns",
            description = "Retrieve organisation-specific PII patterns AND known false positives. " +
                    "Returns: (1) PII types that records managers flagged as missed by the system, " +
                    "and (2) patterns that were incorrectly identified as PII (false positives). " +
                    "Use the false positives to AVOID flagging similar patterns. " +
                    "For example, a postcode belonging to a registered business address is NOT personal data.")
    public String getOrgPiiPatterns() {
        toolLog.logToolCall("", "get_org_pii_patterns", "Loading PII patterns");
        List<ClassificationCorrection> piiCorrections = governanceService.getPiiCorrections();
        List<ClassificationCorrection> piiDismissals = governanceService.getPiiDismissals();

        if (piiCorrections.isEmpty() && piiDismissals.isEmpty()) {
            return "No organisation-specific PII patterns or false positives recorded yet.";
        }

        StringBuilder sb = new StringBuilder();

        // False positives first — these are higher priority for accuracy
        if (!piiDismissals.isEmpty()) {
            sb.append("## Known False Positives — DO NOT flag these as PII\n\n");
            sb.append("Records managers have dismissed the following as false positives. ");
            sb.append("When you encounter similar patterns, consider the context before flagging as PII:\n\n");

            for (ClassificationCorrection c : piiDismissals) {
                if (c.getPiiCorrections() == null) continue;
                for (var p : c.getPiiCorrections()) {
                    sb.append("- **").append(p.type()).append("**: ").append(p.description());
                    if (p.context() != null && !p.context().isBlank()) {
                        sb.append(" — Context: ").append(p.context());
                    }
                    if (c.getReason() != null && !c.getReason().isBlank()) {
                        sb.append(" — Reason: ").append(c.getReason());
                    }
                    sb.append("\n");
                }
            }
            sb.append("\n");
        }

        // Additional PII patterns to watch for
        if (!piiCorrections.isEmpty()) {
            sb.append("## Additional PII Patterns — flag these\n\n");
            sb.append("Records managers have identified the following PII types in past documents:\n\n");

            for (ClassificationCorrection c : piiCorrections) {
                if (c.getPiiCorrections() == null) continue;
                for (var p : c.getPiiCorrections()) {
                    sb.append("- **").append(p.type()).append("**: ").append(p.description());
                    if (p.context() != null && !p.context().isBlank()) {
                        sb.append(" (found in context: ").append(p.context()).append(")");
                    }
                    sb.append("\n");
                }
            }
        }

        return sb.toString();
    }
}
