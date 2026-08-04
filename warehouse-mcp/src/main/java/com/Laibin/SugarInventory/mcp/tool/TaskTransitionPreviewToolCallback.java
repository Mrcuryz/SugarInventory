package com.Laibin.SugarInventory.mcp.tool;

import com.Laibin.SugarInventory.mcp.model.ToolModels.TaskTransitionPreviewRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;

public final class TaskTransitionPreviewToolCallback implements ToolCallback {
    private final WarehouseTools tools;
    private final ObjectMapper mapper;
    private final ToolDefinition definition;

    public TaskTransitionPreviewToolCallback(WarehouseTools tools, ObjectMapper mapper, String schema) {
        this.tools = tools;
        this.mapper = mapper;
        this.definition = DefaultToolDefinition.builder()
                .name("preview_task_transition")
                .description("Preview selected pending finished-product inbound or outbound tasks without changing any business data.")
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
            return mapper.writeValueAsString(tools.previewTaskTransition(
                    mapper.readValue(input, TaskTransitionPreviewRequest.class)));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid preview_task_transition arguments.");
        }
    }
}
