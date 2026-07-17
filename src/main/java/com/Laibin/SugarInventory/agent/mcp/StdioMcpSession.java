package com.Laibin.SugarInventory.agent.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class StdioMcpSession implements McpSession {
    private static final Logger log = LoggerFactory.getLogger(StdioMcpSession.class);

    private final String agentSessionId;
    private final Process process;
    private final ObjectMapper objectMapper;
    private final BufferedReader reader;
    private final BufferedWriter writer;
    private final AtomicLong ids = new AtomicLong(1);
    private final Object ioLock = new Object();
    private volatile boolean initialized;

    public StdioMcpSession(String agentSessionId, Process process, ObjectMapper objectMapper) {
        this.agentSessionId = agentSessionId;
        this.process = process;
        this.objectMapper = objectMapper;
        this.reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
        this.writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
    }

    @Override
    public String agentSessionId() {
        return agentSessionId;
    }

    @Override
    public McpToolResult callTool(McpToolCall call) {
        long start = System.nanoTime();
        try {
            ensureInitialized();
            ObjectNode params = objectMapper.createObjectNode();
            params.put("name", call.toolName());
            params.set("arguments", objectMapper.valueToTree(call.arguments() == null ? Map.of() : call.arguments()));
            JsonNode response = request("tools/call", params);
            JsonNode error = response.path("error");
            if (!error.isMissingNode() && !error.isNull()) {
                return new McpToolResult(call.toolName(), error, "ERROR", "MCP_TOOL_ERROR", elapsedMs(start));
            }
            JsonNode result = response.path("result");
            if (result.path("isError").asBoolean(false)) {
                return new McpToolResult(call.toolName(), extractToolError(result), "ERROR", "MCP_TOOL_ERROR", elapsedMs(start));
            }
            return new McpToolResult(call.toolName(), extractToolResult(result), "SUCCESS", null, elapsedMs(start));
        } catch (RuntimeException | IOException e) {
            log.warn("MCP tool call failed; session={}, tool={}, errorType={}, message={}",
                    safeLogValue(agentSessionId), safeLogValue(call.toolName()),
                    e.getClass().getSimpleName(), safeLogValue(e.getMessage()));
            return new McpToolResult(call.toolName(), objectMapper.createObjectNode().put("message", "MCP tool call failed."), "ERROR", "MCP_CALL_FAILED", elapsedMs(start));
        }
    }

    @Override
    public Set<String> listTools() {
        try {
            ensureInitialized();
            JsonNode response = request("tools/list", objectMapper.createObjectNode());
            if (response.hasNonNull("error")) {
                throw new IllegalStateException("MCP tool registry request failed.");
            }
            Set<String> names = new HashSet<>();
            for (JsonNode tool : response.path("result").path("tools")) {
                String name = tool.path("name").asText(null);
                if (name != null && !name.isBlank()) {
                    names.add(name);
                }
            }
            return Set.copyOf(names);
        } catch (IOException e) {
            throw new IllegalStateException("MCP tool registry request failed.", e);
        }
    }

    @Override
    public void close() {
        process.destroy();
        try {
            if (!process.waitFor(2, TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
    }

    private void ensureInitialized() throws IOException {
        if (initialized) {
            return;
        }
        synchronized (ioLock) {
            if (initialized) {
                return;
            }
            ObjectNode params = objectMapper.createObjectNode();
            params.put("protocolVersion", "2024-11-05");
            params.set("capabilities", objectMapper.createObjectNode());
            ObjectNode clientInfo = objectMapper.createObjectNode();
            clientInfo.put("name", "warehouse-agent-gateway");
            clientInfo.put("version", "0.1.0");
            params.set("clientInfo", clientInfo);
            request("initialize", params);
            notifyInitialized();
            initialized = true;
        }
    }

    private void notifyInitialized() throws IOException {
        ObjectNode message = objectMapper.createObjectNode();
        message.put("jsonrpc", "2.0");
        message.put("method", "notifications/initialized");
        writeMessage(message);
    }

    private JsonNode request(String method, JsonNode params) throws IOException {
        synchronized (ioLock) {
            long id = ids.getAndIncrement();
            ObjectNode message = objectMapper.createObjectNode();
            message.put("jsonrpc", "2.0");
            message.put("id", id);
            message.put("method", method);
            message.set("params", params);
            writeMessage(message);
            return readResponse(id);
        }
    }

    private void writeMessage(JsonNode message) throws IOException {
        writer.write(objectMapper.writeValueAsString(message));
        writer.newLine();
        writer.flush();
    }

    private JsonNode readResponse(long expectedId) throws IOException {
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.isBlank()) {
                continue;
            }
            JsonNode node = objectMapper.readTree(line);
            if (node.has("id") && node.path("id").asLong() == expectedId) {
                return node;
            }
        }
        throw new IOException("MCP process closed before response.");
    }

    private JsonNode extractToolResult(JsonNode result) {
        JsonNode structuredContent = result.path("structuredContent");
        if (structuredContent.isObject() && !structuredContent.isEmpty()) {
            return structuredContent;
        }
        JsonNode content = result.path("content");
        if (content.isArray() && !content.isEmpty()) {
            String text = content.get(0).path("text").asText(null);
            if (text != null && !text.isBlank()) {
                try {
                    return objectMapper.readTree(text);
                } catch (IOException ignored) {
                    return objectMapper.createObjectNode().put("text", text);
                }
            }
        }
        return result;
    }

    private JsonNode extractToolError(JsonNode result) {
        ObjectNode error = objectMapper.createObjectNode();
        error.put("code", "MCP_TOOL_ERROR");
        error.put("message", safeLogValue(extractFirstText(result)));
        error.put("isError", true);
        return error;
    }

    private String extractFirstText(JsonNode result) {
        JsonNode content = result.path("content");
        if (content.isArray() && !content.isEmpty()) {
            String text = content.get(0).path("text").asText(null);
            if (text != null && !text.isBlank()) {
                return text;
            }
        }
        return "MCP tool returned an error.";
    }

    private long elapsedMs(long start) {
        return (System.nanoTime() - start) / 1_000_000L;
    }

    private static String safeLogValue(String value) {
        if (value == null) {
            return null;
        }
        String safe = value
                .replaceAll("(?i)authorization\\s*[:=]\\s*bearer\\s+[^\\s,;]+", "Authorization: <redacted>")
                .replaceAll("(?i)bearer\\s+[^\\s,;]+", "Bearer <redacted>")
                .replaceAll("(?i)(delegation[_-]?token|refresh[_-]?token|token|api[_-]?key|password|secret)\\s*[:=]\\s*[^\\s,;]+", "$1=<redacted>")
                .replaceAll("(?i)jdbc:[^\\s,;]+", "jdbc:<redacted>")
                .replaceAll("(?m)^\\s*at\\s+.+$", "<stack redacted>")
                .replaceAll("[\\r\\n\\t]+", " ");
        return safe.length() <= 200 ? safe : safe.substring(0, 200);
    }
}
