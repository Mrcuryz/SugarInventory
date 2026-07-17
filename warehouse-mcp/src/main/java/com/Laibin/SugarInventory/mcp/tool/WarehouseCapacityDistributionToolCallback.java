package com.Laibin.SugarInventory.mcp.tool;

import com.Laibin.SugarInventory.mcp.model.ToolModels.WarehouseCapacityDistributionRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;

public final class WarehouseCapacityDistributionToolCallback implements ToolCallback {
    private final WarehouseTools tools; private final ObjectMapper mapper; private final ToolDefinition definition;
    public WarehouseCapacityDistributionToolCallback(WarehouseTools tools, ObjectMapper mapper, String schema) {
        this.tools = tools; this.mapper = mapper;
        this.definition = DefaultToolDefinition.builder().name("query_warehouse_capacity_distribution")
                .description("Read current capacity, occupancy and remaining-capacity facts across warehouses; display bands are not business risk decisions.")
                .inputSchema(schema).build();
    }
    @Override public ToolDefinition getToolDefinition() { return definition; }
    @Override public String call(String input) {
        try { return mapper.writeValueAsString(tools.queryWarehouseCapacityDistribution(mapper.readValue(input, WarehouseCapacityDistributionRequest.class))); }
        catch (JsonProcessingException e) { throw new IllegalArgumentException("Invalid query_warehouse_capacity_distribution arguments."); }
    }
}
