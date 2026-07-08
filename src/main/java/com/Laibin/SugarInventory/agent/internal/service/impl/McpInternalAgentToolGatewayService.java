package com.Laibin.SugarInventory.agent.internal.service.impl;

import com.Laibin.SugarInventory.SpringSecurity.LoginUser;
import com.Laibin.SugarInventory.agent.dto.AgentToolAuditDTO;
import com.Laibin.SugarInventory.agent.internal.dto.InternalAgentToolClientDTO;
import com.Laibin.SugarInventory.agent.internal.dto.InternalAgentToolRequestDTO;
import com.Laibin.SugarInventory.agent.internal.service.InternalAgentToolGatewayService;
import com.Laibin.SugarInventory.agent.internal.vo.InternalAgentToolErrorVO;
import com.Laibin.SugarInventory.agent.internal.vo.InternalAgentToolResponseVO;
import com.Laibin.SugarInventory.agent.mcp.McpSession;
import com.Laibin.SugarInventory.agent.mcp.McpSessionManager;
import com.Laibin.SugarInventory.agent.mcp.McpToolCall;
import com.Laibin.SugarInventory.agent.mcp.McpToolResult;
import com.Laibin.SugarInventory.agent.security.AgentSessionAuthenticationException;
import com.Laibin.SugarInventory.agent.service.AgentSessionService;
import com.Laibin.SugarInventory.agent.service.InternalAgentSessionAccess;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class McpInternalAgentToolGatewayService implements InternalAgentToolGatewayService {
    public static final String SERVICE_KEY_HEADER = "X-Agent-Service-Key";

    private static final Logger log = LoggerFactory.getLogger(McpInternalAgentToolGatewayService.class);
    private static final Set<String> ALLOWED_TOOLS = Set.of(
            "resolve_products",
            "resolve_warehouses",
            "get_inventory_overview",
            "get_inventory_distribution",
            "get_warehouse_status",
            "get_pallet_status",
            "get_assay_status"
    );
    private static final Map<String, String> UPSTREAM_PATHS = Map.of(
            "resolve_products", "/api/products/{id}; /api/products/product",
            "resolve_warehouses", "/api/warehouse/{id}; /api/warehouse/query",
            "get_inventory_overview", "/api/products/{id}; /api/inventory/stock/page",
            "get_inventory_distribution", "/api/inventory/distribution",
            "get_warehouse_status", "/api/warehouse/{id}; /api/inventory/qualified-inventory/{id}/page; /api/inventory/warehouses/{id}/recent-operations",
            "get_pallet_status", "/api/pallet-codes/parse; /api/pallet-codes/{code}/inventory; /api/pallet-codes/{code}/assay; /api/pallet-codes/{code}/flows/cycles; /api/pallet-codes/{code}/flows",
            "get_assay_status", "/api/assay/{id}; /api/assay/by-product-date"
    );

    private final AgentSessionService agentSessionService;
    private final McpSessionManager mcpSessionManager;
    private final ObjectMapper objectMapper;
    private final String expectedServiceKey;
    private final int maxArgumentsBytes;

    public McpInternalAgentToolGatewayService(AgentSessionService agentSessionService,
                                              McpSessionManager mcpSessionManager,
                                              ObjectMapper objectMapper,
                                              @Value("${agent.internal-tool.service-key:}") String expectedServiceKey,
                                              @Value("${agent.internal-tool.max-arguments-bytes:16384}") int maxArgumentsBytes) {
        this.agentSessionService = agentSessionService;
        this.mcpSessionManager = mcpSessionManager;
        this.objectMapper = objectMapper;
        this.expectedServiceKey = expectedServiceKey == null ? "" : expectedServiceKey;
        this.maxArgumentsBytes = Math.max(1024, maxArgumentsBytes);
    }

    @Override
    public InternalAgentToolResponseVO invoke(String serviceKey, String toolName, InternalAgentToolRequestDTO request) {
        if (!authenticated(serviceKey)) {
            return error(toolName, toolCallId(request), "SERVICE_AUTHENTICATION_FAILED",
                    "内部 Agent 服务认证失败。", "ERROR", null, false,
                    List.of("检查内部服务鉴权配置。"), null, null);
        }
        if (!validToolName(toolName) || !ALLOWED_TOOLS.contains(toolName)) {
            return auditedError(toolName, request, null, "TOOL_NOT_ALLOWED",
                    "当前工具不允许被 Agent 调用。", "ERROR", "toolName", false,
                    List.of("仅调用已登记的只读仓储工具。"), null);
        }

        InternalAgentToolErrorVO validationError = validateRequest(request);
        if (validationError != null) {
            return auditedError(toolName, request, null, validationError.getCode(), validationError.getMessage(),
                    validationError.getSeverity(), validationError.getField(), validationError.isRetryable(),
                    validationError.getSuggestedActions(), validationError.getUpstreamStatus());
        }

        InternalAgentSessionAccess access;
        try {
            access = agentSessionService.requireActiveInternalToolSession(request.getAgentSessionId());
        } catch (AgentSessionAuthenticationException e) {
            return auditedError(toolName, request, null, e.getErrorCode(), safeSessionMessage(e.getErrorCode()),
                    "ERROR", "agentSessionId", false, List.of("重新创建 Agent 会话后重试。"), e.getStatus());
        } catch (RuntimeException e) {
            return auditedError(toolName, request, null, "AGENT_SESSION_INVALID",
                    "Agent 会话无效或已过期。", "ERROR", "agentSessionId", false,
                    List.of("重新创建 Agent 会话后重试。"), 401);
        }

        long startedAt = System.nanoTime();
        McpToolResult result;
        try {
            McpSession mcpSession = mcpSessionManager.bindSession(access.loginUser(), access.session());
            result = mcpSession.callTool(new McpToolCall(toolName, request.getArguments()));
        } catch (RuntimeException e) {
            long durationMs = elapsedMs(startedAt);
            return auditedError(toolName, request, access.loginUser(), "MCP_CALL_FAILED",
                    "只读仓储工具暂时不可用。", "ERROR", null, true,
                    List.of("稍后重试。"), null, durationMs);
        }

        long durationMs = elapsedMs(startedAt);
        JsonNode safeResult = sanitizeNode(result.result());
        InternalAgentToolErrorVO toolError = resolveToolError(result, safeResult);
        String resultCode = toolError == null ? "SUCCESS" : "ERROR";
        String auditRef = recordAudit(toolName, request, access.loginUser(), resultCode,
                toolError == null ? null : toolError.getCode(), durationMs, safeResult);
        if (auditRef == null) {
            return error(toolName, request.getToolCallId(), "AUDIT_UNAVAILABLE",
                    "工具调用审计暂时不可用，本次结果未返回。", "ERROR", null, true,
                    List.of("稍后重试或联系管理员。"), null, null);
        }
        if (toolError != null) {
            return InternalAgentToolResponseVO.error(toolName, request.getToolCallId(), toolError, auditRef);
        }
        return InternalAgentToolResponseVO.success(toolName, request.getToolCallId(), safeResult, auditRef);
    }

    public static Set<String> allowedTools() {
        return ALLOWED_TOOLS;
    }

    private InternalAgentToolErrorVO validateRequest(InternalAgentToolRequestDTO request) {
        if (request == null) {
            return toolError("INVALID_ARGUMENT", "请求体不能为空。", "request", false,
                    List.of("提供完整的内部工具调用请求。"), null);
        }
        if (blankOrTooLong(request.getAgentSessionId(), 64)) {
            return toolError("INVALID_ARGUMENT", "agentSessionId 不能为空且长度不能超过 64。",
                    "agentSessionId", false, List.of("提供有效的 Agent 会话标识。"), null);
        }
        if (blankOrTooLong(request.getToolCallId(), 100)) {
            return toolError("INVALID_ARGUMENT", "toolCallId 不能为空且长度不能超过 100。",
                    "toolCallId", false, List.of("为每次工具调用提供唯一标识。"), null);
        }
        if (tooLong(request.getMessageId(), 100)) {
            return toolError("INVALID_ARGUMENT", "messageId 长度不能超过 100。",
                    "messageId", false, List.of("使用当前助手消息 ID。"), null);
        }
        if (request.getArguments() == null) {
            return toolError("INVALID_ARGUMENT", "arguments 不能为空。", "arguments", false,
                    List.of("没有参数时传递空对象。"), null);
        }
        InternalAgentToolClientDTO client = request.getClient();
        if (client != null && (tooLong(client.getTraceId(), 100) || tooLong(client.getRequestId(), 100))) {
            return toolError("INVALID_ARGUMENT", "traceId 或 requestId 长度不能超过 100。", "client", false,
                    List.of("缩短调用链标识。"), null);
        }
        try {
            if (objectMapper.writeValueAsBytes(request.getArguments()).length > maxArgumentsBytes) {
                return toolError("ARGUMENTS_TOO_LARGE", "工具参数超过允许大小。", "arguments", false,
                        List.of("减少参数数量或分页查询。"), null);
            }
        } catch (JsonProcessingException e) {
            return toolError("INVALID_ARGUMENT", "工具参数无法解析。", "arguments", false,
                    List.of("仅传递可序列化的 JSON 参数。"), null);
        }
        return null;
    }

    private InternalAgentToolErrorVO resolveToolError(McpToolResult result, JsonNode safeResult) {
        if (!result.success()) {
            String code = result.errorCode() == null || result.errorCode().isBlank()
                    ? "MCP_TOOL_ERROR" : result.errorCode();
            return toolError(code, safeToolMessage(code), null, retryable(code),
                    suggestedActions(code), null);
        }
        JsonNode errorNode = safeResult == null ? null : safeResult.path("error");
        if (errorNode == null || errorNode.isMissingNode() || errorNode.isNull()) {
            return null;
        }
        String code = text(errorNode, "code", "MCP_TOOL_ERROR");
        return new InternalAgentToolErrorVO(
                code,
                safeToolMessage(code),
                text(errorNode, "severity", "ERROR"),
                nullableText(errorNode, "field"),
                errorNode.path("retryable").asBoolean(retryable(code)),
                stringList(errorNode.path("suggestedActions"), suggestedActions(code)),
                errorNode.path("upstreamStatus").canConvertToInt() ? errorNode.path("upstreamStatus").asInt() : null
        );
    }

    private InternalAgentToolResponseVO auditedError(String toolName, InternalAgentToolRequestDTO request,
                                                     LoginUser loginUser, String code, String message,
                                                     String severity, String field, boolean retryable,
                                                     List<String> actions, Integer upstreamStatus) {
        return auditedError(toolName, request, loginUser, code, message, severity, field, retryable,
                actions, upstreamStatus, 0L);
    }

    private InternalAgentToolResponseVO auditedError(String toolName, InternalAgentToolRequestDTO request,
                                                     LoginUser loginUser, String code, String message,
                                                     String severity, String field, boolean retryable,
                                                     List<String> actions, Integer upstreamStatus, long durationMs) {
        String auditRef = recordAudit(toolName, request, loginUser, "ERROR", code, durationMs, null);
        return error(toolName, toolCallId(request), code, message, severity, field, retryable,
                actions, upstreamStatus, auditRef);
    }

    private InternalAgentToolResponseVO error(String toolName, String toolCallId, String code, String message,
                                              String severity, String field, boolean retryable,
                                              List<String> actions, Integer upstreamStatus, String auditRef) {
        return InternalAgentToolResponseVO.error(toolName, toolCallId,
                new InternalAgentToolErrorVO(code, message, severity, field, retryable, actions, upstreamStatus),
                auditRef);
    }

    private String recordAudit(String toolName, InternalAgentToolRequestDTO request, LoginUser loginUser,
                               String resultCode, String errorCode, long durationMs, JsonNode result) {
        if (request == null || request.getAgentSessionId() == null || request.getAgentSessionId().isBlank()) {
            return null;
        }
        AgentToolAuditDTO audit = new AgentToolAuditDTO();
        audit.setToolName(safeIdentifier(toolName, 100));
        audit.setToolCallId(safeIdentifier(request.getToolCallId(), 100));
        audit.setMessageId(safeIdentifier(request.getMessageId(), 100));
        audit.setUpstreamPath(UPSTREAM_PATHS.get(toolName));
        audit.setArgumentsSummary(limit(sanitizeText(toJson(request.getArguments())), 1000));
        audit.setRequestSummary(limit(sanitizeText(toJson(requestSummary(request))), 1000));
        audit.setResponseSummary(limit(sanitizeText(toJson(responseSummary(result))), 1000));
        audit.setResultCode(resultCode);
        audit.setErrorCode(errorCode);
        audit.setDurationMs(durationMs);
        try {
            Integer userId = loginUser == null || loginUser.getUser() == null ? null : loginUser.getUser().getId();
            return agentSessionService.recordToolAudit(request.getAgentSessionId(), userId, audit);
        } catch (RuntimeException e) {
            log.warn("Failed to record internal Agent Tool audit for session {} and tool {}.",
                    safeIdentifier(request.getAgentSessionId(), 64), safeIdentifier(toolName, 100));
            return null;
        }
    }

    private Map<String, Object> requestSummary(InternalAgentToolRequestDTO request) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("toolCallId", request.getToolCallId());
        summary.put("messageId", request.getMessageId());
        if (request.getClient() != null) {
            summary.put("traceId", request.getClient().getTraceId());
            summary.put("requestId", request.getClient().getRequestId());
        }
        return summary;
    }

    private Map<String, Object> responseSummary(JsonNode result) {
        Map<String, Object> summary = new LinkedHashMap<>();
        if (result == null || result.isNull() || result.isMissingNode()) {
            summary.put("empty", true);
            return summary;
        }
        for (String field : List.of("resolutionStatus", "needsUserSelection", "needsAssay", "partial")) {
            if (result.has(field)) {
                summary.put(field, result.get(field));
            }
        }
        for (String field : List.of("code", "isError", "message")) {
            if (result.has(field)) {
                summary.put(field, result.get(field));
            }
        }
        if (result.path("candidates").isArray()) {
            summary.put("candidateCount", result.path("candidates").size());
        }
        if (result.path("options").isArray()) {
            summary.put("optionCount", result.path("options").size());
        }
        for (String field : List.of("scopeLabel", "groupBy", "totalStockText", "warehouseCount", "productCount", "palletCount")) {
            if (result.has(field)) {
                summary.put(field, result.get(field));
            }
        }
        if (result.path("groups").isArray()) {
            summary.put("groupCount", result.path("groups").size());
        }
        return summary;
    }

    private JsonNode sanitizeNode(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return node;
        }
        JsonNode copy = node.deepCopy();
        sanitizeNodeInPlace(copy);
        return copy;
    }

    private void sanitizeNodeInPlace(JsonNode node) {
        if (node instanceof ObjectNode objectNode) {
            Iterator<Map.Entry<String, JsonNode>> fields = objectNode.fields();
            List<String> remove = new ArrayList<>();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if (sensitiveField(field.getKey())) {
                    remove.add(field.getKey());
                } else if (field.getValue().isTextual()) {
                    objectNode.put(field.getKey(), sanitizeText(field.getValue().asText()));
                } else {
                    sanitizeNodeInPlace(field.getValue());
                }
            }
            remove.forEach(objectNode::remove);
        } else if (node instanceof ArrayNode arrayNode) {
            arrayNode.forEach(this::sanitizeNodeInPlace);
        }
    }

    private boolean sensitiveField(String field) {
        String normalized = field == null ? "" : field.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
        return normalized.contains("authorization")
                || normalized.contains("token")
                || normalized.contains("delegationtoken")
                || normalized.contains("refreshtoken")
                || normalized.contains("password")
                || normalized.equals("stack")
                || normalized.contains("stacktrace")
                || normalized.contains("exception")
                || normalized.contains("serverpath")
                || normalized.contains("internalpath")
                || normalized.contains("connectionstring");
    }

    private String sanitizeText(String value) {
        if (value == null) {
            return null;
        }
        return value
                .replaceAll("(?i)authorization\\s*[:=]\\s*bearer\\s+[^\\s,;]+", "Authorization: <redacted>")
                .replaceAll("(?i)bearer\\s+[^\\s,;]+", "Bearer <redacted>")
                .replaceAll("(?i)(delegation[_-]?token|refresh[_-]?token|token|api[_-]?key|password|secret)\\s*[:=]\\s*[^\\s,;]+", "$1=<redacted>")
                .replaceAll("(?i)jdbc:[^\\s,;]+", "jdbc:<redacted>")
                .replaceAll("(?<![A-Za-z0-9_$.])([A-Z][A-Za-z0-9_$.]*(Exception|Error))(?![A-Za-z0-9_$.])(:\\s*[^\\r\\n]*)?", "<exception redacted>")
                .replaceAll("(?<![A-Za-z0-9])(/[A-Za-z0-9._-]+){2,}", "<path redacted>")
                .replaceAll("[A-Za-z]:\\\\[^\\r\\n\\t ]+", "<path redacted>")
                .replaceAll("(?m)^\\s*at\\s+.+$", "<stack redacted>");
    }

    private boolean authenticated(String providedKey) {
        if (expectedServiceKey.isBlank() || providedKey == null || providedKey.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(expectedServiceKey.getBytes(StandardCharsets.UTF_8),
                providedKey.getBytes(StandardCharsets.UTF_8));
    }

    private boolean validToolName(String toolName) {
        return toolName != null && !toolName.isBlank() && toolName.length() <= 100
                && toolName.matches("[a-z][a-z0-9_]*");
    }

    private boolean blankOrTooLong(String value, int maxLength) {
        return value == null || value.isBlank() || value.length() > maxLength;
    }

    private boolean tooLong(String value, int maxLength) {
        return value != null && value.length() > maxLength;
    }

    private String toolCallId(InternalAgentToolRequestDTO request) {
        return request == null ? null : safeIdentifier(request.getToolCallId(), 100);
    }

    private String safeIdentifier(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String safe = value.replaceAll("[^A-Za-z0-9_.:-]", "_");
        return safe.length() <= maxLength ? safe : safe.substring(0, maxLength);
    }

    private String safeSessionMessage(String code) {
        if ("AGENT_SCOPE_DENIED".equals(code)) {
            return "Agent 会话没有只读仓储查询权限。";
        }
        if ("AGENT_USER_INVALID".equals(code)) {
            return "Agent 会话关联用户已失效。";
        }
        return "Agent 会话无效或已过期。";
    }

    private String safeToolMessage(String code) {
        return switch (code) {
            case "INVALID_ARGUMENT" -> "工具参数不合法。";
            case "UPSTREAM_BAD_REQUEST" -> "仓储后端拒绝了只读查询参数。";
            case "UPSTREAM_UNAUTHORIZED" -> "仓储后端认证失败。";
            case "UPSTREAM_PERMISSION_DENIED" -> "当前用户没有执行该只读查询的权限。";
            case "UPSTREAM_NOT_FOUND", "NOT_FOUND" -> "未找到符合条件的业务数据。";
            case "UPSTREAM_TIMEOUT" -> "仓储后端查询超时。";
            case "UPSTREAM_SERVER_ERROR" -> "仓储后端暂时无法完成查询。";
            default -> "只读仓储工具调用失败。";
        };
    }

    private boolean retryable(String code) {
        return "UPSTREAM_TIMEOUT".equals(code) || "UPSTREAM_SERVER_ERROR".equals(code) || "MCP_CALL_FAILED".equals(code);
    }

    private List<String> suggestedActions(String code) {
        return retryable(code) ? List.of("稍后重试。") : List.of("检查查询条件或当前用户权限。");
    }

    private InternalAgentToolErrorVO toolError(String code, String message, String field, boolean retryable,
                                               List<String> actions, Integer upstreamStatus) {
        return new InternalAgentToolErrorVO(code, message, "ERROR", field, retryable, actions, upstreamStatus);
    }

    private String text(JsonNode node, String field, String fallback) {
        String value = nullableText(node, field);
        return value == null ? fallback : value;
    }

    private String nullableText(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.path(field);
        return value == null || value.isMissingNode() || value.isNull() || value.asText().isBlank()
                ? null : value.asText();
    }

    private List<String> stringList(JsonNode node, List<String> fallback) {
        if (node == null || !node.isArray()) {
            return fallback;
        }
        List<String> values = new ArrayList<>();
        node.forEach(item -> {
            if (item.isTextual() && !item.asText().isBlank()) {
                values.add(limit(sanitizeText(item.asText()), 200));
            }
        });
        return values.isEmpty() ? fallback : values;
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "<summary unavailable>";
        }
    }

    private String limit(String value, int maxLength) {
        return value == null || value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }
}
