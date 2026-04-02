package co.uk.wolfnotsheep.mcp.tools;

import co.uk.wolfnotsheep.governance.models.SensitivityLabel;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.stereotype.Component;

@Component
public class SensitivityDefinitionsTool {

    @McpTool(name = "get_sensitivity_definitions",
            description = "Retrieve the definitions of all sensitivity labels (PUBLIC, INTERNAL, CONFIDENTIAL, RESTRICTED). " +
                    "Use this to determine the correct sensitivity level for a document based on its content.")
    public String getDefinitions() {
        StringBuilder sb = new StringBuilder("Sensitivity Labels:\n\n");

        for (SensitivityLabel label : SensitivityLabel.values()) {
            sb.append("## ").append(label.getDisplayName())
                    .append(" (level ").append(label.getLevel()).append(")\n")
                    .append(label.getDescription()).append("\n\n");
        }

        sb.append("""
                Guidelines for assigning sensitivity:
                - PUBLIC: Marketing materials, published reports, public-facing documents
                - INTERNAL: Internal memos, meeting notes, non-sensitive operational docs
                - CONFIDENTIAL: Financial data, employee records, client contracts, strategy docs
                - RESTRICTED: PII (personal identifiable information), health records, legal privilege, \
                trade secrets, regulatory filings

                When in doubt, assign the HIGHER sensitivity level. It is safer to over-classify \
                than to under-classify. Flag for human review if uncertain.
                """);

        return sb.toString();
    }
}
