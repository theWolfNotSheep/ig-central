package co.uk.wolfnotsheep.mcp.tools;

import co.uk.wolfnotsheep.governance.services.GovernanceService;
import co.uk.wolfnotsheep.mcp.ToolCallLogger;
import co.uk.wolfnotsheep.mcp.config.CacheConfig;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class ClassificationTaxonomyTool {

    private final GovernanceService governanceService;
    private final ToolCallLogger toolLog;

    public ClassificationTaxonomyTool(GovernanceService governanceService, ToolCallLogger toolLog) {
        this.governanceService = governanceService;
        this.toolLog = toolLog;
    }

    @Cacheable(CacheConfig.CACHE_TAXONOMY)
    @McpTool(name = "get_classification_taxonomy",
            description = "Retrieve the full document classification taxonomy as a hierarchical tree. " +
                    "Each category includes its name, description, default sensitivity label, and keywords. " +
                    "Use this to determine which category a document belongs to.")
    public String getTaxonomy() {
        toolLog.logToolCall("", "get_classification_taxonomy", "Loading taxonomy categories");
        String taxonomy = governanceService.getTaxonomyAsText();

        if (taxonomy.isBlank()) {
            return "No classification categories have been defined yet. " +
                    "The document should be flagged for manual classification.";
        }

        return taxonomy;
    }
}
