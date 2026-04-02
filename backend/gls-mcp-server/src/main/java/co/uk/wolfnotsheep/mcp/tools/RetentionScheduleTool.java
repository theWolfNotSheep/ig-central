package co.uk.wolfnotsheep.mcp.tools;

import co.uk.wolfnotsheep.governance.models.RetentionSchedule;
import co.uk.wolfnotsheep.governance.services.GovernanceService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RetentionScheduleTool {

    private final GovernanceService governanceService;
    private final ObjectMapper objectMapper;

    public RetentionScheduleTool(GovernanceService governanceService, ObjectMapper objectMapper) {
        this.governanceService = governanceService;
        this.objectMapper = objectMapper;
    }

    @Tool(name = "get_retention_schedules",
            description = "Retrieve retention schedules that define how long documents must be kept and what happens at expiry. " +
                    "Use this after classification to determine the correct retention period.")
    public String getSchedules(
            @ToolParam(description = "Optional: retrieve a specific retention schedule by ID", required = false)
            String scheduleId) throws JsonProcessingException {

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
