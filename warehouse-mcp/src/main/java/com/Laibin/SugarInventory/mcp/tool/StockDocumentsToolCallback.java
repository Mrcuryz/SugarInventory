package com.Laibin.SugarInventory.mcp.tool;

import com.Laibin.SugarInventory.mcp.model.ToolModels.StockDocumentsRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;

public final class StockDocumentsToolCallback implements ToolCallback {
    private final WarehouseTools tools; private final ObjectMapper mapper; private final ToolDefinition definition;
    public StockDocumentsToolCallback(WarehouseTools tools, ObjectMapper mapper, String schema) {
        this.tools = tools; this.mapper = mapper;
        this.definition = DefaultToolDefinition.builder().name("query_stock_documents")
                .description("Read one explicit stock document source with controlled filters and pagination.")
                .inputSchema(schema).build();
    }
    @Override public ToolDefinition getToolDefinition() { return definition; }
    @Override public String call(String input) {
        try { return mapper.writeValueAsString(tools.queryStockDocuments(mapper.readValue(input, StockDocumentsRequest.class))); }
        catch (JsonProcessingException e) { throw new IllegalArgumentException("Invalid query_stock_documents arguments."); }
    }
}
