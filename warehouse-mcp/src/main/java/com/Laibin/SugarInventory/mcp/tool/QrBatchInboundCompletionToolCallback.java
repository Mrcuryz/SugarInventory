package com.Laibin.SugarInventory.mcp.tool;

import com.Laibin.SugarInventory.mcp.model.ToolModels.QrBatchInboundCompletionRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;

public final class QrBatchInboundCompletionToolCallback implements ToolCallback {
    private final WarehouseTools warehouseTools;
    private final ObjectMapper objectMapper;
    private final ToolDefinition toolDefinition;

    public QrBatchInboundCompletionToolCallback(WarehouseTools warehouseTools, ObjectMapper objectMapper, String inputSchema) {
        this.warehouseTools = warehouseTools;
        this.objectMapper = objectMapper;
        this.toolDefinition = DefaultToolDefinition.builder().name("query_qr_batch_inbound_completion")
                .description("Read QR or pallet label inbound completion for a label batch or production order.")
                .inputSchema(inputSchema).build();
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return toolDefinition;
    }

    @Override
    public String call(String toolInput) {
        try {
            QrBatchInboundCompletionRequest request = objectMapper.readValue(toolInput, QrBatchInboundCompletionRequest.class);
            return objectMapper.writeValueAsString(warehouseTools.queryQrBatchInboundCompletion(request));
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid query_qr_batch_inbound_completion arguments or result.");
        }
    }
}
