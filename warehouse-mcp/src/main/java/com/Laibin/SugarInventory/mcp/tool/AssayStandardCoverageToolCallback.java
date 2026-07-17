package com.Laibin.SugarInventory.mcp.tool;

import com.Laibin.SugarInventory.mcp.model.ToolModels.AssayStandardCoverageRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;

public final class AssayStandardCoverageToolCallback implements ToolCallback {
    private final WarehouseTools warehouseTools;
    private final ObjectMapper objectMapper;
    private final ToolDefinition toolDefinition;

    public AssayStandardCoverageToolCallback(WarehouseTools warehouseTools, ObjectMapper objectMapper, String inputSchema) {
        this.warehouseTools = warehouseTools;
        this.objectMapper = objectMapper;
        this.toolDefinition = DefaultToolDefinition.builder()
                .name("query_assay_standard_coverage")
                .description("Read current-inventory product groups that lack an effective quality standard without modifying warehouse data.")
                .inputSchema(inputSchema)
                .build();
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return toolDefinition;
    }

    @Override
    public String call(String toolInput) {
        AssayStandardCoverageRequest request;
        try {
            request = objectMapper.readValue(toolInput, AssayStandardCoverageRequest.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid query_assay_standard_coverage arguments.");
        }
        try {
            return objectMapper.writeValueAsString(warehouseTools.queryAssayStandardCoverage(request));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to serialize query_assay_standard_coverage result.");
        }
    }
}
