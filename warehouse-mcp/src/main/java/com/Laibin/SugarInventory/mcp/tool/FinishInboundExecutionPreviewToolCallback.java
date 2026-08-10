package com.Laibin.SugarInventory.mcp.tool;

import com.Laibin.SugarInventory.mcp.model.ToolModels.FinishInboundExecutionPreviewRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;

public final class FinishInboundExecutionPreviewToolCallback implements ToolCallback {
    private final WarehouseTools tools;
    private final ObjectMapper mapper;
    private final ToolDefinition definition;

    public FinishInboundExecutionPreviewToolCallback(WarehouseTools tools, ObjectMapper mapper, String schema) {
        this.tools = tools;
        this.mapper = mapper;
        this.definition = DefaultToolDefinition.builder()
                .name("preview_finish_inbound_execution")
                .description("Create an exact no-write preview from a completed finished-product inbound form. It never confirms the task or changes inventory.")
                .inputSchema(schema)
                .build();
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return definition;
    }

    @Override
    public String call(String input) {
        try {
            return mapper.writeValueAsString(tools.previewFinishInboundExecution(
                    mapper.readValue(input, FinishInboundExecutionPreviewRequest.class)));
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid preview_finish_inbound_execution arguments.");
        }
    }
}
