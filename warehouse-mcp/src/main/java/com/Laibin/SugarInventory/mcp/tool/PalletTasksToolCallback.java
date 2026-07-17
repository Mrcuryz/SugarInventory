package com.Laibin.SugarInventory.mcp.tool;

import com.Laibin.SugarInventory.mcp.model.ToolModels.PalletTasksRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;

public final class PalletTasksToolCallback implements ToolCallback {
    private final WarehouseTools tools;
    private final ObjectMapper mapper;
    private final ToolDefinition definition;

    public PalletTasksToolCallback(WarehouseTools tools, ObjectMapper mapper, String schema) {
        this.tools = tools;
        this.mapper = mapper;
        this.definition = DefaultToolDefinition.builder().name("query_pallet_tasks")
                .description("Read current pallet tasks with controlled filters and pagination without changing task state.")
                .inputSchema(schema).build();
    }

    @Override public ToolDefinition getToolDefinition() { return definition; }

    @Override public String call(String input) {
        try {
            return mapper.writeValueAsString(tools.queryPalletTasks(mapper.readValue(input, PalletTasksRequest.class)));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid query_pallet_tasks arguments.");
        }
    }
}
