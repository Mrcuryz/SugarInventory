package com.Laibin.SugarInventory.mcp.tool;

import com.Laibin.SugarInventory.mcp.model.ToolModels.PalletLifecycleRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;

public final class PalletLifecycleToolCallback implements ToolCallback {
    private final WarehouseTools warehouseTools;
    private final ObjectMapper objectMapper;
    private final ToolDefinition toolDefinition;

    public PalletLifecycleToolCallback(WarehouseTools warehouseTools, ObjectMapper objectMapper, String inputSchema) {
        this.warehouseTools = warehouseTools;
        this.objectMapper = objectMapper;
        this.toolDefinition = DefaultToolDefinition.builder().name("query_qr_code_lifecycle")
                .description("Read a QR or pallet code lifecycle timeline without modifying warehouse data.")
                .inputSchema(inputSchema).build();
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return toolDefinition;
    }

    @Override
    public String call(String toolInput) {
        try {
            PalletLifecycleRequest request = objectMapper.readValue(toolInput, PalletLifecycleRequest.class);
            return objectMapper.writeValueAsString(warehouseTools.queryQrCodeLifecycle(request));
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid query_qr_code_lifecycle arguments or result.");
        }
    }
}
