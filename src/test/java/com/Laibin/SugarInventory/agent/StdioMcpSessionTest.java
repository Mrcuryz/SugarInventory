package com.Laibin.SugarInventory.agent;

import com.Laibin.SugarInventory.agent.mcp.StdioMcpSession;
import com.Laibin.SugarInventory.agent.mcp.McpToolCall;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StdioMcpSessionTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void extractsStructuredContentBeforeTextContent() throws Exception {
        ObjectNode structured = objectMapper.createObjectNode();
        structured.put("scopeLabel", "8号库位的全部产品");
        structured.put("groupBy", "product");
        structured.putArray("groups").addObject().put("groupLabel", "黄冰糖（袋）");

        ObjectNode result = objectMapper.createObjectNode();
        result.set("structuredContent", structured);
        result.putArray("content").addObject()
                .put("type", "text")
                .put("text", "human readable fallback");

        JsonNode extracted = extract(result);

        assertThat(extracted.path("scopeLabel").asText()).isEqualTo("8号库位的全部产品");
        assertThat(extracted.path("groups")).hasSize(1);
    }

    @Test
    void fallsBackToJsonTextContentWhenStructuredContentIsAbsent() throws Exception {
        ObjectNode result = objectMapper.createObjectNode();
        result.putArray("content").addObject()
                .put("type", "text")
                .put("text", "{\"resolutionStatus\":\"UNIQUE\"}");

        JsonNode extracted = extract(result);

        assertThat(extracted.path("resolutionStatus").asText()).isEqualTo("UNIQUE");
    }

    @Test
    void treatsMcpIsErrorResultAsToolError() throws Exception {
        String responses = """
                {"jsonrpc":"2.0","id":1,"result":{}}
                {"jsonrpc":"2.0","id":2,"result":{"content":[{"type":"text","text":"Conversion from JSON to ProductScope failed"}],"isError":true}}
                """;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Process process = mock(Process.class);
        when(process.getInputStream()).thenReturn(new ByteArrayInputStream(responses.getBytes(StandardCharsets.UTF_8)));
        when(process.getOutputStream()).thenReturn(output);
        StdioMcpSession session = new StdioMcpSession("agt_test", process, objectMapper);

        var result = session.callTool(new McpToolCall("get_inventory_distribution", Map.of()));

        assertThat(result.success()).isFalse();
        assertThat(result.errorCode()).isEqualTo("MCP_TOOL_ERROR");
        assertThat(result.result().path("code").asText()).isEqualTo("MCP_TOOL_ERROR");
        assertThat(result.result().path("isError").asBoolean()).isTrue();
        assertThat(result.result().path("message").asText()).contains("Conversion from JSON");
    }

    private JsonNode extract(JsonNode result) throws Exception {
        Process process = mock(Process.class);
        when(process.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
        when(process.getOutputStream()).thenReturn(new ByteArrayOutputStream());
        StdioMcpSession session = new StdioMcpSession("agt_test", process, objectMapper);
        Method method = StdioMcpSession.class.getDeclaredMethod("extractToolResult", JsonNode.class);
        method.setAccessible(true);
        return (JsonNode) method.invoke(session, result);
    }
}
