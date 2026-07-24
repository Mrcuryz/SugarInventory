package com.Laibin.SugarInventory.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@SpringBootTest(properties = {
        "warehouse.api.base-url=http://127.0.0.1:1",
        "warehouse.api.token=test-token",
        "logging.config=classpath:logback-spring.xml"
})
class WarehouseMcpApplicationTest {
    @Autowired
    private List<ToolCallbackProvider> providers;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void startsAndListsFiftyTwoTools() {
        List<String> names = toolSchemas().keySet().stream().sorted().toList();

        assertThat(names).containsExactly(
                "get_assay_report_detail",
                "get_assay_status",
                "get_auto_inbound_batch_detail",
                "get_inventory_distribution",
                "get_inventory_overview",
                "get_pallet_status",
                "get_product_detail",
                "get_quality_standard_detail",
                "get_role_permission_summary",
                "get_warehouse_status",
                "query_agent_answer_reviews",
                "query_agent_tool_audit",
                "query_assay_abnormalities",
                "query_assay_groups",
                "query_assay_records",
                "query_assay_standard_coverage",
                "query_auto_inbound_batches",
                "query_boiling_batch_trace",
                "query_boiling_batches",
                "query_employee_roster",
                "query_fixed_product_qr_pool",
                "query_in_process_materials",
                "query_inventory_by_assay_metrics",
                "query_inventory_by_quality_standard",
                "query_inventory_ledger",
                "query_material_candidates",
                "query_material_pick_trace",
                "query_pallet_anomalies",
                "query_pallet_flow_records",
                "query_pallet_tasks",
                "query_prepare_pool_balance",
                "query_printed_not_inbound_codes",
                "query_product_catalog",
                "query_product_quality_configuration",
                "query_product_standard_relations",
                "query_production_label_completion",
                "query_production_order_progress",
                "query_products_without_recent_assay",
                "query_qr_batch_inbound_completion",
                "query_qr_code_lifecycle",
                "query_quality_standard_catalog",
                "query_roles",
                "query_screen_mesh_catalog",
                "query_stock_documents",
                "query_unqualified_inventory",
                "query_warehouse_capacity_distribution",
                "query_warehouse_mixed_storage_facts",
                "query_warehouse_recent_operations",
                "resolve_production_entities",
                "resolve_products",
                "resolve_warehouses",
                "search_operation_logs"
        );
    }

    @Test
    void exposesTightInputSchemas() {
        Map<String, JsonNode> schemas = toolSchemas();

        assertObjectClosed(schemas.get("resolve_products"));
        assertObjectClosed(schemas.get("resolve_warehouses"));
        assertObjectClosed(schemas.get("get_inventory_overview"));
        assertObjectClosed(schemas.get("get_inventory_distribution"));
        assertObjectClosed(schemas.get("query_unqualified_inventory"));
        assertObjectClosed(schemas.get("query_inventory_by_quality_standard"));
        assertObjectClosed(schemas.get("query_inventory_by_assay_metrics"));
        assertObjectClosed(schemas.get("get_warehouse_status"));
        assertObjectClosed(schemas.get("get_pallet_status"));
        assertObjectClosed(schemas.get("get_assay_status"));
        assertObjectClosed(schemas.get("query_assay_abnormalities"));
        assertObjectClosed(schemas.get("get_assay_report_detail"));
        assertObjectClosed(schemas.get("query_assay_standard_coverage"));
        assertObjectClosed(schemas.get("query_products_without_recent_assay"));
        assertObjectClosed(schemas.get("query_assay_records"));
        assertObjectClosed(schemas.get("query_qr_code_lifecycle"));
        assertObjectClosed(schemas.get("query_printed_not_inbound_codes"));
        assertObjectClosed(schemas.get("query_pallet_anomalies"));
        assertObjectClosed(schemas.get("query_pallet_flow_records"));
        assertObjectClosed(schemas.get("query_qr_batch_inbound_completion"));
        assertObjectClosed(schemas.get("resolve_production_entities"));
        assertObjectClosed(schemas.get("query_production_order_progress"));
        assertObjectClosed(schemas.get("query_boiling_batches"));
        assertObjectClosed(schemas.get("query_boiling_batch_trace"));
        assertObjectClosed(schemas.get("query_material_pick_trace"));
        assertObjectClosed(schemas.get("query_production_label_completion"));
        assertObjectClosed(schemas.get("query_in_process_materials"));
        assertObjectClosed(schemas.get("query_material_candidates"));
        assertObjectClosed(schemas.get("query_pallet_tasks"));
        assertObjectClosed(schemas.get("query_stock_documents"));
        assertObjectClosed(schemas.get("query_auto_inbound_batches"));
        assertObjectClosed(schemas.get("get_auto_inbound_batch_detail"));
        assertObjectClosed(schemas.get("query_warehouse_capacity_distribution"));
        assertObjectClosed(schemas.get("query_warehouse_recent_operations"));
        assertObjectClosed(schemas.get("query_warehouse_mixed_storage_facts"));
        assertObjectClosed(schemas.get("query_product_catalog"));
        assertObjectClosed(schemas.get("get_product_detail"));
        assertObjectClosed(schemas.get("query_screen_mesh_catalog"));
        assertObjectClosed(schemas.get("query_assay_groups"));
        assertObjectClosed(schemas.get("query_quality_standard_catalog"));
        assertObjectClosed(schemas.get("get_quality_standard_detail"));
        assertObjectClosed(schemas.get("query_product_standard_relations"));
        assertObjectClosed(schemas.get("query_product_quality_configuration"));
        assertObjectClosed(schemas.get("query_employee_roster"));
        assertObjectClosed(schemas.get("query_roles"));
        assertObjectClosed(schemas.get("get_role_permission_summary"));
        assertObjectClosed(schemas.get("search_operation_logs"));
        assertObjectClosed(schemas.get("query_agent_tool_audit"));
        assertObjectClosed(schemas.get("query_agent_answer_reviews"));
        assertObjectClosed(schemas.get("query_inventory_ledger"));
        assertObjectClosed(schemas.get("query_prepare_pool_balance"));
        assertObjectClosed(schemas.get("query_fixed_product_qr_pool"));

        assertRequired(schemas.get("resolve_products"), "query");
        assertRequired(schemas.get("resolve_warehouses"), "query");
        assertRequired(schemas.get("get_pallet_status"), "code");
        assertNoRequiredFields(schemas.get("get_inventory_overview"));
        assertRequired(schemas.get("get_inventory_distribution"), "productScope");
        assertRequired(schemas.get("get_inventory_distribution"), "warehouseScope");
        assertRequired(schemas.get("get_inventory_distribution"), "groupBy");
        assertRequired(schemas.get("query_unqualified_inventory"), "productScope");
        assertRequired(schemas.get("query_unqualified_inventory"), "warehouseScope");
        assertRequired(schemas.get("query_inventory_by_quality_standard"), "standardCode");
        assertRequired(schemas.get("query_inventory_by_assay_metrics"), "metricCondition");
        assertRequired(schemas.get("query_assay_abnormalities"), "productScope");
        assertRequired(schemas.get("get_assay_report_detail"), "reportRef");
        assertRequired(schemas.get("query_assay_standard_coverage"), "productScope");
        assertRequired(schemas.get("query_products_without_recent_assay"), "productScope");
        assertRequired(schemas.get("query_products_without_recent_assay"), "warehouseScope");
        assertRequired(schemas.get("query_assay_records"), "productScope");
        assertRequired(schemas.get("query_qr_code_lifecycle"), "code");
        assertRequired(schemas.get("resolve_production_entities"), "entityType");
        assertRequired(schemas.get("resolve_production_entities"), "query");
        assertRequired(schemas.get("query_production_order_progress"), "orderRef");
        assertNoRequiredFields(schemas.get("query_boiling_batches"));
        assertRequired(schemas.get("query_boiling_batch_trace"), "batchRef");
        assertRequired(schemas.get("query_material_pick_trace"), "orderRef");
        assertRequired(schemas.get("query_material_candidates"), "orderRef");
        assertRequired(schemas.get("query_production_label_completion"), "orderRef");
        assertRequired(schemas.get("query_stock_documents"), "documentType");
        assertRequired(schemas.get("get_auto_inbound_batch_detail"), "batchRef");
        assertRequired(schemas.get("get_role_permission_summary"), "roleCodeOrName");
        assertNoRequiredFields(schemas.get("get_warehouse_status"));
        assertNoRequiredFields(schemas.get("get_assay_status"));

        assertStringBounds(schemas.get("resolve_products"), "query", 1, 100);
        assertIntegerBounds(schemas.get("resolve_products"), "limit", 1, 100);
        assertStringBounds(schemas.get("resolve_warehouses"), "query", 1, 100);
        assertIntegerBounds(schemas.get("resolve_warehouses"), "limit", 1, 100);
        assertIntegerBounds(schemas.get("get_inventory_overview"), "page", 1, null);
        assertIntegerBounds(schemas.get("get_inventory_overview"), "size", 1, 100);
        assertIntegerBounds(schemas.get("get_inventory_distribution"), "limit", 1, 100);
        assertIntegerBounds(schemas.get("query_unqualified_inventory"), "limit", 1, 100);
        assertIntegerBounds(schemas.get("query_inventory_by_quality_standard"), "limit", 1, 100);
        assertIntegerBounds(schemas.get("query_inventory_by_assay_metrics"), "limit", 1, 100);
        assertIntegerBounds(schemas.get("get_warehouse_status"), "page", 1, null);
        assertIntegerBounds(schemas.get("get_warehouse_status"), "size", 1, 100);
        assertStringBounds(schemas.get("get_pallet_status"), "code", 1, 100);
        assertIntegerBounds(schemas.get("get_pallet_status"), "cycleNo", 1, null);
        assertIntegerBounds(schemas.get("get_pallet_status"), "flowLimit", 1, 100);
        assertIntegerBounds(schemas.get("get_assay_status"), "assayId", 1, null);
        assertIntegerBounds(schemas.get("get_assay_status"), "productId", 1, null);
        assertStringBounds(schemas.get("get_assay_status"), "productionDate", 10, 10);
        assertStringBounds(schemas.get("get_assay_status"), "productQuery", 1, 100);
        assertIntegerBounds(schemas.get("query_assay_abnormalities"), "limit", 1, 100);
        assertStringBounds(schemas.get("get_assay_report_detail"), "reportRef", 1, 200);
        assertIntegerBounds(schemas.get("query_assay_standard_coverage"), "limit", 1, 100);
        assertIntegerBounds(schemas.get("query_products_without_recent_assay"), "limit", 1, 100);
        assertIntegerBounds(schemas.get("query_assay_records"), "page", 1, null);
        assertIntegerBounds(schemas.get("query_assay_records"), "size", 1, 100);
        assertIntegerBounds(schemas.get("query_qr_code_lifecycle"), "flowLimit", 1, 100);
        assertIntegerBounds(schemas.get("query_printed_not_inbound_codes"), "limit", 1, 100);
        assertIntegerBounds(schemas.get("query_pallet_anomalies"), "limit", 1, 100);
        assertIntegerBounds(schemas.get("query_pallet_flow_records"), "size", 1, 100);
        assertIntegerBounds(schemas.get("query_qr_batch_inbound_completion"), "limit", 1, 100);
        assertStringBounds(schemas.get("resolve_production_entities"), "query", 1, 100);
        assertIntegerBounds(schemas.get("resolve_production_entities"), "limit", 1, 10);
        assertStringBounds(schemas.get("query_production_order_progress"), "orderRef", 1, 500);
        assertStringBounds(schemas.get("query_boiling_batches"), "productQuery", 1, 100);
        assertIntegerBounds(schemas.get("query_boiling_batches"), "limit", 1, 20);
        assertStringBounds(schemas.get("query_boiling_batch_trace"), "batchRef", 1, 500);
        assertStringBounds(schemas.get("query_material_pick_trace"), "orderRef", 1, 500);
        assertStringBounds(schemas.get("query_production_label_completion"), "orderRef", 1, 500);
        assertStringBounds(schemas.get("query_in_process_materials"), "productName", 1, 100);
        assertIntegerBounds(schemas.get("query_in_process_materials"), "size", 1, 50);
        assertStringBounds(schemas.get("query_material_candidates"), "orderRef", 1, 500);
        assertIntegerBounds(schemas.get("query_material_candidates"), "size", 1, 50);
        assertStringBounds(schemas.get("query_pallet_tasks"), "code", 1, 100);
        assertIntegerBounds(schemas.get("query_pallet_tasks"), "size", 1, 50);
        assertIntegerBounds(schemas.get("query_stock_documents"), "size", 1, 50);
    }

    @Test
    void bindsAndInvokesAllFiftyTwoToolCallbacks() {
        Map<String, ToolCallback> callbacks = providers.stream()
                .flatMap(provider -> Arrays.stream(provider.getToolCallbacks()))
                .collect(Collectors.toMap(
                        callback -> callback.getToolDefinition().name(),
                        Function.identity(),
                        (left, right) -> left
                ));
        Map<String, String> inputs = minimalToolInputs();

        assertThat(inputs.keySet()).containsExactlyInAnyOrderElementsOf(callbacks.keySet());
        inputs.forEach((toolName, input) -> assertThatCode(() -> {
            String result = callbacks.get(toolName).call(input);
            assertThat(result).isNotBlank();
        }).as(toolName).doesNotThrowAnyException());
    }

    private static Map<String, String> minimalToolInputs() {
        String autoInboundRef = "aibr_" + "A".repeat(43);
        return Map.ofEntries(
                Map.entry("resolve_products", "{\"query\":\"验收产品\"}"),
                Map.entry("resolve_warehouses", "{\"query\":\"1号库位\"}"),
                Map.entry("get_inventory_overview", "{}"),
                Map.entry("get_inventory_distribution", "{\"productScope\":{\"type\":\"ALL\"},\"warehouseScope\":{\"type\":\"ALL\"},\"groupBy\":\"product\"}"),
                Map.entry("query_unqualified_inventory", "{\"productScope\":{\"type\":\"ALL\"},\"warehouseScope\":{\"type\":\"ALL\"}}"),
                Map.entry("query_inventory_by_quality_standard", "{\"productScope\":{\"type\":\"ALL\"},\"warehouseScope\":{\"type\":\"ALL\"},\"standardCode\":\"STD-ACCEPTANCE\"}"),
                Map.entry("query_inventory_by_assay_metrics", "{\"productScope\":{\"type\":\"ALL\"},\"warehouseScope\":{\"type\":\"ALL\"},\"metricCondition\":{\"metricCode\":\"sucrose\",\"operator\":\"GTE\",\"value\":99.7}}"),
                Map.entry("query_assay_records", "{\"productScope\":{\"type\":\"ALL\"}}"),
                Map.entry("get_assay_report_detail", "{\"reportRef\":\"report_ref\"}"),
                Map.entry("query_assay_abnormalities", "{\"productScope\":{\"type\":\"ALL\"}}"),
                Map.entry("query_products_without_recent_assay", "{\"productScope\":{\"type\":\"ALL\"},\"warehouseScope\":{\"type\":\"ALL\"}}"),
                Map.entry("query_assay_standard_coverage", "{\"productScope\":{\"type\":\"ALL\"}}"),
                Map.entry("query_qr_code_lifecycle", "{\"code\":\"QR-ACCEPTANCE\"}"),
                Map.entry("query_printed_not_inbound_codes", "{}"),
                Map.entry("query_pallet_anomalies", "{}"),
                Map.entry("query_pallet_flow_records", "{}"),
                Map.entry("query_qr_batch_inbound_completion", "{\"batchNo\":\"BATCH-ACCEPTANCE\"}"),
                Map.entry("resolve_production_entities", "{\"entityType\":\"PRODUCTION_ORDER\",\"query\":\"ORDER-ACCEPTANCE\"}"),
                Map.entry("query_production_order_progress", "{\"orderRef\":\"order_ref\"}"),
                Map.entry("query_boiling_batches", "{}"),
                Map.entry("query_boiling_batch_trace", "{\"batchRef\":\"batch_ref\"}"),
                Map.entry("query_material_pick_trace", "{\"orderRef\":\"order_ref\"}"),
                Map.entry("query_production_label_completion", "{\"orderRef\":\"order_ref\"}"),
                Map.entry("query_in_process_materials", "{}"),
                Map.entry("query_material_candidates", "{\"orderRef\":\"order_ref\"}"),
                Map.entry("query_pallet_tasks", "{}"),
                Map.entry("query_stock_documents", "{\"documentType\":\"INBOUND\"}"),
                Map.entry("query_auto_inbound_batches", "{}"),
                Map.entry("get_auto_inbound_batch_detail", "{\"batchRef\":\"" + autoInboundRef + "\"}"),
                Map.entry("query_warehouse_capacity_distribution", "{}"),
                Map.entry("query_warehouse_recent_operations", "{}"),
                Map.entry("query_warehouse_mixed_storage_facts", "{}"),
                Map.entry("query_product_catalog", "{}"),
                Map.entry("get_product_detail", "{\"productName\":\"验收产品\"}"),
                Map.entry("query_screen_mesh_catalog", "{}"),
                Map.entry("query_assay_groups", "{}"),
                Map.entry("query_quality_standard_catalog", "{}"),
                Map.entry("get_quality_standard_detail", "{\"standardCode\":\"STD-ACCEPTANCE\",\"version\":1}"),
                Map.entry("query_product_standard_relations", "{\"productName\":\"验收产品\"}"),
                Map.entry("query_product_quality_configuration", "{\"productId\":1}"),
                Map.entry("query_employee_roster", "{}"),
                Map.entry("query_roles", "{}"),
                Map.entry("get_role_permission_summary", "{\"roleCodeOrName\":\"ADMIN\"}"),
                Map.entry("search_operation_logs", "{}"),
                Map.entry("query_agent_tool_audit", "{}"),
                Map.entry("query_agent_answer_reviews", "{}"),
                Map.entry("query_inventory_ledger", "{}"),
                Map.entry("query_prepare_pool_balance", "{\"positiveOnly\":true}"),
                Map.entry("query_fixed_product_qr_pool", "{}"),
                Map.entry("get_warehouse_status", "{}"),
                Map.entry("get_pallet_status", "{\"code\":\"QR-ACCEPTANCE\"}"),
                Map.entry("get_assay_status", "{}")
        );
    }

    private Map<String, JsonNode> toolSchemas() {
        return providers.stream()
                .flatMap(provider -> Arrays.stream(provider.getToolCallbacks()))
                .collect(Collectors.toMap(
                        callback -> callback.getToolDefinition().name(),
                        callback -> readJson(callback.getToolDefinition().inputSchema()),
                        (left, right) -> left
                ));
    }

    private JsonNode readJson(String schema) {
        try {
            return objectMapper.readTree(schema);
        } catch (Exception e) {
            throw new AssertionError("Tool inputSchema is not valid JSON", e);
        }
    }

    private static void assertObjectClosed(JsonNode schema) {
        assertThat(schema.path("type").asText()).isEqualTo("object");
        assertThat(schema.has("additionalProperties")).isTrue();
        assertThat(schema.path("additionalProperties").asBoolean()).isFalse();
    }

    private static void assertRequired(JsonNode schema, String field) {
        assertThat(schema.path("required")).anyMatch(node -> node.asText().equals(field));
    }

    private static void assertNoRequiredFields(JsonNode schema) {
        assertThat(schema.has("required") && schema.path("required").size() > 0).isFalse();
    }

    private static void assertStringBounds(JsonNode schema, String field, int minLength, int maxLength) {
        JsonNode property = schema.path("properties").path(field);
        assertThat(property.path("type").asText()).isEqualTo("string");
        assertThat(property.path("minLength").asInt()).isEqualTo(minLength);
        assertThat(property.path("maxLength").asInt()).isEqualTo(maxLength);
    }

    private static void assertIntegerBounds(JsonNode schema, String field, Integer minimum, Integer maximum) {
        JsonNode property = schema.path("properties").path(field);
        assertThat(property.path("type").asText()).isIn("integer", "number");
        if (minimum != null) {
            assertThat(property.path("minimum").asInt()).isEqualTo(minimum);
        }
        if (maximum != null) {
            assertThat(property.path("maximum").asInt()).isEqualTo(maximum);
        }
    }
}
