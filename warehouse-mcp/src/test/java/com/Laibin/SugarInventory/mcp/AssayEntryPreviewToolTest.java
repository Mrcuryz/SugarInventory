package com.Laibin.SugarInventory.mcp;

import com.Laibin.SugarInventory.mcp.client.WarehouseApiClient;
import com.Laibin.SugarInventory.mcp.config.WarehouseApiProperties;
import com.Laibin.SugarInventory.mcp.service.WarehouseReadService;
import com.Laibin.SugarInventory.mcp.tool.AssayEntryPreviewToolCallback;
import com.Laibin.SugarInventory.mcp.tool.WarehouseTools;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class AssayEntryPreviewToolTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private MockWebServer backend;
    private AssayEntryPreviewToolCallback callback;
    private static final String INPUT = "{\"productId\":84,\"sampleDate\":\"2026-09-23\",\"colorValue\":268.001,\"phValue\":0}";
    private static final String VALID_DATA = """
            {"previewOnly":true,"productId":84,"sampleDate":"2026-09-23","productName":"黄冰糖（袋）",
             "proposedVersion":2,"existingVersion":1,"judgementLabel":"无标准",
             "metrics":[{"field":"phValue","name":"pH值","value":0,"provided":true,"unit":null},
             {"field":"dryWeight","name":"干燥失重","value":null,"provided":false,"unit":null}],
             "failedMetrics":[],"warnings":["未保存"]}
            """;

    @BeforeEach
    void setup() throws Exception {
        backend = new MockWebServer();
        backend.start();
        var client = new WarehouseApiClient(new WarehouseApiProperties(
                backend.url("/").toString(), "test-token", Duration.ofSeconds(2)), mapper);
        callback = new AssayEntryPreviewToolCallback(new WarehouseTools(new WarehouseReadService(client)), mapper);
    }

    @AfterEach
    void close() throws Exception { backend.shutdown(); }

    private void enqueue(String data) {
        backend.enqueue(new MockResponse().addHeader("Content-Type", "application/json")
                .setBody("{\"code\":200,\"data\":" + data + "}"));
    }

    @Test
    void usesOnlyPreviewEndpointAndPreservesExactValuesAndNulls() throws Exception {
        enqueue(VALID_DATA);
        JsonNode result = mapper.readTree(callback.call(INPUT));
        var request = backend.takeRequest();
        assertThat(request.getPath()).isEqualTo("/api/assay/entry/preview");
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getHeader("X-Agent-Tool-Name")).isEqualTo("preview_assay_entry");
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer test-token");
        JsonNode body = mapper.readTree(request.getBody().readUtf8());
        assertThat(body.path("colorValue").decimalValue()).isEqualByComparingTo("268.001");
        assertThat(body.path("phValue").decimalValue()).isEqualByComparingTo("0");
        assertThat(body.path("dryWeight").isNull()).isTrue();
        assertThat(result.path("error").isNull()).isTrue();
        assertThat(result.path("previewOnly").asBoolean()).isTrue();
        assertThat(result.path("metrics").get(1).path("value").isNull()).isTrue();
        assertThat(result.path("judgementLabel").asText()).isEqualTo("无标准");
        assertThat(result.has("executionToken")).isFalse();
        assertThat(backend.getRequestCount()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "null", "[]", "{}", "{\"productId\":84.2,\"sampleDate\":\"2026-09-23\"}",
            "{\"productId\":\"84\",\"sampleDate\":\"2026-09-23\"}",
            "{\"productId\":0,\"sampleDate\":\"2026-09-23\"}",
            "{\"productId\":84,\"sampleDate\":\"2026-09-31\"}",
            "{\"productId\":84,\"sampleDate\":\"2026-09-23\",\"colorValue\":\"268\"}",
            "{\"productId\":84,\"sampleDate\":\"2026-09-23\",\"selectType\":2}",
            "{\"productId\":84,\"productId\":85,\"sampleDate\":\"2026-09-23\"}",
            "{\"productId\":84,\"sampleDate\":\"2026-09-23\"} {}"
    })
    void rejectsAmbiguousOrUnsupportedJsonWithoutCallingBackend(String input) throws Exception {
        JsonNode result = mapper.readTree(callback.call(input));
        assertThat(result.path("error").path("code").asText()).isEqualTo("INVALID_ARGUMENT");
        assertThat(backend.getRequestCount()).isZero();
    }

    @ParameterizedTest
    @ValueSource(ints = {1051, 1052, 1053})
    void businessRejectionIsNonRetryableAndDoesNotLeakRawBackendMessage(int code) throws Exception {
        backend.enqueue(new MockResponse().setBody("{\"code\":" + code
                + ",\"msg\":\"Bearer private-secret jdbc:private\",\"data\":null}"));
        String result = callback.call(INPUT);
        assertThat(mapper.readTree(result).path("error").path("code").asText()).startsWith("ASSAY_ENTRY_");
        assertThat(mapper.readTree(result).path("error").path("retryable").asBoolean()).isFalse();
        assertThat(result).doesNotContain("private-secret", "jdbc:private");
        assertThat(backend.getRequestCount()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "{}", "{\"previewOnly\":false,\"productId\":84,\"sampleDate\":\"2026-09-23\"}",
            "{\"previewOnly\":true,\"productId\":85,\"sampleDate\":\"2026-09-23\"}",
            "{\"previewOnly\":true,\"productId\":84,\"sampleDate\":\"2026-09-22\"}"})
    void refusesMismatchedOrMissingPreview(String data) throws Exception {
        enqueue(data);
        JsonNode result = mapper.readTree(callback.call(INPUT));
        assertThat(result.path("error").path("code").asText()).isEqualTo("UPSTREAM_INVALID_RESPONSE");
        assertThat(result.path("metrics").isEmpty()).isTrue();
    }

    @Test
    void permissionDenialDoesNotBecomeEmptyPreviewSuccess() throws Exception {
        backend.enqueue(new MockResponse().setResponseCode(403).setBody("denied"));
        JsonNode result = mapper.readTree(callback.call(INPUT));
        assertThat(result.path("error").path("code").asText()).isEqualTo("UPSTREAM_PERMISSION_DENIED");
        assertThat(result.path("proposedVersion").isNull()).isTrue();
    }

    @Test
    void schemaIsClosedAndAllowsOnlySignedGroupSelectionWithoutWriteInputs() throws Exception {
        JsonNode schema = mapper.readTree(callback.getToolDefinition().inputSchema());
        assertThat(schema.path("additionalProperties").asBoolean()).isFalse();
        assertThat(schema.path("properties").size()).isEqualTo(10);
        assertThat(schema.path("required").toString()).isEqualTo("[\"sampleDate\"]");
        assertThat(schema.path("properties").has("groupRef")).isTrue();
        assertThat(schema.toString()).doesNotContain("executionToken", "selectType", "relatedId", "groupId");
    }

    private static final String GROUP_REF = "aer_test-group.signature";
    private static final String GROUP_INPUT = "{\"groupRef\":\"" + GROUP_REF
            + "\",\"sampleDate\":\"2026-09-23\",\"colorValue\":268.001,\"phValue\":0}";

    private com.fasterxml.jackson.databind.node.ObjectNode groupData() throws Exception {
        var data = mapper.createObjectNode();
        data.put("previewOnly", true).put("confirmationAvailable", false).put("groupId", 9)
                .put("groupRef", GROUP_REF).put("groupName", "冰糖组").put("sampleDate", "2026-09-23").put("productCount", 2);
        var first = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(VALID_DATA);
        var second = first.deepCopy().put("productId", 85).put("productName", "白冰糖").put("proposedVersion", 4);
        data.putArray("products").add(first).add(second);
        data.putArray("warnings").add("未保存");
        return data;
    }

    @Test
    void selectedGroupUsesSameToolAndOnlyReadOnlyReferencePreview() throws Exception {
        enqueue(groupData().toString());
        JsonNode result = mapper.readTree(callback.call(GROUP_INPUT));
        var request = backend.takeRequest();
        assertThat(request.getPath()).isEqualTo("/api/assay/entry/group/from-reference/preview");
        assertThat(request.getHeader("X-Agent-Tool-Name")).isEqualTo("preview_assay_entry");
        var body = mapper.readTree(request.getBody().readUtf8());
        assertThat(body.path("groupRef").asText()).isEqualTo(GROUP_REF);
        assertThat(body.has("groupId")).isFalse();
        assertThat(body.path("values").path("productId").isNull()).isTrue();
        assertThat(body.path("values").path("colorValue").decimalValue()).isEqualByComparingTo("268.001");
        assertThat(body.path("values").path("phValue").decimalValue()).isEqualByComparingTo("0");
        assertThat(body.path("values").path("dryWeight").isNull()).isTrue();
        assertThat(result.path("error").isNull()).isTrue();
        assertThat(result.path("products").size()).isEqualTo(2);
        assertThat(result.path("products").get(1).path("proposedVersion").asInt()).isEqualTo(4);
        assertThat(result.path("confirmationAvailable").asBoolean()).isFalse();
        assertThat(result.toString()).doesNotContain("executionToken", "confirmationToken");
        assertThat(backend.getRequestCount()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"both", "rawId", "null", "numeric", "unknown", "badRef", "tooLong", "stringMetric"})
    void rejectsUntrustedGroupInputsWithoutBackendCall(String variant) throws Exception {
        var input = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(GROUP_INPUT);
        switch (variant) {
            case "both" -> input.put("productId", 84);
            case "rawId" -> { input.remove("groupRef"); input.put("groupId", 9); }
            case "null" -> input.putNull("groupRef");
            case "numeric" -> input.put("groupRef", 9);
            case "unknown" -> input.put("execute", true);
            case "badRef" -> input.put("groupRef", "冰糖组");
            case "tooLong" -> input.put("groupRef", "aer_" + "x".repeat(500) + ".signature");
            case "stringMetric" -> input.put("phValue", "0");
        }
        var result = mapper.readTree(callback.call(input.toString()));
        assertThat(result.path("error").path("code").asText()).isEqualTo("INVALID_ARGUMENT");
        assertThat(backend.getRequestCount()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"wrongRef", "wrongDate", "notPreview", "missing", "empty", "count", "duplicate", "productDate", "productWrite", "version", "productError"})
    void refusesPartialOrMismatchedGroupResults(String variant) throws Exception {
        var data = groupData();
        var second = (com.fasterxml.jackson.databind.node.ObjectNode) data.path("products").get(1);
        switch (variant) {
            case "wrongRef" -> data.put("groupRef", "aer_other.signature");
            case "wrongDate" -> data.put("sampleDate", "2026-09-22");
            case "notPreview" -> data.put("previewOnly", false);
            case "missing" -> data.remove("products");
            case "empty" -> { data.putArray("products"); data.put("productCount", 0); }
            case "count" -> data.put("productCount", 3);
            case "duplicate" -> second.put("productId", 84);
            case "productDate" -> second.put("sampleDate", "2026-09-22");
            case "productWrite" -> second.put("previewOnly", false);
            case "version" -> second.put("proposedVersion", 0);
            case "productError" -> second.putObject("error").put("code", "FAILED");
        }
        enqueue(data.toString());
        var result = mapper.readTree(callback.call(GROUP_INPUT));
        assertThat(result.path("error").path("code").asText()).isEqualTo("UPSTREAM_INVALID_RESPONSE");
        assertThat(result.path("products").isEmpty()).isTrue();
    }
}
