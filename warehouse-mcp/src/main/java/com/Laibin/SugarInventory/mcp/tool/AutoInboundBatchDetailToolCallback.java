package com.Laibin.SugarInventory.mcp.tool;

import com.Laibin.SugarInventory.mcp.model.ToolModels.AutoInboundBatchDetailRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;

public final class AutoInboundBatchDetailToolCallback implements ToolCallback {
    private final WarehouseTools tools; private final ObjectMapper mapper; private final ToolDefinition definition;
    public AutoInboundBatchDetailToolCallback(WarehouseTools tools, ObjectMapper mapper, String schema) {
        this.tools = tools; this.mapper = mapper;
        this.definition = DefaultToolDefinition.builder().name("get_auto_inbound_batch_detail")
                .description("Read a selected current-user auto-inbound parse batch using an opaque batchRef.")
                .inputSchema(schema).build();
    }
    @Override public ToolDefinition getToolDefinition() { return definition; }
    @Override public String call(String input) {
        try { return mapper.writeValueAsString(tools.getAutoInboundBatchDetail(mapper.readValue(input, AutoInboundBatchDetailRequest.class))); }
        catch (JsonProcessingException e) { throw new IllegalArgumentException("Invalid get_auto_inbound_batch_detail arguments."); }
    }
}
