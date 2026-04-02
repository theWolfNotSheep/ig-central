package co.uk.wolfnotsheep.mcp.tools;

import co.uk.wolfnotsheep.governance.models.RetentionSchedule;
import co.uk.wolfnotsheep.governance.services.GovernanceService;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

@Service
public class RetentionScheduleTool {

    private final GovernanceService governanceService;
    private final ObjectMapper objectMapper;

    public RetentionScheduleTool(GovernanceService governanceService, ObjectMapper objectMapper) {
        this.governanceService = governanceService;
        this.objectMapper = objectMapper;
    }

    @McpTool(name = "get_retention_schedules",
            description = "Retrieve retention schedules that define how long documents must be kept and what happens at expiry. " +
                    "Use this after classification to determine the correct retention period.")
    public String getSchedules(
            @McpToolParam(description = "Optional: retrieve a specific retention schedule by ID", required = false)
            String scheduleId) throws JacksonException {

        if (scheduleId != null && !scheduleId.isBlank()) {
            RetentionSchedule schedule = governanceService.getRetentionSchedule(scheduleId);
            if (schedule == null) {
                return "No retention schedule found with ID: " + scheduleId;
            }
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(schedule);
        }

        List<RetentionSchedule> schedules = governanceService.getAllRetentionSchedules();
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(schedules);
    }
}
