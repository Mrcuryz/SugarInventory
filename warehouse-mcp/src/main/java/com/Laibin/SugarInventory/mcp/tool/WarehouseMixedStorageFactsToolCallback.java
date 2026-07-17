package com.Laibin.SugarInventory.mcp.tool;

import com.Laibin.SugarInventory.mcp.model.ToolModels.WarehouseMixedStorageFactsRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;

public final class WarehouseMixedStorageFactsToolCallback implements ToolCallback {
    private final WarehouseTools tools; private final ObjectMapper mapper; private final ToolDefinition definition;
    public WarehouseMixedStorageFactsToolCallback(WarehouseTools tools, ObjectMapper mapper, String schema) {
        this.tools = tools; this.mapper = mapper;
        this.definition = DefaultToolDefinition.builder().name("query_warehouse_mixed_storage_facts")
                .description("Read current same-warehouse multiple-product or multiple-specification facts without deciding mixed-storage risk.")
                .inputSchema(schema).build();
    }
    @Override public ToolDefinition getToolDefinition() { return definition; }
    @Override public String call(String input) {
        try { return mapper.writeValueAsString(tools.queryWarehouseMixedStorageFacts(mapper.readValue(input, WarehouseMixedStorageFactsRequest.class))); }
        catch (JsonProcessingException e) { throw new IllegalArgumentException("Invalid query_warehouse_mixed_storage_facts arguments."); }
    }
}
