package com.Laibin.SugarInventory.mcp.tool;

import com.Laibin.SugarInventory.mcp.model.ToolModels.AssayAbnormalitiesRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;

public final class AssayAbnormalitiesToolCallback implements ToolCallback {
    private final WarehouseTools warehouseTools;
    private final ObjectMapper objectMapper;
    private final ToolDefinition toolDefinition;

    public AssayAbnormalitiesToolCallback(WarehouseTools warehouseTools, ObjectMapper objectMapper, String inputSchema) {
        this.warehouseTools = warehouseTools;
        this.objectMapper = objectMapper;
        this.toolDefinition = DefaultToolDefinition.builder()
                .name("query_assay_abnormalities")
                .description("Read grouped assay quality abnormalities for controlled product and date scopes without modifying warehouse data.")
                .inputSchema(inputSchema)
                .build();
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return toolDefinition;
    }

    @Override
    public String call(String toolInput) {
        AssayAbnormalitiesRequest request;
        try {
            request = objectMapper.readValue(toolInput, AssayAbnormalitiesRequest.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid query_assay_abnormalities arguments.");
        }
        try {
            return objectMapper.writeValueAsString(warehouseTools.queryAssayAbnormalities(request));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to serialize query_assay_abnormalities result.");
        }
    }
}
