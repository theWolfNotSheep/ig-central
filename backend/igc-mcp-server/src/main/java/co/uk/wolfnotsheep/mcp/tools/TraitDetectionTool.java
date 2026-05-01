package co.uk.wolfnotsheep.mcp.tools;

import co.uk.wolfnotsheep.governance.models.TraitDefinition;
import co.uk.wolfnotsheep.governance.repositories.TraitDefinitionRepository;
import co.uk.wolfnotsheep.mcp.ToolCallLogger;
import co.uk.wolfnotsheep.mcp.config.CacheConfig;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * MCP tool that provides trait definitions to the LLM.
 * The LLM uses these to detect document characteristics:
 * template vs real, draft vs final, inbound vs outbound, etc.
 */
@Service
public class TraitDetectionTool {

    private final TraitDefinitionRepository traitRepo;
    private final ToolCallLogger toolLog;

    public TraitDetectionTool(TraitDefinitionRepository traitRepo, ToolCallLogger toolLog) {
        this.traitRepo = traitRepo;
        this.toolLog = toolLog;
    }

    @Cacheable(CacheConfig.CACHE_TRAITS)
    @McpTool(name = "get_document_traits",
            description = "Get the list of document traits to detect. Traits describe document characteristics " +
                    "like whether it's a template or real document, draft or final, inbound or outbound. " +
                    "For each trait, check if the document matches based on the detection hints and indicators. " +
                    "Include matching trait keys as a comma-separated list in the 'traits' field when calling " +
                    "save_classification_result. IMPORTANT: If the document is a template (contains placeholders " +
                    "like {Name} or [Insert Date]), mark it as TEMPLATE — this suppresses PII detection.")
    public String getTraits() {
        toolLog.logToolCall("", "get_document_traits", "Loading trait definitions");
        List<TraitDefinition> traits = traitRepo.findByActiveTrueOrderByDimensionAscDisplayNameAsc();

        if (traits.isEmpty()) {
            return "No trait definitions configured.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("## Document Traits\n\n");
        sb.append("Analyse the document and determine which traits apply. ");
        sb.append("Only include traits where you have evidence from the document text.\n\n");

        String currentDimension = "";
        for (TraitDefinition trait : traits) {
            if (!trait.getDimension().equals(currentDimension)) {
                currentDimension = trait.getDimension();
                sb.append("### ").append(currentDimension).append("\n");
                sb.append("Pick ONE from this group (or none if unclear):\n\n");
            }
            sb.append("- **").append(trait.getKey()).append("** — ").append(trait.getDescription());
            if (trait.getDetectionHint() != null) {
                sb.append("\n  Detection: ").append(trait.getDetectionHint());
            }
            if (trait.getIndicators() != null && !trait.getIndicators().isEmpty()) {
                sb.append("\n  Look for: ").append(String.join(", ", trait.getIndicators()));
            }
            if (trait.isSuppressPii()) {
                sb.append("\n  ⚠ If this trait applies, PII findings should be treated as informational only");
            }
            sb.append("\n\n");
        }

        return sb.toString();
    }
}
