package co.uk.wolfnotsheep.mcp.tools;

import co.uk.wolfnotsheep.governance.services.GovernanceService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
public class ClassificationTaxonomyTool {

    private final GovernanceService governanceService;

    public ClassificationTaxonomyTool(GovernanceService governanceService) {
        this.governanceService = governanceService;
    }

    @Tool(name = "get_classification_taxonomy",
            description = "Retrieve the full document classification taxonomy as a hierarchical tree. " +
                    "Each category includes its name, description, default sensitivity label, and keywords. " +
                    "Use this to determine which category a document belongs to.")
    public String getTaxonomy() {
        String taxonomy = governanceService.getTaxonomyAsText();

        if (taxonomy.isBlank()) {
            return "No classification categories have been defined yet. " +
                    "The document should be flagged for manual classification.";
        }

        return taxonomy;
    }
}
