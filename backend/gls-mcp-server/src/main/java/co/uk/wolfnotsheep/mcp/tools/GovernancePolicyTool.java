package co.uk.wolfnotsheep.mcp.tools;

import co.uk.wolfnotsheep.governance.models.GovernancePolicy;
import co.uk.wolfnotsheep.governance.models.SensitivityLabel;
import co.uk.wolfnotsheep.governance.services.GovernanceService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class GovernancePolicyTool {

    private final GovernanceService governanceService;
    private final ObjectMapper objectMapper;

    public GovernancePolicyTool(GovernanceService governanceService, ObjectMapper objectMapper) {
        this.governanceService = governanceService;
        this.objectMapper = objectMapper;
    }

    @McpTool(name = "get_governance_policies",
            description = "Retrieve active governance policies. Optionally filter by category ID or sensitivity label. " +
                    "Use this to understand what rules apply to a document based on its classification.")
    public String getPolicies(
            @McpToolParam(description = "Optional: filter policies by classification category ID", required = false)
            String categoryId,
            @McpToolParam(description = "Optional: filter policies by sensitivity label (PUBLIC, INTERNAL, CONFIDENTIAL, RESTRICTED)", required = false)
            String sensitivityLabel) throws JsonProcessingException {

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
