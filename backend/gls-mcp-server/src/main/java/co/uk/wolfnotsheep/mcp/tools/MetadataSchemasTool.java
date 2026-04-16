package co.uk.wolfnotsheep.mcp.tools;

import co.uk.wolfnotsheep.governance.repositories.ClassificationCategoryRepository;
import co.uk.wolfnotsheep.governance.services.GovernanceService;
import co.uk.wolfnotsheep.mcp.ToolCallLogger;
import co.uk.wolfnotsheep.mcp.config.CacheConfig;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * MCP tool that provides the LLM with metadata extraction schemas.
 * These define the structured fields to extract from documents
 * based on their classification category.
 */
@Service
public class MetadataSchemasTool {

    private final GovernanceService governanceService;
    private final ClassificationCategoryRepository categoryRepo;
    private final ToolCallLogger toolLog;

    public MetadataSchemasTool(GovernanceService governanceService,
                               ClassificationCategoryRepository categoryRepo,
                               ToolCallLogger toolLog) {
        this.governanceService = governanceService;
        this.categoryRepo = categoryRepo;
        this.toolLog = toolLog;
    }

    @Cacheable(value = CacheConfig.CACHE_SCHEMAS, key = "#categoryId")
    @McpTool(name = "get_metadata_schemas",
            description = "Retrieve the metadata extraction schema for a document's category. " +
                    "The schema defines EXACTLY which structured fields to extract — extract ONLY those fields, nothing else. " +
                    "Call this AFTER determining the document category. Pass the category ID or category name. " +
                    "If no schema exists for the category, do NOT extract metadata.")
    public String getMetadataSchemas(
            @McpToolParam(description = "The category ID or category name to get the schema for")
            String categoryId) {
        toolLog.logToolCall("", "get_metadata_schemas", "Loading schema for category=" + categoryId);

        if (categoryId == null || categoryId.isBlank()) {
            return "No category provided. Cannot look up metadata schema.";
        }

        // Try by ID first
        String resolvedCategoryId = categoryId;
        String schema = governanceService.getMetadataSchemaForCategory(categoryId);

        // If not found, try resolving by name
        if (schema == null) {
            var byName = categoryRepo.findByNameIgnoreCase(categoryId);
            if (byName.isPresent()) {
                resolvedCategoryId = byName.get().getId();
                schema = governanceService.getMetadataSchemaForCategory(resolvedCategoryId);
                toolLog.logToolCall("", "get_metadata_schemas", "Resolved name '" + categoryId + "' to ID " + resolvedCategoryId);
            }
        }

        // If still not found, try the parent category (schemas may be linked at parent level)
        if (schema == null) {
            var category = categoryRepo.findById(resolvedCategoryId);
            if (category.isPresent() && category.get().getParentId() != null) {
                String parentId = category.get().getParentId();
                schema = governanceService.getMetadataSchemaForCategory(parentId);
                if (schema != null) {
                    toolLog.logToolCall("", "get_metadata_schemas", "Found schema via parent category ID " + parentId);
                }
            }
        }

        if (schema != null) {
            return "## Metadata Extraction Instructions\n\n" +
                    "Extract ONLY the fields listed below into the `extractedMetadata` JSON parameter " +
                    "when calling save_classification_result. Do not add extra fields. " +
                    "For required fields you cannot find, set the value to \"NOT_FOUND\".\n\n" +
                    schema;
        }

        return "No metadata extraction schema configured for this category. " +
                "Do NOT include extractedMetadata when calling save_classification_result.";
    }
}
