package com.Laibin.SugarInventory.mcp.config;

import com.Laibin.SugarInventory.mcp.tool.WarehouseTools;
import org.springframework.ai.tool.StaticToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.execution.DefaultToolCallResultConverter;
import org.springframework.ai.tool.method.MethodToolCallback;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.lang.reflect.Method;
import java.util.List;

@Configuration
public class ToolConfiguration {

    @Bean
    ToolCallbackProvider warehouseToolCallbackProvider(WarehouseTools warehouseTools) throws NoSuchMethodException {
        return new StaticToolCallbackProvider(List.of(
                methodTool(warehouseTools, "resolve_products",
                        "Resolve a natural-language product query to unique or ambiguous product candidates without modifying warehouse data.",
                        resolveProductsSchema(),
                        WarehouseTools.class.getMethod("resolveProducts", String.class, String.class, String.class, Integer.class)),
                methodTool(warehouseTools, "resolve_warehouses",
                        "Resolve a natural-language warehouse query to unique or ambiguous warehouse candidates without modifying warehouse data.",
                        resolveWarehousesSchema(),
                        WarehouseTools.class.getMethod("resolveWarehouses", String.class, Boolean.class, Integer.class)),
                methodTool(warehouseTools, "get_inventory_overview",
                        "Read paged inventory overview and totals, preferring a unique productId when available.",
                        inventoryOverviewSchema(),
                        WarehouseTools.class.getMethod("getInventoryOverview", Integer.class, String.class, String.class, Integer.class, Integer.class)),
                methodTool(warehouseTools, "get_inventory_distribution",
                        "Read filtered current inventory distribution for a controlled product and warehouse scope without modifying warehouse data.",
                        inventoryDistributionSchema(),
                        WarehouseTools.class.getMethod("getInventoryDistribution",
                                com.Laibin.SugarInventory.mcp.model.ToolModels.ProductScope.class,
                                com.Laibin.SugarInventory.mcp.model.ToolModels.WarehouseScope.class,
                                com.Laibin.SugarInventory.mcp.model.ToolModels.InventoryDistributionFilter.class,
                                String.class, Integer.class)),
                methodTool(warehouseTools, "get_warehouse_status",
                        "Read warehouse capacity, inventory details, and recent operations, preferring a unique warehouseId when available.",
                        warehouseStatusSchema(),
                        WarehouseTools.class.getMethod("getWarehouseStatus", Integer.class, String.class, Boolean.class, Boolean.class, Integer.class, Integer.class, Integer.class)),
                methodTool(warehouseTools, "get_pallet_status",
                        "Read pallet code status, current inventory position, assay information, flow cycles, and flow details without modifying warehouse data.",
                        palletStatusSchema(),
                        WarehouseTools.class.getMethod("getPalletStatus", String.class, Boolean.class, Boolean.class, Boolean.class, Integer.class, Integer.class)),
                methodTool(warehouseTools, "get_assay_status",
                        "Read an assay by id, or by product and production date, including judge result, failed metrics, and applied standard details.",
                        assayStatusSchema(),
                        WarehouseTools.class.getMethod("getAssayStatus", Integer.class, Integer.class, String.class, String.class, Boolean.class))
        ));
    }

    private static ToolCallback methodTool(WarehouseTools target, String name, String description, String inputSchema, Method method) {
        return MethodToolCallback.builder()
                .toolDefinition(DefaultToolDefinition.builder()
                        .name(name)
                        .description(description)
                        .inputSchema(inputSchema)
                        .build())
                .toolMethod(method)
                .toolObject(target)
                .toolCallResultConverter(new DefaultToolCallResultConverter())
                .build();
    }

    private static String resolveProductsSchema() {
        return """
                {"type":"object","additionalProperties":false,"required":["query"],"properties":{"query":{"type":"string","minLength":1,"maxLength":100,"description":"Product id, name, normalized name, or alias fragment."},"productType":{"type":"string","minLength":1,"maxLength":50,"description":"Optional product type filter."},"productStatus":{"type":"string","minLength":1,"maxLength":50,"description":"Optional product status filter."},"limit":{"type":"integer","minimum":1,"maximum":100,"description":"Maximum candidates to return."}}}
                """;
    }

    private static String resolveWarehousesSchema() {
        return """
                {"type":"object","additionalProperties":false,"required":["query"],"properties":{"query":{"type":"string","minLength":1,"maxLength":100,"description":"Warehouse id, name, normalized name, or alias fragment."},"onlyAvailable":{"type":"boolean","description":"Filter out warehouses without free capacity when capacity fields are present."},"limit":{"type":"integer","minimum":1,"maximum":100,"description":"Maximum candidates to return."}}}
                """;
    }

    private static String inventoryOverviewSchema() {
        return """
                {"type":"object","additionalProperties":false,"properties":{"productId":{"type":"integer","minimum":1,"description":"Preferred unique product id."},"productQuery":{"type":"string","minLength":1,"maxLength":100,"description":"Product query used only when productId is absent."},"productStatus":{"type":"string","minLength":1,"maxLength":50,"description":"Optional product status filter."},"page":{"type":"integer","minimum":1,"description":"Page number."},"size":{"type":"integer","minimum":1,"maximum":100,"description":"Page size."}}}
                """;
    }

    private static String inventoryDistributionSchema() {
        return """
                {"type":"object","additionalProperties":false,"required":["productScope","warehouseScope","groupBy"],"properties":{"productScope":{"type":"object","additionalProperties":false,"required":["type"],"properties":{"type":{"type":"string","enum":["SINGLE_PRODUCT","EXACT_PRODUCT_NAME_GROUP","PRODUCT_TYPE_GROUP","ALL"]},"productId":{"type":"integer","minimum":1},"productName":{"type":"string","minLength":1,"maxLength":100},"productType":{"type":"string","minLength":1,"maxLength":50}}},"warehouseScope":{"type":"object","additionalProperties":false,"required":["type"],"properties":{"type":{"type":"string","enum":["ALL","SINGLE_WAREHOUSE"]},"warehouseId":{"type":"integer","minimum":1}}},"statusFilter":{"type":"object","additionalProperties":false,"properties":{"productStatuses":{"type":"array","maxItems":10,"items":{"type":"string","enum":["半成品","成品"]}},"warehouseStatuses":{"type":"array","maxItems":10,"items":{"type":"string","enum":["正常","空置","满仓","维护","临期预警"]}},"palletStatuses":{"type":"array","maxItems":10,"items":{"type":"string","enum":["FREE","PENDING","INSTOCK","INVALID","ORDER_RESERVED"]}},"assayStatus":{"type":"string","enum":["HAS_ASSAY","MISSING_ASSAY","PASS","FAIL","NO_STANDARD","MULTIPLE_CANDIDATES"]},"entryDateFrom":{"type":"string","format":"date"},"entryDateTo":{"type":"string","format":"date"}}},"groupBy":{"type":"string","enum":["warehouse","product","warehouse_product"]},"limit":{"type":"integer","minimum":1,"maximum":100,"default":20}}}
                """;
    }

    private static String warehouseStatusSchema() {
        return """
                {"type":"object","additionalProperties":false,"properties":{"warehouseId":{"type":"integer","minimum":1,"description":"Preferred unique warehouse id."},"warehouseQuery":{"type":"string","minLength":1,"maxLength":100,"description":"Warehouse query used only when warehouseId is absent."},"includeInventoryDetails":{"type":"boolean","description":"Include paged inventory details."},"includeRecentOperations":{"type":"boolean","description":"Include recent operation records."},"page":{"type":"integer","minimum":1,"description":"Page number."},"size":{"type":"integer","minimum":1,"maximum":100,"description":"Page size."},"recentLimit":{"type":"integer","minimum":1,"maximum":100,"description":"Recent operation limit."}}}
                """;
    }
    private static String palletStatusSchema() {
        return """
                {"type":"object","additionalProperties":false,"required":["code"],"properties":{"code":{"type":"string","minLength":1,"maxLength":100,"description":"Pallet code."},"includeInventory":{"type":"boolean","description":"Include current inventory position. Defaults to true."},"includeAssay":{"type":"boolean","description":"Include resolved assay information. Defaults to true."},"includeFlows":{"type":"boolean","description":"Include flow cycles and flow details. Defaults to true."},"cycleNo":{"type":"integer","minimum":1,"description":"Optional flow cycle number."},"flowLimit":{"type":"integer","minimum":1,"maximum":100,"description":"Maximum flow cycles/details to return."}}}
                """;
    }

    private static String assayStatusSchema() {
        return """
                {"type":"object","additionalProperties":false,"properties":{"assayId":{"type":"integer","minimum":1,"description":"Preferred assay id."},"productId":{"type":"integer","minimum":1,"description":"Product id used with productionDate."},"productionDate":{"type":"string","minLength":10,"maxLength":10,"format":"date","description":"Production date in yyyy-MM-dd format."},"productQuery":{"type":"string","minLength":1,"maxLength":100,"description":"Product query used only when productId is absent."},"includeStandardDetails":{"type":"boolean","description":"Include applied standard and failed metric details. Defaults to true."}}}
                """;
    }
}
