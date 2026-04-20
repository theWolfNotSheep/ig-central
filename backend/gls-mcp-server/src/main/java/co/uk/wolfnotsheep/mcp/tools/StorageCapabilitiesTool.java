package co.uk.wolfnotsheep.mcp.tools;

import co.uk.wolfnotsheep.governance.models.SensitivityLabel;
import co.uk.wolfnotsheep.governance.models.StorageTier;
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
public class StorageCapabilitiesTool {

    private final GovernanceService governanceService;
    private final ObjectMapper objectMapper;
    private final ToolCallLogger toolLog;

    public StorageCapabilitiesTool(GovernanceService governanceService, ObjectMapper objectMapper, ToolCallLogger toolLog) {
        this.governanceService = governanceService;
        this.objectMapper = objectMapper;
        this.toolLog = toolLog;
    }

    @Cacheable(value = CacheConfig.CACHE_STORAGE, key = "#p0 != null ? #p0 : 'all'")
    @McpTool(name = "get_storage_capabilities",
            description = "Retrieve available storage tiers with their encryption, immutability, and geographic constraints. " +
                    "Use this to recommend the appropriate storage tier for a classified document.")
    public String getCapabilities(
            @McpToolParam(description = "Optional: filter storage tiers by what sensitivity they support (PUBLIC, INTERNAL, CONFIDENTIAL, RESTRICTED)", required = false)
            String sensitivityLabel) throws JacksonException {
        toolLog.logToolCall("", "get_storage_capabilities", "Loading storage tiers");

        List<StorageTier> tiers;

        if (sensitivityLabel != null && !sensitivityLabel.isBlank()) {
            tiers = governanceService.getStorageTiersForSensitivity(
                    SensitivityLabel.valueOf(sensitivityLabel));
        } else {
            tiers = governanceService.getAllStorageTiers();
        }

        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(tiers);
    }
}
