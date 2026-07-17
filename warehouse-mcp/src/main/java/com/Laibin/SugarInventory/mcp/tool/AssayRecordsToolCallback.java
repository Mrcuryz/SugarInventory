package com.Laibin.SugarInventory.mcp.tool;

import com.Laibin.SugarInventory.mcp.model.ToolModels.AssayRecordsRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;

public final class AssayRecordsToolCallback implements ToolCallback {
    private final WarehouseTools warehouseTools;
    private final ObjectMapper objectMapper;
    private final ToolDefinition toolDefinition;

    public AssayRecordsToolCallback(WarehouseTools warehouseTools, ObjectMapper objectMapper, String inputSchema) {
        this.warehouseTools = warehouseTools;
        this.objectMapper = objectMapper;
        this.toolDefinition = DefaultToolDefinition.builder()
                .name("query_assay_records")
                .description("Read paged assay records and summary for a controlled product scope and sample-date range without modifying assay data.")
                .inputSchema(inputSchema)
                .build();
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return toolDefinition;
    }

    @Override
    public String call(String toolInput) {
        AssayRecordsRequest request;
        try {
            request = objectMapper.readValue(toolInput, AssayRecordsRequest.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid query_assay_records arguments.");
        }
        try {
            return objectMapper.writeValueAsString(warehouseTools.queryAssayRecords(request));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to serialize query_assay_records result.");
        }
    }
}
