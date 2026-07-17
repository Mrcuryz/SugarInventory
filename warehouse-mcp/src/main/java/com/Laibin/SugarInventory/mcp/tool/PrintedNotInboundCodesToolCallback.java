package com.Laibin.SugarInventory.mcp.tool;

import com.Laibin.SugarInventory.mcp.model.ToolModels.PrintedNotInboundCodesRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;

public final class PrintedNotInboundCodesToolCallback implements ToolCallback {
    private final WarehouseTools warehouseTools;
    private final ObjectMapper objectMapper;
    private final ToolDefinition toolDefinition;

    public PrintedNotInboundCodesToolCallback(WarehouseTools warehouseTools, ObjectMapper objectMapper, String inputSchema) {
        this.warehouseTools = warehouseTools;
        this.objectMapper = objectMapper;
        this.toolDefinition = DefaultToolDefinition.builder().name("query_printed_not_inbound_codes")
                .description("Read printed QR or pallet codes that have not completed inbound processing.")
                .inputSchema(inputSchema).build();
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return toolDefinition;
    }

    @Override
    public String call(String toolInput) {
        try {
            PrintedNotInboundCodesRequest request = objectMapper.readValue(toolInput, PrintedNotInboundCodesRequest.class);
            return objectMapper.writeValueAsString(warehouseTools.queryPrintedNotInboundCodes(request));
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid query_printed_not_inbound_codes arguments or result.");
        }
    }
}
