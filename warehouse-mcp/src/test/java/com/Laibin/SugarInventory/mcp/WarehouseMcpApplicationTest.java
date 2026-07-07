package com.Laibin.SugarInventory.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

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
    void startsAndListsSevenTools() {
        List<String> names = toolSchemas().keySet().stream().sorted().toList();

        assertThat(names).containsExactly(
                "get_assay_status",
                "get_inventory_distribution",
                "get_inventory_overview",
                "get_pallet_status",
                "get_warehouse_status",
                "resolve_products",
                "resolve_warehouses"
        );
    }

    @Test
    void exposesTightInputSchemas() {
        Map<String, JsonNode> schemas = toolSchemas();

        assertObjectClosed(schemas.get("resolve_products"));
        assertObjectClosed(schemas.get("resolve_warehouses"));
        assertObjectClosed(schemas.get("get_inventory_overview"));
        assertObjectClosed(schemas.get("get_inventory_distribution"));
        assertObjectClosed(schemas.get("get_warehouse_status"));
        assertObjectClosed(schemas.get("get_pallet_status"));
        assertObjectClosed(schemas.get("get_assay_status"));

        assertRequired(schemas.get("resolve_products"), "query");
        assertRequired(schemas.get("resolve_warehouses"), "query");
        assertRequired(schemas.get("get_pallet_status"), "code");
        assertNoRequiredFields(schemas.get("get_inventory_overview"));
        assertRequired(schemas.get("get_inventory_distribution"), "productScope");
        assertRequired(schemas.get("get_inventory_distribution"), "warehouseScope");
        assertRequired(schemas.get("get_inventory_distribution"), "groupBy");
        assertNoRequiredFields(schemas.get("get_warehouse_status"));
        assertNoRequiredFields(schemas.get("get_assay_status"));

        assertStringBounds(schemas.get("resolve_products"), "query", 1, 100);
        assertIntegerBounds(schemas.get("resolve_products"), "limit", 1, 100);
        assertStringBounds(schemas.get("resolve_warehouses"), "query", 1, 100);
        assertIntegerBounds(schemas.get("resolve_warehouses"), "limit", 1, 100);
        assertIntegerBounds(schemas.get("get_inventory_overview"), "page", 1, null);
        assertIntegerBounds(schemas.get("get_inventory_overview"), "size", 1, 100);
        assertIntegerBounds(schemas.get("get_inventory_distribution"), "limit", 1, 100);
        assertIntegerBounds(schemas.get("get_warehouse_status"), "page", 1, null);
        assertIntegerBounds(schemas.get("get_warehouse_status"), "size", 1, 100);
        assertStringBounds(schemas.get("get_pallet_status"), "code", 1, 100);
        assertIntegerBounds(schemas.get("get_pallet_status"), "cycleNo", 1, null);
        assertIntegerBounds(schemas.get("get_pallet_status"), "flowLimit", 1, 100);
        assertIntegerBounds(schemas.get("get_assay_status"), "assayId", 1, null);
        assertIntegerBounds(schemas.get("get_assay_status"), "productId", 1, null);
        assertStringBounds(schemas.get("get_assay_status"), "productionDate", 10, 10);
        assertStringBounds(schemas.get("get_assay_status"), "productQuery", 1, 100);
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
