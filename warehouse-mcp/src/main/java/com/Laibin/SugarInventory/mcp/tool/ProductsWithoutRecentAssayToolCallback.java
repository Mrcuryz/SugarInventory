package com.Laibin.SugarInventory.mcp.tool;

import com.Laibin.SugarInventory.mcp.model.ToolModels.ProductsWithoutRecentAssayRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;

public final class ProductsWithoutRecentAssayToolCallback implements ToolCallback {
    private final WarehouseTools warehouseTools;
    private final ObjectMapper objectMapper;
    private final ToolDefinition toolDefinition;

    public ProductsWithoutRecentAssayToolCallback(WarehouseTools warehouseTools, ObjectMapper objectMapper, String inputSchema) {
        this.warehouseTools = warehouseTools;
        this.objectMapper = objectMapper;
        this.toolDefinition = DefaultToolDefinition.builder()
                .name("query_products_without_recent_assay")
                .description("Read current inventory product, warehouse, or product-warehouse groups that have no valid assay in a controlled date range.")
                .inputSchema(inputSchema)
                .build();
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return toolDefinition;
    }

    @Override
    public String call(String toolInput) {
        ProductsWithoutRecentAssayRequest request;
        try {
            request = objectMapper.readValue(toolInput, ProductsWithoutRecentAssayRequest.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid query_products_without_recent_assay arguments.");
        }
        try {
            return objectMapper.writeValueAsString(warehouseTools.queryProductsWithoutRecentAssay(request));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to serialize query_products_without_recent_assay result.");
        }
    }
}
