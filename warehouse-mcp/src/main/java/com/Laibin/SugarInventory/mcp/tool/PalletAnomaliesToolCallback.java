package com.Laibin.SugarInventory.mcp.tool;

import com.Laibin.SugarInventory.mcp.model.ToolModels.PalletAnomaliesRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;

public final class PalletAnomaliesToolCallback implements ToolCallback {
    private final WarehouseTools warehouseTools;
    private final ObjectMapper objectMapper;
    private final ToolDefinition toolDefinition;

    public PalletAnomaliesToolCallback(WarehouseTools warehouseTools, ObjectMapper objectMapper, String inputSchema) {
        this.warehouseTools = warehouseTools;
        this.objectMapper = objectMapper;
        this.toolDefinition = DefaultToolDefinition.builder().name("query_pallet_anomalies")
                .description("Read controlled pallet lifecycle anomalies without modifying warehouse data.")
                .inputSchema(inputSchema).build();
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return toolDefinition;
    }

    @Override
    public String call(String toolInput) {
        try {
            PalletAnomaliesRequest request = objectMapper.readValue(toolInput, PalletAnomaliesRequest.class);
            return objectMapper.writeValueAsString(warehouseTools.queryPalletAnomalies(request));
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid query_pallet_anomalies arguments or result.");
        }
    }
}
