package com.Laibin.SugarInventory.mcp.tool;

import com.Laibin.SugarInventory.mcp.model.ToolModels.AssayEntryPreviewRequest;
import com.Laibin.SugarInventory.mcp.model.ToolModels.AssayEntryPreviewResponse;
import com.Laibin.SugarInventory.mcp.model.ToolModels.AssayGroupEntryPreviewRequest;
import com.Laibin.SugarInventory.mcp.service.ErrorMapper;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;

/** No-write preview, paired with the Agent goal and trusted-product binding. */
public final class AssayEntryPreviewToolCallback implements ToolCallback {
    public static final String NAME = "preview_assay_entry";
    public static final String SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["sampleDate"],"properties":{
              "productId":{"type":"integer","minimum":1,"description":"Exact product selected from current resolve_products evidence; never guess an ID."},
              "groupRef":{"type":"string","maxLength":500,"pattern":"^aer_[A-Za-z0-9_-]+[.][A-Za-z0-9_-]+$","description":"Signed group reference selected from current query_assay_groups evidence. Provide exactly one of productId or groupRef."},
              "sampleDate":{"type":"string","format":"date","description":"Explicit sampling date; do not infer it from a production date."},
              "colorValue":{"type":["number","null"]},"reducingSugar":{"type":["number","null"]},
              "dryWeight":{"type":["number","null"]},"conductivityAsh":{"type":["number","null"]},
              "sucrose":{"type":["number","null"]},"insolubleImpurity":{"type":["number","null"]},
              "phValue":{"type":["number","null"]}}}
            """;
    private final WarehouseTools tools;
    private final ObjectMapper mapper;
    private final ToolDefinition definition = DefaultToolDefinition.builder()
            .name("preview_assay_entry")
            .description("Preview one selected product or an entire selected assay group without saving or issuing confirmation credentials. Provide exactly one of productId or groupRef. Preserve exact measured values, zero and missing values. Backend standards determine each product's preliminary judgement.")
            .inputSchema(SCHEMA).build();

    public AssayEntryPreviewToolCallback(WarehouseTools tools, ObjectMapper mapper) {
        this.tools = tools;
        this.mapper = mapper.copy()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    }

    @Override
    public ToolDefinition getToolDefinition() { return definition; }

    @Override
    public String call(String input) {
        Object result;
        try {
            JsonNode root = mapper.readTree(input);
            if (root == null || !root.isObject() || !root.path("sampleDate").isTextual()
                    || root.has("productId") == root.has("groupRef")) {
                throw new IllegalArgumentException("Invalid identity fields");
            }
            if (root.has("productId") && (!root.path("productId").isIntegralNumber()
                    || !root.path("productId").canConvertToInt())) {
                throw new IllegalArgumentException("Invalid product identity");
            }
            if (root.has("groupRef") && (!root.path("groupRef").isTextual()
                    || root.path("groupRef").textValue().length() > 500
                    || !root.path("groupRef").textValue().matches("aer_[A-Za-z0-9_-]+[.][A-Za-z0-9_-]+"))) {
                throw new IllegalArgumentException("Invalid group reference");
            }
            for (String metric : new String[]{"colorValue", "reducingSugar", "dryWeight", "conductivityAsh",
                    "sucrose", "insolubleImpurity", "phValue"}) {
                JsonNode value = root.get(metric);
                if (value != null && !value.isNull() && !value.isNumber()) {
                    throw new IllegalArgumentException("Measurements must be numbers");
                }
            }
            result = root.has("groupRef")
                    ? tools.previewAssayGroupEntry(mapper.treeToValue(root, AssayGroupEntryPreviewRequest.class))
                    : tools.previewAssayEntry(mapper.treeToValue(root, AssayEntryPreviewRequest.class));
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            result = AssayEntryPreviewResponse.error(ErrorMapper.invalid("input",
                    "请选择一个具体产品或化验组，并核对采样日期和检测数值。"));
        }
        try {
            return mapper.writeValueAsString(result);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize assay entry preview result.");
        }
    }
}
