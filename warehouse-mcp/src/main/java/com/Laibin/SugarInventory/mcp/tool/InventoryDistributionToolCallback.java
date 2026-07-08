package com.Laibin.SugarInventory.mcp.tool;

import com.Laibin.SugarInventory.mcp.model.ToolModels.InventoryDistributionRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;

public final class InventoryDistributionToolCallback implements ToolCallback {
    private final WarehouseTools warehouseTools;
    private final ObjectMapper objectMapper;
    private final ToolDefinition toolDefinition;

    public InventoryDistributionToolCallback(WarehouseTools warehouseTools, ObjectMapper objectMapper, String inputSchema) {
        this.warehouseTools = warehouseTools;
        this.objectMapper = objectMapper;
        this.toolDefinition = DefaultToolDefinition.builder()
                .name("get_inventory_distribution")
                .description("Read filtered current inventory distribution for a controlled product and warehouse scope without modifying warehouse data.")
                .inputSchema(inputSchema)
                .build();
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return toolDefinition;
    }

    @Override
    public String call(String toolInput) {
        InventoryDistributionRequest request;
        try {
            request = objectMapper.readValue(toolInput, InventoryDistributionRequest.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid get_inventory_distribution arguments.");
        }
        try {
            return objectMapper.writeValueAsString(warehouseTools.getInventoryDistribution(request));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to serialize get_inventory_distribution result.");
        }
    }
}
