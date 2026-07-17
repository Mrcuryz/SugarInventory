package com.Laibin.SugarInventory.mcp.tool;

import com.Laibin.SugarInventory.mcp.model.ToolModels.WarehouseRecentOperationsRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;

public final class WarehouseRecentOperationsToolCallback implements ToolCallback {
    private final WarehouseTools tools; private final ObjectMapper mapper; private final ToolDefinition definition;
    public WarehouseRecentOperationsToolCallback(WarehouseTools tools, ObjectMapper mapper, String schema) {
        this.tools = tools; this.mapper = mapper;
        this.definition = DefaultToolDefinition.builder().name("query_warehouse_recent_operations")
                .description("Read recorded recent pallet-flow events for one warehouse or all warehouses; not a complete audit ledger.")
                .inputSchema(schema).build();
    }
    @Override public ToolDefinition getToolDefinition() { return definition; }
    @Override public String call(String input) {
        try { return mapper.writeValueAsString(tools.queryWarehouseRecentOperations(mapper.readValue(input, WarehouseRecentOperationsRequest.class))); }
        catch (JsonProcessingException e) { throw new IllegalArgumentException("Invalid query_warehouse_recent_operations arguments."); }
    }
}
