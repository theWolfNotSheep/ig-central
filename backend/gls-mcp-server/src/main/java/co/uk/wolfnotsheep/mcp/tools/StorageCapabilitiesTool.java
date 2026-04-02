package co.uk.wolfnotsheep.mcp.tools;

import co.uk.wolfnotsheep.governance.models.SensitivityLabel;
import co.uk.wolfnotsheep.governance.models.StorageTier;
import co.uk.wolfnotsheep.governance.services.GovernanceService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class StorageCapabilitiesTool {

    private final GovernanceService governanceService;
    private final ObjectMapper objectMapper;

    public StorageCapabilitiesTool(GovernanceService governanceService, ObjectMapper objectMapper) {
        this.governanceService = governanceService;
        this.objectMapper = objectMapper;
    }

    @Tool(name = "get_storage_capabilities",
            description = "Retrieve available storage tiers with their encryption, immutability, and geographic constraints. " +
                    "Use this to recommend the appropriate storage tier for a classified document.")
    public String getCapabilities(
            @ToolParam(description = "Optional: filter storage tiers by what sensitivity they support (PUBLIC, INTERNAL, CONFIDENTIAL, RESTRICTED)", required = false)
            String sensitivityLabel) throws JsonProcessingException {

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
