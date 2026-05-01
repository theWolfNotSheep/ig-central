package co.uk.wolfnotsheep.mcp.tools;

import co.uk.wolfnotsheep.governance.models.GovernancePolicy;
import co.uk.wolfnotsheep.governance.models.SensitivityLabel;
import co.uk.wolfnotsheep.governance.services.GovernanceService;
import co.uk.wolfnotsheep.mcp.ToolCallLogger;
import co.uk.wolfnotsheep.mcp.config.CacheConfig;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

@Service
public class GovernancePolicyTool {

    private final GovernanceService governanceService;
    private final ObjectMapper objectMapper;
    private final ToolCallLogger toolLog;

    public GovernancePolicyTool(GovernanceService governanceService, ObjectMapper objectMapper, ToolCallLogger toolLog) {
        this.governanceService = governanceService;
        this.objectMapper = objectMapper;
        this.toolLog = toolLog;
    }

    @Cacheable(value = CacheConfig.CACHE_POLICIES, key = "(#p0 != null ? #p0 : 'null') + ':' + (#p1 != null ? #p1 : 'null')")
    @McpTool(name = "get_governance_policies",
            description = "Retrieve active governance policies. Optionally filter by category ID or sensitivity label. " +
                    "Use this to understand what rules apply to a document based on its classification.")
    public String getPolicies(
            @McpToolParam(description = "Optional: filter policies by classification category ID", required = false)
            String categoryId,
            @McpToolParam(description = "Optional: filter policies by sensitivity label (PUBLIC, INTERNAL, CONFIDENTIAL, RESTRICTED)", required = false)
            String sensitivityLabel) throws JacksonException {
        toolLog.logToolCall("", "get_governance_policies", "Loading policies");

        List<GovernancePolicy> policies;

        if (categoryId != null && !categoryId.isBlank()) {
            policies = governanceService.getPoliciesForCategory(categoryId);
        } else if (sensitivityLabel != null && !sensitivityLabel.isBlank()) {
            policies = governanceService.getPoliciesForSensitivity(
                    SensitivityLabel.valueOf(sensitivityLabel));
        } else {
            policies = governanceService.getActivePolicies();
        }

        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(policies);
    }
}
