package com.Laibin.SugarInventory.mcp.tool;

import com.Laibin.SugarInventory.mcp.model.ToolModels.AutoInboundBatchesRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;

public final class AutoInboundBatchesToolCallback implements ToolCallback {
    private final WarehouseTools tools; private final ObjectMapper mapper; private final ToolDefinition definition;
    public AutoInboundBatchesToolCallback(WarehouseTools tools, ObjectMapper mapper, String schema) {
        this.tools = tools; this.mapper = mapper;
        this.definition = DefaultToolDefinition.builder().name("query_auto_inbound_batches")
                .description("Read the current user's recent non-expired auto-inbound parse batches without confirming inbound.")
                .inputSchema(schema).build();
    }
    @Override public ToolDefinition getToolDefinition() { return definition; }
    @Override public String call(String input) {
        try { return mapper.writeValueAsString(tools.queryAutoInboundBatches(mapper.readValue(input, AutoInboundBatchesRequest.class))); }
        catch (JsonProcessingException e) { throw new IllegalArgumentException("Invalid query_auto_inbound_batches arguments."); }
    }
}
