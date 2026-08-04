package com.Laibin.SugarInventory.mcp.config;

import com.Laibin.SugarInventory.mcp.tool.WarehouseTools;
import com.Laibin.SugarInventory.mcp.tool.AssayAbnormalitiesToolCallback;
import com.Laibin.SugarInventory.mcp.tool.AssayReportDetailToolCallback;
import com.Laibin.SugarInventory.mcp.tool.AssayRecordsToolCallback;
import com.Laibin.SugarInventory.mcp.tool.AssayStandardCoverageToolCallback;
import com.Laibin.SugarInventory.mcp.tool.InventoryDistributionToolCallback;
import com.Laibin.SugarInventory.mcp.tool.InventoryQualityToolCallback;
import com.Laibin.SugarInventory.mcp.tool.ProductsWithoutRecentAssayToolCallback;
import com.Laibin.SugarInventory.mcp.tool.PalletLifecycleToolCallback;
import com.Laibin.SugarInventory.mcp.tool.PrintedNotInboundCodesToolCallback;
import com.Laibin.SugarInventory.mcp.tool.PalletAnomaliesToolCallback;
import com.Laibin.SugarInventory.mcp.tool.PalletFlowRecordsToolCallback;
import com.Laibin.SugarInventory.mcp.tool.QrBatchInboundCompletionToolCallback;
import com.Laibin.SugarInventory.mcp.tool.PalletTasksToolCallback;
import com.Laibin.SugarInventory.mcp.tool.TaskTransitionPreviewToolCallback;
import com.Laibin.SugarInventory.mcp.tool.StockDocumentsToolCallback;
import com.Laibin.SugarInventory.mcp.tool.AutoInboundBatchesToolCallback;
import com.Laibin.SugarInventory.mcp.tool.AutoInboundBatchDetailToolCallback;
import com.Laibin.SugarInventory.mcp.tool.WarehouseCapacityDistributionToolCallback;
import com.Laibin.SugarInventory.mcp.tool.WarehouseRecentOperationsToolCallback;
import com.Laibin.SugarInventory.mcp.tool.WarehouseMixedStorageFactsToolCallback;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    ToolCallbackProvider warehouseToolCallbackProvider(WarehouseTools warehouseTools, ObjectMapper objectMapper) throws NoSuchMethodException {
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
                new InventoryDistributionToolCallback(warehouseTools, objectMapper, inventoryDistributionSchema()),
                new InventoryQualityToolCallback(warehouseTools, objectMapper,
                        "query_unqualified_inventory", "JUDGE_STATUS",
                        "Read current inventory whose latest product-and-production-date assay is explicitly unqualified.",
                        unqualifiedInventorySchema()),
                new InventoryQualityToolCallback(warehouseTools, objectMapper,
                        "query_inventory_by_quality_standard", "STANDARD",
                        "Read current inventory whose latest batch assay satisfies every metric of a controlled quality standard.",
                        inventoryByQualityStandardSchema()),
                new InventoryQualityToolCallback(warehouseTools, objectMapper,
                        "query_inventory_by_assay_metrics", "METRIC",
                        "Read current inventory whose latest batch assay meets one closed, numeric metric condition.",
                        inventoryByAssayMetricSchema()),
                new AssayRecordsToolCallback(warehouseTools, objectMapper, assayRecordsSchema()),
                new AssayReportDetailToolCallback(warehouseTools, objectMapper, assayReportDetailSchema()),
                new AssayAbnormalitiesToolCallback(warehouseTools, objectMapper, assayAbnormalitiesSchema()),
                new ProductsWithoutRecentAssayToolCallback(warehouseTools, objectMapper, productsWithoutRecentAssaySchema()),
                new AssayStandardCoverageToolCallback(warehouseTools, objectMapper, assayStandardCoverageSchema()),
                new PalletLifecycleToolCallback(warehouseTools, objectMapper, palletLifecycleSchema()),
                new PrintedNotInboundCodesToolCallback(warehouseTools, objectMapper, printedNotInboundCodesSchema()),
                new PalletAnomaliesToolCallback(warehouseTools, objectMapper, palletAnomaliesSchema()),
                new PalletFlowRecordsToolCallback(warehouseTools, objectMapper, palletFlowRecordsSchema()),
                new QrBatchInboundCompletionToolCallback(warehouseTools, objectMapper, qrBatchInboundCompletionSchema()),
                methodTool(warehouseTools, "resolve_production_entities",
                        "Resolve a production order number or boiling batch number to short-lived user-bound entity references.",
                        resolveProductionEntitiesSchema(),
                        WarehouseTools.class.getMethod("resolveProductionEntities", String.class, String.class, Integer.class)),
                methodTool(warehouseTools, "query_boiling_batches",
                        "List registered boiling batches by optional product, Beijing business-date range, and status without modifying production data.",
                        boilingBatchListSchema(),
                        WarehouseTools.class.getMethod("queryBoilingBatches", String.class, java.time.LocalDate.class,
                                java.time.LocalDate.class, String.class, Integer.class)),
                methodTool(warehouseTools, "query_production_order_progress",
                        "Read current production order plan, material, output, label, QR binding, and inbound progress using a controlled orderRef.",
                        productionOrderProgressSchema(),
                        WarehouseTools.class.getMethod("queryProductionOrderProgress", String.class)),
                methodTool(warehouseTools, "run_registered_report",
                        "Run one allowlisted, versioned production, quality, inventory-level, or registered pallet-task-cycle report using deterministic backend facts, with an optional same-definition previous-period or custom non-overlapping comparison. Inventory level uses trusted reconciled daily snapshots in normal environments; an explicitly enabled local simulation is labeled historical replay and never counts as production release evidence.",
                        registeredReportRunSchema(),
                        WarehouseTools.class.getMethod("runRegisteredReport", String.class, Integer.class,
                                java.time.LocalDate.class, java.time.LocalDate.class, String.class, String.class,
                                String.class, String.class, java.time.LocalDate.class, java.time.LocalDate.class)),
                methodTool(warehouseTools, "query_boiling_batch_trace",
                        "Read a registered boiling batch trace using a controlled batchRef without inferring missing relationships.",
                        boilingBatchTraceSchema(),
                        WarehouseTools.class.getMethod("queryBoilingBatchTrace", String.class)),
                methodTool(warehouseTools, "query_material_pick_trace",
                        "Read registered material pick records and pallet sources for a controlled production order.",
                        materialPickTraceSchema(),
                        WarehouseTools.class.getMethod("queryMaterialPickTrace", String.class)),
                methodTool(warehouseTools, "query_production_label_completion",
                        "Read label reservation, use, recycle, QR binding, and inbound completion for a controlled production order.",
                        productionLabelCompletionSchema(),
                        WarehouseTools.class.getMethod("queryProductionLabelCompletion", String.class)),
                methodTool(warehouseTools, "query_in_process_materials",
                        "Read confirmed semi-finished material picks already deducted from inventory and still associated with unfinished production orders, using controlled filters and pagination.",
                        productionInProcessMaterialsSchema(),
                        WarehouseTools.class.getMethod("queryInProcessMaterials", String.class, String.class,
                                String.class, String.class, Integer.class, Integer.class)),
                methodTool(warehouseTools, "query_material_candidates",
                        "Read current semi-finished inventory candidates for a controlled production order without recommending a pick.",
                        productionMaterialCandidatesSchema(),
                        WarehouseTools.class.getMethod("queryMaterialCandidates", String.class, Integer.class, Integer.class)),
                new PalletTasksToolCallback(warehouseTools, objectMapper, palletTasksSchema()),
                new TaskTransitionPreviewToolCallback(warehouseTools, objectMapper, taskTransitionPreviewSchema()),
                new StockDocumentsToolCallback(warehouseTools, objectMapper, stockDocumentsSchema()),
                new AutoInboundBatchesToolCallback(warehouseTools, objectMapper, autoInboundBatchesSchema()),
                new AutoInboundBatchDetailToolCallback(warehouseTools, objectMapper, autoInboundBatchDetailSchema()),
                new WarehouseCapacityDistributionToolCallback(warehouseTools, objectMapper, warehouseCapacityDistributionSchema()),
                new WarehouseRecentOperationsToolCallback(warehouseTools, objectMapper, warehouseRecentOperationsSchema()),
                new WarehouseMixedStorageFactsToolCallback(warehouseTools, objectMapper, warehouseMixedStorageFactsSchema()),
                methodTool(warehouseTools, "query_product_catalog", "Read the current product master-data catalog without inventory or quality claims.",
                        productCatalogSchema(), WarehouseTools.class.getMethod("queryProductCatalog", String.class, String.class, String.class, String.class, String.class, Integer.class, Integer.class)),
                methodTool(warehouseTools, "get_product_detail", "Read one exactly named product's current master-data detail without internal IDs.",
                        productDetailSchema(), WarehouseTools.class.getMethod("getProductDetail", String.class)),
                methodTool(warehouseTools, "query_screen_mesh_catalog", "Read the current screen-mesh master-data catalog without modifying configuration.",
                        screenMeshCatalogSchema(), WarehouseTools.class.getMethod("queryScreenMeshCatalog", String.class, Integer.class, Integer.class)),
                methodTool(warehouseTools, "query_assay_groups", "Read current assay product-group configuration without quality conclusions.",
                        assayGroupsSchema(), WarehouseTools.class.getMethod("queryAssayGroups", String.class, Integer.class, Integer.class)),
                methodTool(warehouseTools, "query_quality_standard_catalog", "Read current quality-standard catalog and versions without claiming report usage.",
                        qualityStandardCatalogSchema(), WarehouseTools.class.getMethod("queryQualityStandardCatalog", String.class, String.class, String.class, Integer.class, Integer.class)),
                methodTool(warehouseTools, "get_quality_standard_detail", "Read one exact quality-standard code and version with metrics.",
                        qualityStandardDetailSchema(), WarehouseTools.class.getMethod("getQualityStandardDetail", String.class, Integer.class)),
                methodTool(warehouseTools, "query_product_standard_relations", "Read current product-to-quality-standard bindings without changing them.",
                        productStandardRelationsSchema(), WarehouseTools.class.getMethod("queryProductStandardRelations", String.class)),
                methodTool(warehouseTools, "query_product_quality_configuration",
                        "Read one resolved product's current quality-standard bindings and assay-group memberships as one controlled fact.",
                        productQualityConfigurationSchema(),
                        WarehouseTools.class.getMethod("queryProductQualityConfiguration", Integer.class)),
                methodTool(warehouseTools, "query_employee_roster", "Read the current employee roster with masked mobile numbers and no credentials.",
                        employeeRosterSchema(), WarehouseTools.class.getMethod("queryEmployeeRoster", String.class, String.class, String.class, String.class, String.class, String.class, Integer.class, Integer.class)),
                methodTool(warehouseTools, "query_roles", "Read the current RBAC role catalog without internal identifiers.",
                        roleCatalogSchema(), WarehouseTools.class.getMethod("queryRoles", String.class, String.class, Integer.class, Integer.class)),
                methodTool(warehouseTools, "get_role_permission_summary", "Read one exact role permission summary without Agent internals or credentials.",
                        rolePermissionSummarySchema(), WarehouseTools.class.getMethod("getRolePermissionSummary", String.class)),
                methodTool(warehouseTools, "search_operation_logs", "Search recorded business-operation audit summaries without old/new field values.",
                        operationLogAuditSchema(), WarehouseTools.class.getMethod("searchOperationLogs", String.class, String.class, String.class, String.class, String.class, Integer.class, Integer.class)),
                methodTool(warehouseTools, "query_agent_tool_audit", "Query safe Agent tool-call audit categories without arguments, prompts, IDs, or stack traces.",
                        agentToolAuditSchema(), WarehouseTools.class.getMethod("queryAgentToolAudit", String.class, String.class, String.class, String.class, String.class, Integer.class, Integer.class)),
                methodTool(warehouseTools, "query_agent_answer_reviews", "Query safe Agent answer-review status summaries without user questions, answers, or decision snapshots.",
                        agentAnswerReviewSchema(), WarehouseTools.class.getMethod("queryAgentAnswerReviews", String.class, String.class, String.class, String.class, String.class, String.class, Boolean.class, Integer.class, Integer.class)),
                methodTool(warehouseTools, "query_inventory_ledger", "Query current inventory ledger rows; this is a current snapshot, not historical movement.",
                        inventoryLedgerSchema(), WarehouseTools.class.getMethod("queryInventoryLedger", String.class, String.class, String.class, String.class, String.class, String.class, Integer.class, Integer.class)),
                methodTool(warehouseTools, "query_fixed_product_qr_pool", "Query current fixed-product QR pool status without binding, printing, activating, invalidating, or restoring codes.",
                        fixedProductQrPoolSchema(), WarehouseTools.class.getMethod("queryFixedProductQrPool", String.class, List.class, String.class, Boolean.class, Integer.class, Integer.class)),
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

    private static String unqualifiedInventorySchema() {
        return """
                {"type":"object","additionalProperties":false,"required":["productScope","warehouseScope"],"properties":{"productScope":{"type":"object","additionalProperties":false,"required":["type"],"properties":{"type":{"type":"string","enum":["SINGLE_PRODUCT","EXACT_PRODUCT_NAME_GROUP","PRODUCT_TYPE_GROUP","ALL"]},"productId":{"type":"integer","minimum":1},"productName":{"type":"string","minLength":1,"maxLength":100},"productType":{"type":"string","minLength":1,"maxLength":50}}},"warehouseScope":{"type":"object","additionalProperties":false,"required":["type"],"properties":{"type":{"type":"string","enum":["ALL","SINGLE_WAREHOUSE"]},"warehouseId":{"type":"integer","minimum":1}}},"limit":{"type":"integer","minimum":1,"maximum":100,"default":50}}}
                """;
    }

    private static String inventoryByQualityStandardSchema() {
        return """
                {"type":"object","additionalProperties":false,"required":["productScope","warehouseScope","standardCode"],"properties":{"productScope":{"type":"object","additionalProperties":false,"required":["type"],"properties":{"type":{"type":"string","enum":["SINGLE_PRODUCT","EXACT_PRODUCT_NAME_GROUP","PRODUCT_TYPE_GROUP","ALL"]},"productId":{"type":"integer","minimum":1},"productName":{"type":"string","minLength":1,"maxLength":100},"productType":{"type":"string","minLength":1,"maxLength":50}}},"warehouseScope":{"type":"object","additionalProperties":false,"required":["type"],"properties":{"type":{"type":"string","enum":["ALL","SINGLE_WAREHOUSE"]},"warehouseId":{"type":"integer","minimum":1}}},"standardCode":{"type":"string","pattern":"^[A-Za-z0-9_-]{1,64}$","description":"Controlled standardCode returned by query_quality_standard_catalog."},"standardVersion":{"type":"integer","minimum":1},"limit":{"type":"integer","minimum":1,"maximum":100,"default":50}}}
                """;
    }

    private static String inventoryByAssayMetricSchema() {
        return """
                {"type":"object","additionalProperties":false,"required":["productScope","warehouseScope","metricCondition"],"properties":{"productScope":{"type":"object","additionalProperties":false,"required":["type"],"properties":{"type":{"type":"string","enum":["SINGLE_PRODUCT","EXACT_PRODUCT_NAME_GROUP","PRODUCT_TYPE_GROUP","ALL"]},"productId":{"type":"integer","minimum":1},"productName":{"type":"string","minLength":1,"maxLength":100},"productType":{"type":"string","minLength":1,"maxLength":50}}},"warehouseScope":{"type":"object","additionalProperties":false,"required":["type"],"properties":{"type":{"type":"string","enum":["ALL","SINGLE_WAREHOUSE"]},"warehouseId":{"type":"integer","minimum":1}}},"metricCondition":{"type":"object","additionalProperties":false,"required":["metricCode","operator"],"properties":{"metricCode":{"type":"string","enum":["color_value","reducing_sugar","dry_weight_loss","conductivity_ash","sucrose","insoluble_impurity","ph"]},"operator":{"type":"string","enum":["GT","GTE","LT","LTE","EQ","BETWEEN"]},"value":{"type":"number"},"minValue":{"type":"number"},"maxValue":{"type":"number"}}},"limit":{"type":"integer","minimum":1,"maximum":100,"default":50}}}
                """;
    }

    private static String assayRecordsSchema() {
        return """
                {"type":"object","additionalProperties":false,"required":["productScope"],"properties":{"productScope":{"type":"object","additionalProperties":false,"required":["type"],"properties":{"type":{"type":"string","enum":["SINGLE_PRODUCT","EXACT_PRODUCT_NAME_GROUP","PRODUCT_TYPE_GROUP","ALL"]},"productId":{"type":"integer","minimum":1},"productName":{"type":"string","minLength":1,"maxLength":100},"productType":{"type":"string","minLength":1,"maxLength":50}}},"dateRange":{"type":"object","additionalProperties":false,"required":["type"],"properties":{"type":{"type":"string","enum":["EXACT","LAST_DAYS","RANGE"]},"date":{"type":"string","format":"date"},"days":{"type":"integer","minimum":1,"maximum":366},"from":{"type":"string","format":"date"},"to":{"type":"string","format":"date"}}},"judgeStatus":{"type":"string","enum":["ANY","PASS","FAILED","NO_STANDARD","MULTIPLE_CANDIDATES"],"default":"ANY"},"sortBy":{"type":"string","enum":["sampleDate","createdAt"],"default":"sampleDate"},"sortDirection":{"type":"string","enum":["ASC","DESC"],"default":"DESC"},"page":{"type":"integer","minimum":1,"default":1},"size":{"type":"integer","minimum":1,"maximum":100,"default":20}}}
                """;
    }

    private static String assayReportDetailSchema() {
        return """
                {"type":"object","additionalProperties":false,"required":["reportRef"],"properties":{"reportRef":{"type":"string","minLength":1,"maxLength":200,"description":"Opaque reportRef returned by query_assay_records; do not invent from an assay id."},"includeMetrics":{"type":"boolean","default":true},"includeStandardSnapshot":{"type":"boolean","default":true}}}
                """;
    }

    private static String assayAbnormalitiesSchema() {
        return """
                {"type":"object","additionalProperties":false,"required":["productScope"],"properties":{"productScope":{"type":"object","additionalProperties":false,"required":["type"],"properties":{"type":{"type":"string","enum":["SINGLE_PRODUCT","EXACT_PRODUCT_NAME_GROUP","PRODUCT_TYPE_GROUP","ALL"]},"productId":{"type":"integer","minimum":1},"productName":{"type":"string","minLength":1,"maxLength":100},"productType":{"type":"string","minLength":1,"maxLength":50}}},"dateRange":{"type":"object","additionalProperties":false,"required":["type"],"properties":{"type":{"type":"string","enum":["EXACT","LAST_DAYS","RANGE"]},"date":{"type":"string","format":"date"},"days":{"type":"integer","minimum":1,"maximum":366},"from":{"type":"string","format":"date"},"to":{"type":"string","format":"date"}}},"abnormalTypes":{"type":"array","minItems":1,"maxItems":3,"items":{"type":"string","enum":["FAILED","NO_STANDARD","MULTIPLE_CANDIDATES"]},"default":["FAILED","NO_STANDARD","MULTIPLE_CANDIDATES"]},"groupBy":{"type":"string","enum":["product","date","abnormal_type","metric"],"default":"product"},"limit":{"type":"integer","minimum":1,"maximum":100,"default":50}}}
                """;
    }

    private static String productsWithoutRecentAssaySchema() {
        return """
                {"type":"object","additionalProperties":false,"required":["productScope","warehouseScope"],"properties":{"productScope":{"type":"object","additionalProperties":false,"required":["type"],"properties":{"type":{"type":"string","enum":["SINGLE_PRODUCT","EXACT_PRODUCT_NAME_GROUP","PRODUCT_TYPE_GROUP","ALL"]},"productId":{"type":"integer","minimum":1},"productName":{"type":"string","minLength":1,"maxLength":100},"productType":{"type":"string","minLength":1,"maxLength":50}}},"warehouseScope":{"type":"object","additionalProperties":false,"required":["type"],"properties":{"type":{"type":"string","enum":["ALL","SINGLE_WAREHOUSE"]},"warehouseId":{"type":"integer","minimum":1}}},"population":{"type":"string","enum":["CURRENT_INVENTORY"],"default":"CURRENT_INVENTORY"},"dateRange":{"type":"object","additionalProperties":false,"required":["type"],"properties":{"type":{"type":"string","enum":["EXACT","LAST_DAYS","RANGE"]},"date":{"type":"string","format":"date"},"days":{"type":"integer","minimum":1,"maximum":366},"from":{"type":"string","format":"date"},"to":{"type":"string","format":"date"}}},"groupBy":{"type":"string","enum":["product","warehouse","product_warehouse"],"default":"product"},"limit":{"type":"integer","minimum":1,"maximum":100,"default":50}}}
                """;
    }

    private static String assayStandardCoverageSchema() {
        return """
                {"type":"object","additionalProperties":false,"required":["productScope"],"properties":{"productScope":{"type":"object","additionalProperties":false,"required":["type"],"properties":{"type":{"type":"string","enum":["SINGLE_PRODUCT","EXACT_PRODUCT_NAME_GROUP","PRODUCT_TYPE_GROUP","ALL"]},"productId":{"type":"integer","minimum":1},"productName":{"type":"string","minLength":1,"maxLength":100},"productType":{"type":"string","minLength":1,"maxLength":50}}},"dateRange":{"type":"object","additionalProperties":false,"required":["type"],"properties":{"type":{"type":"string","enum":["EXACT","LAST_DAYS","RANGE"]},"date":{"type":"string","format":"date"},"days":{"type":"integer","minimum":1,"maximum":366},"from":{"type":"string","format":"date"},"to":{"type":"string","format":"date"}}},"coverageType":{"type":"string","enum":["PRODUCT_WITHOUT_STANDARD","ASSAY_WITHOUT_STANDARD","UNUSED_STANDARD"],"default":"PRODUCT_WITHOUT_STANDARD","description":"First implementation supports PRODUCT_WITHOUT_STANDARD only; other values are reserved until business rules are confirmed."},"limit":{"type":"integer","minimum":1,"maximum":100,"default":50}}}
        """;
    }

    private static String palletLifecycleSchema() {
        return """
                {"type":"object","additionalProperties":false,"required":["code"],"properties":{"code":{"type":"string","minLength":1,"maxLength":100},"includeInventory":{"type":"boolean","default":true},"includeAssay":{"type":"boolean","default":true},"includeFlows":{"type":"boolean","default":true},"includePrintInfo":{"type":"boolean","default":false},"flowLimit":{"type":"integer","minimum":1,"maximum":100,"default":50}}}
                """;
    }

    private static String printedNotInboundCodesSchema() {
        return """
                {"type":"object","additionalProperties":false,"properties":{"productScope":{"type":"object","additionalProperties":false,"required":["type"],"properties":{"type":{"type":"string","enum":["SINGLE_PRODUCT","EXACT_PRODUCT_NAME_GROUP","PRODUCT_TYPE_GROUP","ALL"]},"productId":{"type":"integer","minimum":1},"productName":{"type":"string","minLength":1,"maxLength":100},"productType":{"type":"string","minLength":1,"maxLength":50}}},"orderNo":{"type":"string","minLength":1,"maxLength":50},"batchNo":{"type":"string","minLength":1,"maxLength":80},"dateRange":{"type":"object","additionalProperties":false,"required":["type"],"properties":{"type":{"type":"string","enum":["EXACT","LAST_DAYS","RANGE"]},"date":{"type":"string","format":"date"},"days":{"type":"integer","minimum":1,"maximum":366},"from":{"type":"string","format":"date"},"to":{"type":"string","format":"date"}}},"groupBy":{"type":"string","enum":["batch","order","product"],"default":"batch"},"limit":{"type":"integer","minimum":1,"maximum":100,"default":50}}}
                """;
    }

    private static String palletAnomaliesSchema() {
        return """
                {"type":"object","additionalProperties":false,"properties":{"productScope":{"type":"object","additionalProperties":false,"required":["type"],"properties":{"type":{"type":"string","enum":["SINGLE_PRODUCT","EXACT_PRODUCT_NAME_GROUP","PRODUCT_TYPE_GROUP","ALL"]},"productId":{"type":"integer","minimum":1},"productName":{"type":"string","minLength":1,"maxLength":100},"productType":{"type":"string","minLength":1,"maxLength":50}}},"warehouseId":{"type":"integer","minimum":1},"dateRange":{"type":"object","additionalProperties":false,"required":["type"],"properties":{"type":{"type":"string","enum":["EXACT","LAST_DAYS","RANGE"]},"date":{"type":"string","format":"date"},"days":{"type":"integer","minimum":1,"maximum":366},"from":{"type":"string","format":"date"},"to":{"type":"string","format":"date"}}},"anomalyTypes":{"type":"array","minItems":1,"maxItems":5,"items":{"type":"string","enum":["VOID_CODE_SCANNED","STATUS_INVENTORY_MISMATCH","DUPLICATE_INBOUND","OUTBOUND_WITHOUT_INBOUND","PRODUCT_BINDING_MISMATCH"]}},"limit":{"type":"integer","minimum":1,"maximum":100,"default":50}}}
                """;
    }

    private static String palletFlowRecordsSchema() {
        return """
                {"type":"object","additionalProperties":false,"properties":{"code":{"type":"string","minLength":1,"maxLength":100},"productScope":{"type":"object","additionalProperties":false,"required":["type"],"properties":{"type":{"type":"string","enum":["SINGLE_PRODUCT","EXACT_PRODUCT_NAME_GROUP","PRODUCT_TYPE_GROUP","ALL"]},"productId":{"type":"integer","minimum":1},"productName":{"type":"string","minLength":1,"maxLength":100},"productType":{"type":"string","minLength":1,"maxLength":50}}},"warehouseId":{"type":"integer","minimum":1},"dateRange":{"type":"object","additionalProperties":false,"required":["type"],"properties":{"type":{"type":"string","enum":["EXACT","LAST_DAYS","RANGE"]},"date":{"type":"string","format":"date"},"days":{"type":"integer","minimum":1,"maximum":366},"from":{"type":"string","format":"date"},"to":{"type":"string","format":"date"}}},"eventTypes":{"type":"array","maxItems":8,"items":{"type":"string","enum":["INBOUND","OUTBOUND","TRANSFER","BIND","ASSAY","CANCEL","LABEL"]}},"page":{"type":"integer","minimum":1,"default":1},"size":{"type":"integer","minimum":1,"maximum":100,"default":20}}}
                """;
    }

    private static String qrBatchInboundCompletionSchema() {
        return """
                {"type":"object","additionalProperties":false,"properties":{"batchNo":{"type":"string","minLength":1,"maxLength":80},"orderNo":{"type":"string","minLength":1,"maxLength":50},"productId":{"type":"integer","minimum":1},"dateRange":{"type":"object","additionalProperties":false,"required":["type"],"properties":{"type":{"type":"string","enum":["EXACT","LAST_DAYS","RANGE"]},"date":{"type":"string","format":"date"},"days":{"type":"integer","minimum":1,"maximum":366},"from":{"type":"string","format":"date"},"to":{"type":"string","format":"date"}}},"includeUnfinishedExamples":{"type":"boolean","default":true},"limit":{"type":"integer","minimum":1,"maximum":100,"default":20}}}
                """;
    }

    private static String resolveProductionEntitiesSchema() {
        return """
                {"type":"object","additionalProperties":false,"required":["entityType","query"],"properties":{"entityType":{"type":"string","enum":["PRODUCTION_ORDER","BOILING_BATCH"]},"query":{"type":"string","minLength":1,"maxLength":100},"limit":{"type":"integer","minimum":1,"maximum":10,"default":5}}}
                """;
    }

    private static String productionOrderProgressSchema() {
        return """
                {"type":"object","additionalProperties":false,"required":["orderRef"],"properties":{"orderRef":{"type":"string","minLength":1,"maxLength":500,"description":"Short-lived opaque orderRef returned by resolve_production_entities; never invent from an internal id."}}}
                """;
    }

    private static String registeredReportRunSchema() {
        return """
                {"type":"object","additionalProperties":false,"required":["reportDefinitionId","reportVersion","startDate","endDate"],"properties":{"reportDefinitionId":{"type":"string","enum":["daily_production_overview_v1","quality_assay_result_trend_v1","quality_metric_trend_v1","production_input_output_flow_v1","pallet_task_cycle_time_v1","inventory_level_trend_v1","today_operations_overview_v1"]},"reportVersion":{"type":"integer","const":1},"startDate":{"type":"string","format":"date"},"endDate":{"type":"string","format":"date"},"productQuery":{"type":"string","minLength":1,"maxLength":100,"description":"Optional user-visible product-name fragment. For production_input_output_flow_v1 it scopes orders through stable registered output products; for inventory_level_trend_v1 it scopes product-level inventory balances. today_operations_overview_v1 does not accept productQuery."},"metricKey":{"type":"string","enum":["color_value","reducing_sugar","dry_weight_loss","conductivity_ash","sucrose","insoluble_impurity","ph"],"description":"Required only for quality_metric_trend_v1; identifies one supported assay metric."},"taskType":{"type":"string","enum":["ALL","INBOUND","SEMI_IN","FINISH_IN","OUT","TRANSFER"],"description":"Optional controlled task-type filter used only for pallet_task_cycle_time_v1. INBOUND covers semi-finished and finished inbound tasks."},"comparisonMode":{"type":"string","enum":["PREVIOUS_PERIOD","CUSTOM"],"description":"Optional deterministic cross-period comparison. today_operations_overview_v1 does not support comparison."},"comparisonStartDate":{"type":"string","format":"date"},"comparisonEndDate":{"type":"string","format":"date"}}}
                """;
    }

    private static String boilingBatchListSchema() {
        return """
                {"type":"object","additionalProperties":false,"properties":{"productQuery":{"type":"string","minLength":1,"maxLength":100},"startDate":{"type":"string","format":"date"},"endDate":{"type":"string","format":"date"},"status":{"type":"string","enum":["AVAILABLE","USED_UP","CANCELED"]},"limit":{"type":"integer","minimum":1,"maximum":20,"default":10}}}
                """;
    }

    private static String boilingBatchTraceSchema() {
        return """
                {"type":"object","additionalProperties":false,"required":["batchRef"],"properties":{"batchRef":{"type":"string","minLength":1,"maxLength":500,"description":"Short-lived opaque batchRef returned by resolve_production_entities for BOILING_BATCH."}}}
                """;
    }

    private static String materialPickTraceSchema() {
        return """
                {"type":"object","additionalProperties":false,"required":["orderRef"],"properties":{"orderRef":{"type":"string","minLength":1,"maxLength":500,"description":"Short-lived opaque orderRef returned by resolve_production_entities."}}}
                """;
    }

    private static String productionLabelCompletionSchema() {
        return """
                {"type":"object","additionalProperties":false,"required":["orderRef"],"properties":{"orderRef":{"type":"string","minLength":1,"maxLength":500,"description":"Short-lived opaque orderRef returned by resolve_production_entities."}}}
                """;
    }

    private static String productionInProcessMaterialsSchema() {
        return """
                {"type":"object","additionalProperties":false,"properties":{"productName":{"type":"string","minLength":1,"maxLength":100},"productType":{"type":"string","minLength":1,"maxLength":50},"productionDateStart":{"type":"string","format":"date"},"productionDateEnd":{"type":"string","format":"date"},"page":{"type":"integer","minimum":1},"size":{"type":"integer","minimum":1,"maximum":50}}}
                """;
    }

    private static String productionMaterialCandidatesSchema() {
        return """
                {"type":"object","additionalProperties":false,"required":["orderRef"],"properties":{"orderRef":{"type":"string","minLength":1,"maxLength":500},"page":{"type":"integer","minimum":1},"size":{"type":"integer","minimum":1,"maximum":50}}}
                """;
    }

    private static String palletTasksSchema() {
        return """
                {"type":"object","additionalProperties":false,"properties":{"code":{"type":"string","minLength":1,"maxLength":100},"taskType":{"type":"string","enum":["IN","SEMI_IN","FINISH_IN","OUT","TRANSFER"]},"bizScene":{"type":"string","enum":["DIRECT_OUT","PREPARE_CONSUMED","FINISH_OUT"]},"status":{"type":"string","enum":["PENDING","CONFIRMED","CANCELED"]},"productName":{"type":"string","minLength":1,"maxLength":100},"productType":{"type":"string","minLength":1,"maxLength":50},"productStatus":{"type":"string","enum":["半成品","成品"]},"targetWarehouseName":{"type":"string","minLength":1,"maxLength":100},"productionDateStart":{"type":"string","format":"date"},"productionDateEnd":{"type":"string","format":"date"},"page":{"type":"integer","minimum":1},"size":{"type":"integer","minimum":1,"maximum":50}}}
                """;
    }

    private static String taskTransitionPreviewSchema() {
        return """
                {"type":"object","additionalProperties":false,"required":["previewVersion","transition","palletCodes"],"properties":{"previewVersion":{"type":"integer","const":1},"transition":{"type":"string","enum":["CONFIRM_FINISH_INBOUND","CONFIRM_FINISH_OUTBOUND"]},"palletCodes":{"type":"array","minItems":1,"maxItems":20,"uniqueItems":true,"items":{"type":"string","minLength":1,"maxLength":100,"pattern":"^[A-Za-z0-9-]+$"}}}}
                """;
    }

    private static String stockDocumentsSchema() {
        return """
                {"type":"object","additionalProperties":false,"required":["documentType"],"properties":{"documentType":{"type":"string","enum":["INBOUND","OUTBOUND","SEMI_PRODUCT"]},"productName":{"type":"string","minLength":1,"maxLength":100},"warehouseName":{"type":"string","minLength":1,"maxLength":100},"operatorName":{"type":"string","minLength":1,"maxLength":100},"startDate":{"type":"string","format":"date"},"endDate":{"type":"string","format":"date"},"page":{"type":"integer","minimum":1},"size":{"type":"integer","minimum":1,"maximum":50}}}
                """;
    }

    private static String autoInboundBatchesSchema() {
        return """
                {"type":"object","additionalProperties":false,"properties":{"status":{"type":"string","minLength":1,"maxLength":50},"limit":{"type":"integer","minimum":1,"maximum":20}}}
                """;
    }

    private static String autoInboundBatchDetailSchema() {
        return """
                {"type":"object","additionalProperties":false,"required":["batchRef"],"properties":{"batchRef":{"type":"string","minLength":48,"maxLength":100,"pattern":"^aibr_[A-Za-z0-9_-]+$","description":"Opaque batchRef returned by query_auto_inbound_batches; never invent from an internal batch id."}}}
                """;
    }

    private static String warehouseCapacityDistributionSchema() {
        return """
                {"type":"object","additionalProperties":false,"properties":{"warehouseScope":{"type":"object","additionalProperties":false,"required":["type"],"properties":{"type":{"type":"string","enum":["ALL","SINGLE_WAREHOUSE"]},"warehouseId":{"type":"integer","minimum":1}}},"occupancyBand":{"type":"string","enum":["ANY","EMPTY","LOW","MEDIUM","HIGH","FULL"],"default":"ANY"},"onlyAvailable":{"type":"boolean","default":false},"page":{"type":"integer","minimum":1,"default":1},"size":{"type":"integer","minimum":1,"maximum":50,"default":20}}}
                """;
    }

    private static String warehouseRecentOperationsSchema() {
        return """
                {"type":"object","additionalProperties":false,"properties":{"warehouseId":{"type":"integer","minimum":1},"from":{"type":"string","format":"date-time"},"to":{"type":"string","format":"date-time"},"eventTypes":{"type":"array","maxItems":3,"uniqueItems":true,"items":{"type":"string","enum":["INBOUND","OUTBOUND","TRANSFER"]}},"limit":{"type":"integer","minimum":1,"maximum":50,"default":20}}}
                """;
    }

    private static String warehouseMixedStorageFactsSchema() {
        return """
                {"type":"object","additionalProperties":false,"properties":{"warehouseId":{"type":"integer","minimum":1},"factType":{"type":"string","enum":["ANY","MULTIPLE_PRODUCTS","MULTIPLE_SPECIFICATIONS"],"default":"ANY"},"limit":{"type":"integer","minimum":1,"maximum":50,"default":20}}}
                """;
    }

    private static String productCatalogSchema() {
        return """
                {"type":"object","additionalProperties":false,"properties":{"productName":{"type":"string","minLength":1,"maxLength":100},"productType":{"type":"string","minLength":1,"maxLength":50},"productStatus":{"type":"string","enum":["半成品","成品"]},"packagingMethod":{"type":"string","minLength":1,"maxLength":50},"screenMeshName":{"type":"string","minLength":1,"maxLength":100},"page":{"type":"integer","minimum":1,"default":1},"size":{"type":"integer","minimum":1,"maximum":50,"default":20}}}
                """;
    }
    private static String productDetailSchema() {
        return """
                {"type":"object","additionalProperties":false,"required":["productName"],"properties":{"productName":{"type":"string","minLength":1,"maxLength":100,"description":"Exact product name from the catalog; ambiguous or partial names are rejected."}}}
                """;
    }
    private static String screenMeshCatalogSchema() {
        return """
                {"type":"object","additionalProperties":false,"properties":{"meshName":{"type":"string","minLength":1,"maxLength":100},"page":{"type":"integer","minimum":1,"default":1},"size":{"type":"integer","minimum":1,"maximum":50,"default":20}}}
                """;
    }
    private static String assayGroupsSchema() { return """
            {"type":"object","additionalProperties":false,"properties":{"groupName":{"type":"string","minLength":1,"maxLength":100},"page":{"type":"integer","minimum":1,"default":1},"size":{"type":"integer","minimum":1,"maximum":50,"default":20}}}
            """; }
    private static String qualityStandardCatalogSchema() { return """
            {"type":"object","additionalProperties":false,"properties":{"productType":{"type":"string","minLength":1,"maxLength":50},"standardName":{"type":"string","minLength":1,"maxLength":100},"status":{"type":"string","enum":["ENABLED","DISABLED"]},"page":{"type":"integer","minimum":1,"default":1},"size":{"type":"integer","minimum":1,"maximum":50,"default":20}}}
            """; }
    private static String qualityStandardDetailSchema() { return """
            {"type":"object","additionalProperties":false,"required":["standardCode","version"],"properties":{"standardCode":{"type":"string","minLength":1,"maxLength":100},"version":{"type":"integer","minimum":1}}}
            """; }
    private static String productStandardRelationsSchema() { return """
            {"type":"object","additionalProperties":false,"required":["productName"],"properties":{"productName":{"type":"string","minLength":1,"maxLength":100}}}
            """; }
    private static String productQualityConfigurationSchema() { return """
            {"type":"object","additionalProperties":false,"required":["productId"],"properties":{"productId":{"type":"integer","minimum":1,"description":"Unique product id returned by resolve_products; never guess."}}}
            """; }
    private static String employeeRosterSchema() { return """
            {"type":"object","additionalProperties":false,"properties":{"employeeId":{"type":"string","minLength":1,"maxLength":50},"name":{"type":"string","minLength":1,"maxLength":100},"department":{"type":"string","minLength":1,"maxLength":100},"position":{"type":"string","minLength":1,"maxLength":100},"status":{"type":"string","minLength":1,"maxLength":20},"roleCode":{"type":"string","minLength":1,"maxLength":50},"page":{"type":"integer","minimum":1,"default":1},"size":{"type":"integer","minimum":1,"maximum":50,"default":20}}}
            """; }
    private static String roleCatalogSchema() { return """
            {"type":"object","additionalProperties":false,"properties":{"keyword":{"type":"string","minLength":1,"maxLength":100},"status":{"type":"string","enum":["ENABLED","DISABLED"]},"page":{"type":"integer","minimum":1,"default":1},"size":{"type":"integer","minimum":1,"maximum":50,"default":20}}}
            """; }
    private static String rolePermissionSummarySchema() { return """
            {"type":"object","additionalProperties":false,"required":["roleCodeOrName"],"properties":{"roleCodeOrName":{"type":"string","minLength":1,"maxLength":100}}}
            """; }
    private static String operationLogAuditSchema() { return """
            {"type":"object","additionalProperties":false,"properties":{"module":{"type":"string","minLength":1,"maxLength":100},"operationType":{"type":"string","minLength":1,"maxLength":20},"operator":{"type":"string","minLength":1,"maxLength":100},"startTime":{"type":"string","format":"date-time"},"endTime":{"type":"string","format":"date-time"},"page":{"type":"integer","minimum":1,"default":1},"size":{"type":"integer","minimum":1,"maximum":50,"default":20}}}
            """; }
    private static String agentToolAuditSchema() { return """
            {"type":"object","additionalProperties":false,"properties":{"capability":{"type":"string","minLength":1,"maxLength":100},"resultCode":{"type":"string","minLength":1,"maxLength":40},"errorCode":{"type":"string","minLength":1,"maxLength":80},"startTime":{"type":"string","format":"date-time"},"endTime":{"type":"string","format":"date-time"},"page":{"type":"integer","minimum":1,"default":1},"size":{"type":"integer","minimum":1,"maximum":50,"default":20}}}
            """; }
    private static String agentAnswerReviewSchema() { return """
            {"type":"object","additionalProperties":false,"properties":{"reviewStatus":{"type":"string","minLength":1,"maxLength":40},"answerStatus":{"type":"string","minLength":1,"maxLength":40},"failureDomain":{"type":"string","minLength":1,"maxLength":80},"failureCategory":{"type":"string","minLength":1,"maxLength":120},"suggestedFixType":{"type":"string","minLength":1,"maxLength":80},"testCaseStatus":{"type":"string","minLength":1,"maxLength":40},"priorityOnly":{"type":"boolean"},"page":{"type":"integer","minimum":1,"default":1},"size":{"type":"integer","minimum":1,"maximum":50,"default":20}}}
            """; }
    private static String inventoryLedgerSchema() { return """
            {"type":"object","additionalProperties":false,"properties":{"productName":{"type":"string","minLength":1,"maxLength":100},"warehouseName":{"type":"string","minLength":1,"maxLength":100},"screenMeshName":{"type":"string","minLength":1,"maxLength":100},"productStatus":{"type":"string","minLength":1,"maxLength":50},"entryDateStart":{"type":"string","format":"date"},"entryDateEnd":{"type":"string","format":"date"},"page":{"type":"integer","minimum":1,"default":1},"size":{"type":"integer","minimum":1,"maximum":50,"default":20}}}
            """; }
    private static String preparePoolBalanceSchema() { return """
            {"type":"object","additionalProperties":false,"properties":{"productName":{"type":"string","minLength":1,"maxLength":100},"productType":{"type":"string","minLength":1,"maxLength":50},"screenMeshName":{"type":"string","minLength":1,"maxLength":100},"productionDateStart":{"type":"string","format":"date"},"productionDateEnd":{"type":"string","format":"date"},"positiveOnly":{"type":"boolean","const":true,"default":true},"page":{"type":"integer","minimum":1,"default":1},"size":{"type":"integer","minimum":1,"maximum":50,"default":20}}}
            """; }
    private static String fixedProductQrPoolSchema() { return """
            {"type":"object","additionalProperties":false,"properties":{"productName":{"type":"string","minLength":1,"maxLength":100},"codes":{"type":"array","maxItems":20,"uniqueItems":true,"items":{"type":"string","minLength":1,"maxLength":100}},"status":{"type":"string","minLength":1,"maxLength":30},"freeOnly":{"type":"boolean"},"page":{"type":"integer","minimum":1,"default":1},"size":{"type":"integer","minimum":1,"maximum":50,"default":20}}}
            """; }

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
