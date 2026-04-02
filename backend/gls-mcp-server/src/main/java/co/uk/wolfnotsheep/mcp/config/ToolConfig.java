package co.uk.wolfnotsheep.mcp.config;

import co.uk.wolfnotsheep.mcp.tools.*;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers all @Tool-annotated beans as ToolCallbackProviders.
 * The MCP server auto-configuration picks these up and exposes
 * them as MCP tools over SSE.
 */
@Configuration
public class ToolConfig {

    @Bean
    public ToolCallbackProvider governanceToolCallbacks(
            GovernancePolicyTool policyTool,
            ClassificationTaxonomyTool taxonomyTool,
            SensitivityDefinitionsTool sensitivityTool,
            RetentionScheduleTool retentionTool,
            StorageCapabilitiesTool storageTool,
            SaveClassificationTool classificationTool) {

        return MethodToolCallbackProvider.builder()
                .toolObjects(
                        policyTool,
                        taxonomyTool,
                        sensitivityTool,
                        retentionTool,
                        storageTool,
                        classificationTool
                )
                .build();
    }
}
