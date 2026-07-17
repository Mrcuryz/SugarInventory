package com.Laibin.SugarInventory.mcp.tool;

import com.Laibin.SugarInventory.mcp.model.ToolModels.PalletFlowRecordsRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;

public final class PalletFlowRecordsToolCallback implements ToolCallback {
    private final WarehouseTools warehouseTools;
    private final ObjectMapper objectMapper;
    private final ToolDefinition toolDefinition;

    public PalletFlowRecordsToolCallback(WarehouseTools warehouseTools, ObjectMapper objectMapper, String inputSchema) {
        this.warehouseTools = warehouseTools;
        this.objectMapper = objectMapper;
        this.toolDefinition = DefaultToolDefinition.builder().name("query_pallet_flow_records")
                .description("Read paged pallet flow records by code, product, warehouse, time, and event type.")
                .inputSchema(inputSchema).build();
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return toolDefinition;
    }

    @Override
    public String call(String toolInput) {
        try {
            PalletFlowRecordsRequest request = objectMapper.readValue(toolInput, PalletFlowRecordsRequest.class);
            return objectMapper.writeValueAsString(warehouseTools.queryPalletFlowRecords(request));
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid query_pallet_flow_records arguments or result.");
        }
    }
}
