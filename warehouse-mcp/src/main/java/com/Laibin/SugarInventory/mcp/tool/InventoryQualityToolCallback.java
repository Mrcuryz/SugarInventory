package com.Laibin.SugarInventory.mcp.tool;

import com.Laibin.SugarInventory.mcp.model.ToolModels.InventoryQualityRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;

public final class InventoryQualityToolCallback implements ToolCallback {
    private final WarehouseTools warehouseTools;
    private final ObjectMapper objectMapper;
    private final String toolName;
    private final String mode;
    private final ToolDefinition toolDefinition;

    public InventoryQualityToolCallback(WarehouseTools warehouseTools, ObjectMapper objectMapper,
                                        String toolName, String mode, String description, String inputSchema) {
        this.warehouseTools = warehouseTools;
        this.objectMapper = objectMapper;
        this.toolName = toolName;
        this.mode = mode;
        this.toolDefinition = DefaultToolDefinition.builder()
                .name(toolName)
                .description(description)
                .inputSchema(inputSchema)
                .build();
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return toolDefinition;
    }

    @Override
    public String call(String toolInput) {
        try {
            ObjectNode arguments = (ObjectNode) objectMapper.readTree(toolInput);
            arguments.put("mode", mode);
            if ("JUDGE_STATUS".equals(mode)) {
                arguments.put("judgeStatus", "FAIL");
            }
            InventoryQualityRequest request = objectMapper.treeToValue(arguments, InventoryQualityRequest.class);
            return objectMapper.writeValueAsString(warehouseTools.queryInventoryQuality(toolName, request));
        } catch (JsonProcessingException | ClassCastException e) {
            throw new IllegalArgumentException("Invalid " + toolName + " arguments.");
        }
    }
}
