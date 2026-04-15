package co.uk.wolfnotsheep.mcp.tools;

import co.uk.wolfnotsheep.governance.models.SensitivityDefinition;
import co.uk.wolfnotsheep.governance.models.SensitivityLabel;
import co.uk.wolfnotsheep.governance.repositories.SensitivityDefinitionRepository;
import co.uk.wolfnotsheep.mcp.ToolCallLogger;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SensitivityDefinitionsTool {

    private final SensitivityDefinitionRepository sensitivityRepo;
    private final ToolCallLogger toolLog;

    public SensitivityDefinitionsTool(SensitivityDefinitionRepository sensitivityRepo, ToolCallLogger toolLog) {
        this.sensitivityRepo = sensitivityRepo;
        this.toolLog = toolLog;
    }

    @McpTool(name = "get_sensitivity_definitions",
            description = "Retrieve the definitions of all sensitivity labels with their guidelines and examples. " +
                    "Use this to determine the correct sensitivity level for a document based on its content.")
    public String getDefinitions() {
        toolLog.logToolCall("", "get_sensitivity_definitions", "Loading sensitivity levels");
        List<SensitivityDefinition> defs = sensitivityRepo.findByActiveTrueOrderByLevelAsc();

        if (defs.isEmpty()) {
            // Fallback to enum if no definitions in DB
            return getFallbackDefinitions();
        }

        StringBuilder sb = new StringBuilder("Sensitivity Labels:\n\n");
        for (SensitivityDefinition def : defs) {
            sb.append("## ").append(def.getDisplayName())
                    .append(" (").append(def.getKey()).append(", level ").append(def.getLevel()).append(")\n")
                    .append(def.getDescription()).append("\n\n");

            if (def.getGuidelines() != null && !def.getGuidelines().isEmpty()) {
                sb.append("Guidelines:\n");
                for (String g : def.getGuidelines()) {
                    sb.append("- ").append(g).append("\n");
                }
                sb.append("\n");
            }

            if (def.getExamples() != null && !def.getExamples().isEmpty()) {
                sb.append("Examples:\n");
                for (String e : def.getExamples()) {
                    sb.append("- ").append(e).append("\n");
                }
                sb.append("\n");
            }
        }

        sb.append("IMPORTANT: When in doubt, assign the HIGHER sensitivity level. ")
                .append("It is safer to over-classify than to under-classify. ")
                .append("Flag for human review if uncertain.\n");

        return sb.toString();
    }

    private String getFallbackDefinitions() {
        StringBuilder sb = new StringBuilder("Sensitivity Labels:\n\n");
        for (SensitivityLabel label : SensitivityLabel.values()) {
            sb.append("## ").append(label.getDisplayName())
                    .append(" (level ").append(label.getLevel()).append(")\n")
                    .append(label.getDescription()).append("\n\n");
        }
        return sb.toString();
    }
}
