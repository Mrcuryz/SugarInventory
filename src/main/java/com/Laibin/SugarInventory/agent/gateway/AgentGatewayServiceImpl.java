package com.Laibin.SugarInventory.agent.gateway;

import com.Laibin.SugarInventory.SpringSecurity.LoginUser;
import com.Laibin.SugarInventory.agent.context.AgentConversationMemory;
import com.Laibin.SugarInventory.agent.dto.AgentMessageRequestDTO;
import com.Laibin.SugarInventory.agent.dto.AgentToolAuditDTO;
import com.Laibin.SugarInventory.agent.mcp.McpSession;
import com.Laibin.SugarInventory.agent.mcp.McpSessionManager;
import com.Laibin.SugarInventory.agent.mcp.McpToolCall;
import com.Laibin.SugarInventory.agent.mcp.McpToolResult;
import com.Laibin.SugarInventory.agent.model.AgentIntent;
import com.Laibin.SugarInventory.agent.model.AgentModelClient;
import com.Laibin.SugarInventory.agent.model.AgentPlan;
import com.Laibin.SugarInventory.agent.service.AgentSessionService;
import com.Laibin.SugarInventory.agent.vo.AgentChoiceOptionVO;
import com.Laibin.SugarInventory.agent.vo.AgentMessageResponseVO;
import com.Laibin.SugarInventory.agent.vo.AgentSessionVO;
import com.Laibin.SugarInventory.agent.vo.AgentToolCallSummaryVO;
import com.Laibin.SugarInventory.domain.po.AgentSession;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service("legacyAgentGatewayService")
public class AgentGatewayServiceImpl implements AgentGatewayService {
    private static final String STATUS_UNIQUE = "UNIQUE";
    private static final String STATUS_AMBIGUOUS = "AMBIGUOUS";
    private static final String STATUS_NOT_FOUND = "NOT_FOUND";

    private final AgentSessionService agentSessionService;
    private final McpSessionManager mcpSessionManager;
    private final AgentModelClient modelClient;
    private final ObjectMapper objectMapper;
    private final AgentConversationMemory conversationMemory;

    public AgentGatewayServiceImpl(AgentSessionService agentSessionService,
                                   McpSessionManager mcpSessionManager,
                                   AgentModelClient modelClient,
                                   ObjectMapper objectMapper,
                                   AgentConversationMemory conversationMemory) {
        this.agentSessionService = agentSessionService;
        this.mcpSessionManager = mcpSessionManager;
        this.modelClient = modelClient;
        this.objectMapper = objectMapper;
        this.conversationMemory = conversationMemory;
    }

    @Override
    public AgentMessageResponseVO handleMessage(LoginUser loginUser, String agentSessionId, AgentMessageRequestDTO request) {
        AgentSession session = agentSessionService.requireOwnedActiveSession(loginUser, agentSessionId);
        AgentSessionVO sessionVO = agentSessionService.toSessionVO(session, loginUser);
        AgentMessageResponseVO response = new AgentMessageResponseVO();
        response.setSession(sessionVO);
        AgentConversationMemory.SelectedOption selectedOption = selectedOption(agentSessionId, request.getPageContext());
        if (selectedOption != null) {
            McpSession mcpSession = mcpSessionManager.bindSession(loginUser, session);
            AgentMessageResponseVO handled = handleSelectedOption(selectedOption, mcpSession, sessionVO, loginUser, response);
            conversationMemory.recordAssistantMessage(agentSessionId, handled.getAnswer());
            return filterDebugToolCalls(loginUser, handled);
        }

        conversationMemory.recordUserMessage(agentSessionId, request.getMessage());
        Map<String, Object> pageContext = planningContext(agentSessionId, request.getPageContext());
        AgentPlan plan = enrichPlan(modelClient.plan(request.getMessage(), pageContext), request.getMessage(), agentSessionId);
        if (plan.getIntent() == null || plan.getIntent() == AgentIntent.UNSUPPORTED) {
            Object assistantReply = plan.getHints() == null ? null : plan.getHints().get("assistantReply");
            response.setAnswer(assistantReply instanceof String text && !text.isBlank()
                    ? text
                    : "当前 AI 助手只支持库存、库位、托盘和化验的只读查询。请补充要查询的产品、库位、托盘码或生产日期。");
            conversationMemory.recordAssistantMessage(agentSessionId, response.getAnswer());
            return response;
        }

        McpSession mcpSession = mcpSessionManager.bindSession(loginUser, session);
        AgentMessageResponseVO handled = switch (plan.getIntent()) {
            case INVENTORY_OVERVIEW -> handleInventory(plan, mcpSession, sessionVO, loginUser, response);
            case WAREHOUSE_STATUS -> handleWarehouse(plan, mcpSession, sessionVO, loginUser, response);
            case PALLET_STATUS -> handlePallet(plan, mcpSession, sessionVO, loginUser, response);
            case ASSAY_STATUS -> handleAssay(plan, mcpSession, sessionVO, loginUser, response);
            case UNSUPPORTED -> response;
        };
        conversationMemory.recordAssistantMessage(agentSessionId, handled.getAnswer());
        return filterDebugToolCalls(loginUser, handled);
    }

    private AgentMessageResponseVO filterDebugToolCalls(LoginUser loginUser, AgentMessageResponseVO response) {
        String roleCode = loginUser == null || loginUser.getUser() == null ? null : loginUser.getUser().getRoleCode();
        if (!Objects.equals(roleCode, "ADMIN") && !Objects.equals(roleCode, "SUPER_ADMIN")) {
            response.getToolCalls().clear();
        }
        return response;
    }

    @SuppressWarnings("unchecked")
    private AgentConversationMemory.SelectedOption selectedOption(String agentSessionId, Map<String, Object> pageContext) {
        if (pageContext == null) {
            return null;
        }
        Object value = pageContext.get("selectedOption");
        if (!(value instanceof Map<?, ?> map)) {
            return null;
        }
        return conversationMemory.resolveSelectedOption(agentSessionId, (Map<String, Object>) map);
    }

    private AgentMessageResponseVO handleSelectedOption(AgentConversationMemory.SelectedOption selectedOption,
                                                        McpSession mcpSession,
                                                        AgentSessionVO sessionVO,
                                                        LoginUser loginUser,
                                                        AgentMessageResponseVO response) {
        if (!selectedOption.known()) {
            response.setNeedsUserSelection(true);
            response.setAnswer("这个候选项已失效，请重新输入查询内容并从新的候选卡片中选择。");
            return response;
        }
        if (!selectedOption.supported()) {
            response.setNeedsUserSelection(true);
            response.setAnswer("这个查询范围目前还没有安全的聚合查询能力，请先选择一个具体产品或具体库位。");
            return response;
        }
        if ("SINGLE_PRODUCT".equals(selectedOption.optionType()) && selectedOption.productId() != null) {
            return handleSelectedProduct(selectedOption, mcpSession, sessionVO, loginUser, response);
        }
        if ("SINGLE_WAREHOUSE".equals(selectedOption.optionType()) && selectedOption.warehouseId() != null) {
            return handleSelectedWarehouse(selectedOption, mcpSession, sessionVO, loginUser, response);
        }
        response.setNeedsUserSelection(true);
        response.setAnswer("这个候选项暂不支持直接查询，请选择具体产品或具体库位。");
        return response;
    }

    private AgentMessageResponseVO handleSelectedProduct(AgentConversationMemory.SelectedOption selectedOption,
                                                         McpSession mcpSession,
                                                         AgentSessionVO sessionVO,
                                                         LoginUser loginUser,
                                                         AgentMessageResponseVO response) {
        conversationMemory.rememberProduct(sessionVO.getAgentSessionId(), selectedOption.productId(), selectedOption.displayLabel(), selectedOption.displayLabel());
        McpToolResult overview = callTool(mcpSession, loginUser, sessionVO.getAgentSessionId(), "get_inventory_overview",
                "/api/inventory/stock/page", args("productId", selectedOption.productId(), "page", 1, "size", 10), response);
        if (!overview.success()) {
            response.setAnswer("查询库存时遇到权限或上游服务错误，未将错误解释为空库存。");
            return response;
        }
        response.setAnswer(formatInventoryAnswer(selectedOption.displayLabel(), overview.result(), false));
        return response;
    }

    private AgentMessageResponseVO handleSelectedWarehouse(AgentConversationMemory.SelectedOption selectedOption,
                                                           McpSession mcpSession,
                                                           AgentSessionVO sessionVO,
                                                           LoginUser loginUser,
                                                           AgentMessageResponseVO response) {
        conversationMemory.rememberWarehouse(sessionVO.getAgentSessionId(), selectedOption.warehouseId(), selectedOption.displayLabel(), selectedOption.displayLabel());
        McpToolResult statusResult = callTool(mcpSession, loginUser, sessionVO.getAgentSessionId(), "get_warehouse_status",
                "/api/warehouses/{id}, /api/inventory/stock/page", args("warehouseId", selectedOption.warehouseId(),
                        "includeInventoryDetails", true, "includeRecentOperations", true, "page", 1, "size", 10, "recentLimit", 10), response);
        if (!statusResult.success()) {
            response.setAnswer("查询库位状态时遇到权限或上游服务错误，未将错误解释为空库位状态。");
            return response;
        }
        response.setAnswer(formatWarehouseAnswer(selectedOption.displayLabel(), statusResult.result()));
        return response;
    }
    private Map<String, Object> planningContext(String agentSessionId, Map<String, Object> requestContext) {
        Map<String, Object> context = new LinkedHashMap<>();
        if (requestContext != null) {
            context.putAll(requestContext);
        }
        context.put("conversationContext", conversationMemory.modelContext(agentSessionId));
        return context;
    }

    private AgentPlan enrichPlan(AgentPlan plan, String message, String agentSessionId) {
        AgentConversationMemory.ConversationSnapshot snapshot = conversationMemory.snapshot(agentSessionId);
        String text = message == null ? "" : message.trim();
        if (isAssayFollowUp(text) && snapshot.lastProductId() != null) {
            plan.setIntent(AgentIntent.ASSAY_STATUS);
            plan.setEntityQuery(snapshot.lastProductLabel());
            if (plan.getProductionDate() == null && text.contains("今天")) {
                plan.setProductionDate(LocalDate.now().toString());
            }
            plan.getHints().put("resolvedProductId", snapshot.lastProductId());
            return plan;
        }
        if (isProductLocationQuestion(text) && snapshot.lastProductId() != null) {
            plan.setIntent(AgentIntent.INVENTORY_OVERVIEW);
            plan.setEntityQuery(snapshot.lastProductLabel());
            plan.getHints().put("locationQuestion", Boolean.TRUE);
            plan.getHints().put("resolvedProductId", snapshot.lastProductId());
            return plan;
        }
        if ((plan.getIntent() == AgentIntent.INVENTORY_OVERVIEW || plan.getIntent() == AgentIntent.ASSAY_STATUS)
                && shouldUseLastReference(plan.getEntityQuery(), text)
                && snapshot.lastProductId() != null) {
            plan.setEntityQuery(snapshot.lastProductLabel());
            plan.getHints().put("resolvedProductId", snapshot.lastProductId());
        }
        if (plan.getIntent() == AgentIntent.WAREHOUSE_STATUS
                && shouldUseLastReference(plan.getEntityQuery(), text)
                && snapshot.lastWarehouseLabel() != null) {
            plan.setEntityQuery(snapshot.lastWarehouseLabel());
        }
        if (plan.getIntent() == AgentIntent.WAREHOUSE_STATUS) {
            plan.setEntityQuery(cleanWarehouseQuery(plan.getEntityQuery()));
        }
        return plan;
    }

    private Integer resolvedProductId(AgentPlan plan) {
        if (plan.getHints() == null) {
            return null;
        }
        return positiveInt(plan.getHints().get("resolvedProductId"));
    }
    private boolean isLocationQuestion(AgentPlan plan) {
        Object value = plan.getHints() == null ? null : plan.getHints().get("locationQuestion");
        return Boolean.TRUE.equals(value);
    }

    private boolean isAssayFollowUp(String text) {
        return containsAny(text, "化验", "合格", "不合格")
                && containsAny(text, "这些", "它", "其", "刚才", "这个", "这种", "糖", "产品", "货");
    }
    private boolean isProductLocationQuestion(String text) {
        return containsAny(text, "存放", "放在", "在哪个库位", "在哪些库位", "哪些库位", "哪个库位", "位置")
                && containsAny(text, "这些", "它", "其", "刚才", "这个", "这种", "糖", "产品", "货");
    }

    private boolean shouldUseLastReference(String entityQuery, String message) {
        String query = normalizeQuery(entityQuery);
        if (query == null) {
            return containsAny(message, "这些", "它", "那个", "这个", "刚才", "上述", "前面", "这些糖");
        }
        return containsAny(query, "这些", "它", "那个", "这个", "刚才", "上述", "前面");
    }

    private String cleanWarehouseQuery(String entityQuery) {
        String query = normalizeQuery(entityQuery);
        if (query == null) {
            return null;
        }
        String cleaned = query.replaceAll("(当前|现在|目前|容量|占用|剩余|余量|还有多少|还剩多少|多少|情况|状态|的)", "")
                .trim();
        return cleaned.isBlank() ? query : cleaned;
    }
    private AgentMessageResponseVO handleInventory(AgentPlan plan, McpSession mcpSession, AgentSessionVO sessionVO,
                                                   LoginUser loginUser, AgentMessageResponseVO response) {
        String query = normalizeQuery(plan.getEntityQuery());
        Integer contextProductId = resolvedProductId(plan);
        if (contextProductId != null) {
            return handleResolvedProductInventory(contextProductId, query, isLocationQuestion(plan), mcpSession, sessionVO, loginUser, response);
        }
        if (query == null) {
            response.setAnswer("请提供要查询库存的产品名称或编号。");
            return response;
        }
        McpToolResult resolved = callTool(mcpSession, loginUser, sessionVO.getAgentSessionId(), "resolve_products",
                "/api/products/page", args("query", query, "limit", 10), response);
        if (!resolved.success()) {
            response.setAnswer("查询产品时遇到权限或上游服务错误，请稍后重试或联系管理员。");
            return response;
        }
        JsonNode result = resolved.result();
        String status = result.path("resolutionStatus").asText("");
        if (STATUS_AMBIGUOUS.equals(status)) {
            response.setNeedsUserSelection(true);
            response.setOptions(extractOptions(result, true));
            conversationMemory.rememberOptions(sessionVO.getAgentSessionId(), response.getOptions());
            response.setAnswer(defaultText(result.path("clarificationPrompt").asText(null), "找到多个可能的产品，请先选择要查询的范围或具体规格。"));
            return response;
        }
        if (STATUS_NOT_FOUND.equals(status)) {
            response.setAnswer("未找到匹配产品，请换一个更准确的产品名称或编号。");
            return response;
        }
        Integer productId = firstCandidateId(result, "productId", "id");
        String productLabel = firstCandidateLabel(result, true);
        if (!STATUS_UNIQUE.equals(status) || productId == null) {
            response.setNeedsUserSelection(true);
            response.setOptions(extractOptions(result, true));
            conversationMemory.rememberOptions(sessionVO.getAgentSessionId(), response.getOptions());
            response.setAnswer("产品解析结果不唯一，请先选择具体产品后再查询库存。");
            return response;
        }
        conversationMemory.rememberProduct(sessionVO.getAgentSessionId(), productId, productLabel, query);
        McpToolResult overview = callTool(mcpSession, loginUser, sessionVO.getAgentSessionId(), "get_inventory_overview",
                "/api/inventory/stock/page", args("productId", productId, "page", 1, "size", 10), response);
        if (!overview.success()) {
            response.setAnswer("查询库存时遇到权限或上游服务错误，未将错误解释为空库存。");
            return response;
        }
        response.setAnswer(formatInventoryAnswer(defaultText(productLabel, query), overview.result(), isLocationQuestion(plan)));
        return response;
    }

    private AgentMessageResponseVO handleResolvedProductInventory(Integer productId,
                                                                  String query,
                                                                  boolean locationQuestion,
                                                                  McpSession mcpSession,
                                                                  AgentSessionVO sessionVO,
                                                                  LoginUser loginUser,
                                                                  AgentMessageResponseVO response) {
        String label = defaultText(safeBusinessLabel(query), "该产品");
        conversationMemory.rememberProduct(sessionVO.getAgentSessionId(), productId, label, label);
        McpToolResult overview = callTool(mcpSession, loginUser, sessionVO.getAgentSessionId(), "get_inventory_overview",
                "/api/inventory/stock/page", args("productId", productId, "page", 1, "size", 10), response);
        if (!overview.success()) {
            response.setAnswer("查询库存时遇到权限或上游服务错误，未将错误解释为空库存。");
            return response;
        }
        response.setAnswer(formatInventoryAnswer(label, overview.result(), locationQuestion));
        return response;
    }

    private AgentMessageResponseVO handleWarehouse(AgentPlan plan, McpSession mcpSession, AgentSessionVO sessionVO,
                                                   LoginUser loginUser, AgentMessageResponseVO response) {
        String query = normalizeQuery(plan.getEntityQuery());
        if (query == null) {
            response.setAnswer("请提供要查询的库位名称或编号。");
            return response;
        }
        McpToolResult resolved = callTool(mcpSession, loginUser, sessionVO.getAgentSessionId(), "resolve_warehouses",
                "/api/warehouses/page", args("query", query, "onlyAvailable", false, "limit", 10), response);
        if (!resolved.success()) {
            response.setAnswer("查询库位时遇到权限或上游服务错误，请稍后重试或联系管理员。");
            return response;
        }
        JsonNode result = resolved.result();
        String status = result.path("resolutionStatus").asText("");
        if (STATUS_AMBIGUOUS.equals(status)) {
            response.setNeedsUserSelection(true);
            response.setOptions(extractOptions(result, false));
            conversationMemory.rememberOptions(sessionVO.getAgentSessionId(), response.getOptions());
            response.setAnswer(defaultText(result.path("clarificationPrompt").asText(null), "找到多个可能的库位，请先选择具体库位。"));
            return response;
        }
        if (STATUS_NOT_FOUND.equals(status)) {
            response.setAnswer("未找到匹配库位，请换一个更准确的库位名称或编号。");
            return response;
        }
        Integer warehouseId = firstCandidateId(result, "warehouseId", "id");
        String warehouseLabel = firstCandidateLabel(result, false);
        if (!STATUS_UNIQUE.equals(status) || warehouseId == null) {
            response.setNeedsUserSelection(true);
            response.setOptions(extractOptions(result, false));
            conversationMemory.rememberOptions(sessionVO.getAgentSessionId(), response.getOptions());
            response.setAnswer("库位解析结果不唯一，请先选择具体库位后再查询状态。");
            return response;
        }
        conversationMemory.rememberWarehouse(sessionVO.getAgentSessionId(), warehouseId, warehouseLabel, query);
        McpToolResult statusResult = callTool(mcpSession, loginUser, sessionVO.getAgentSessionId(), "get_warehouse_status",
                "/api/warehouses/{id}, /api/inventory/stock/page", args("warehouseId", warehouseId,
                        "includeInventoryDetails", true, "includeRecentOperations", true, "page", 1, "size", 10, "recentLimit", 10), response);
        if (!statusResult.success()) {
            response.setAnswer("查询库位状态时遇到权限或上游服务错误，未将错误解释为空库位状态。");
            return response;
        }
        response.setAnswer(formatWarehouseAnswer(defaultText(warehouseLabel, query), statusResult.result()));
        return response;
    }

    private AgentMessageResponseVO handlePallet(AgentPlan plan, McpSession mcpSession, AgentSessionVO sessionVO,
                                                LoginUser loginUser, AgentMessageResponseVO response) {
        String code = normalizeQuery(plan.getPalletCode());
        if (code == null) {
            response.setAnswer("请提供要查询的托盘码。");
            return response;
        }
        McpToolResult result = callTool(mcpSession, loginUser, sessionVO.getAgentSessionId(), "get_pallet_status",
                "/api/pallet-codes/parse, /api/pallet-codes/{code}/inventory, /api/pallet-codes/{code}/assay, /api/pallet-codes/{code}/flows",
                args("code", code, "includeInventory", true, "includeAssay", true, "includeFlows", true, "flowLimit", 20), response);
        if (!result.success()) {
            response.setAnswer("查询托盘状态时遇到权限或上游服务错误，未编造托盘状态。");
            return response;
        }
        response.setAnswer(formatPalletAnswer(code, result.result()));
        return response;
    }

    private AgentMessageResponseVO handleAssay(AgentPlan plan, McpSession mcpSession, AgentSessionVO sessionVO,
                                               LoginUser loginUser, AgentMessageResponseVO response) {
        String query = normalizeQuery(plan.getEntityQuery());
        String productionDate = normalizeQuery(plan.getProductionDate());
        if (productionDate == null) {
            response.setAnswer("请提供生产日期，例如 2026-06-25，才能查询某产品某日期的化验状态。");
            return response;
        }
        Integer contextProductId = resolvedProductId(plan);
        if (contextProductId != null) {
            return handleResolvedProductAssay(contextProductId, query, productionDate, mcpSession, sessionVO, loginUser, response);
        }
        if (query == null) {
            response.setAnswer("请提供要查询化验的产品名称或编号。");
            return response;
        }
        McpToolResult resolved = callTool(mcpSession, loginUser, sessionVO.getAgentSessionId(), "resolve_products",
                "/api/products/page", args("query", query, "limit", 10), response);
        if (!resolved.success()) {
            response.setAnswer("查询产品时遇到权限或上游服务错误，请稍后重试或联系管理员。");
            return response;
        }
        JsonNode result = resolved.result();
        String status = result.path("resolutionStatus").asText("");
        if (STATUS_AMBIGUOUS.equals(status)) {
            response.setNeedsUserSelection(true);
            response.setOptions(extractOptions(result, true));
            conversationMemory.rememberOptions(sessionVO.getAgentSessionId(), response.getOptions());
            response.setAnswer(defaultText(result.path("clarificationPrompt").asText(null), "找到多个可能的产品，请先选择要查询化验的具体产品。"));
            return response;
        }
        if (STATUS_NOT_FOUND.equals(status)) {
            response.setAnswer("未找到匹配产品，无法判断化验状态。");
            return response;
        }
        Integer productId = firstCandidateId(result, "productId", "id");
        String productLabel = firstCandidateLabel(result, true);
        if (!STATUS_UNIQUE.equals(status) || productId == null) {
            response.setNeedsUserSelection(true);
            response.setOptions(extractOptions(result, true));
            conversationMemory.rememberOptions(sessionVO.getAgentSessionId(), response.getOptions());
            response.setAnswer("产品解析结果不唯一，请先选择具体产品后再查询化验。");
            return response;
        }
        conversationMemory.rememberProduct(sessionVO.getAgentSessionId(), productId, productLabel, query);
        McpToolResult assay = callTool(mcpSession, loginUser, sessionVO.getAgentSessionId(), "get_assay_status",
                "/api/assay/by-product-date", args("productId", productId, "productionDate", productionDate, "includeStandardDetails", true), response);
        if (!assay.success()) {
            response.setAnswer("查询化验状态时遇到权限或上游服务错误，未将错误解释为无化验或合格。 ");
            return response;
        }
        response.setAnswer(formatAssayAnswer(defaultText(productLabel, query), productionDate, assay.result()));
        return response;
    }

    private AgentMessageResponseVO handleResolvedProductAssay(Integer productId,
                                                              String query,
                                                              String productionDate,
                                                              McpSession mcpSession,
                                                              AgentSessionVO sessionVO,
                                                              LoginUser loginUser,
                                                              AgentMessageResponseVO response) {
        String label = defaultText(safeBusinessLabel(query), "该产品");
        conversationMemory.rememberProduct(sessionVO.getAgentSessionId(), productId, label, label);
        McpToolResult assay = callTool(mcpSession, loginUser, sessionVO.getAgentSessionId(), "get_assay_status",
                "/api/assay/by-product-date", args("productId", productId, "productionDate", productionDate, "includeStandardDetails", true), response);
        if (!assay.success()) {
            response.setAnswer("查询化验状态时遇到权限或上游服务错误，未将错误解释为无化验或合格。");
            return response;
        }
        response.setAnswer(formatAssayAnswer(label, productionDate, assay.result()));
        return response;
    }

    private McpToolResult callTool(McpSession mcpSession, LoginUser loginUser, String agentSessionId, String toolName,
                                   String upstreamPath, Map<String, Object> arguments, AgentMessageResponseVO response) {
        McpToolResult result = mcpSession.callTool(new McpToolCall(toolName, arguments));
        String errorCode = resolveErrorCode(result);
        String resultCode = errorCode == null ? result.resultCode() : "ERROR";

        AgentToolCallSummaryVO summary = new AgentToolCallSummaryVO();
        summary.setToolName(toolName);
        summary.setUpstreamPath(upstreamPath);
        summary.setResultCode(resultCode);
        summary.setErrorCode(errorCode);
        summary.setDurationMs(result.durationMs());
        summary.setRequestSummary(toSafeSummary(arguments));
        summary.setResponseSummary(toSafeSummary(summarizeResult(result.result())));
        response.getToolCalls().add(summary);

        AgentToolAuditDTO audit = new AgentToolAuditDTO();
        audit.setToolName(toolName);
        audit.setUpstreamPath(upstreamPath);
        audit.setArgumentsSummary(summary.getRequestSummary());
        audit.setRequestSummary(summary.getRequestSummary());
        audit.setResponseSummary(summary.getResponseSummary());
        audit.setResultCode(resultCode);
        audit.setErrorCode(errorCode);
        audit.setDurationMs(result.durationMs());
        agentSessionService.recordToolAudit(agentSessionId, loginUser.getUser().getId(), audit);

        if (errorCode != null) {
            return new McpToolResult(result.toolName(), result.result(), "ERROR", errorCode, result.durationMs());
        }
        return result;
    }

    private String resolveErrorCode(McpToolResult result) {
        if (!result.success()) {
            return result.errorCode() == null ? "MCP_TOOL_ERROR" : result.errorCode();
        }
        JsonNode node = result.result();
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        JsonNode error = node.path("error");
        if (!error.isMissingNode() && !error.isNull()) {
            String code = error.path("code").asText(null);
            return code == null || code.isBlank() ? "MCP_TOOL_ERROR" : code;
        }
        String code = node.path("code").asText(null);
        String severity = node.path("severity").asText(null);
        if (code != null && severity != null) {
            return code;
        }
        return null;
    }

    private Map<String, Object> args(Object... entries) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < entries.length; i += 2) {
            if (entries[i + 1] != null) {
                map.put(String.valueOf(entries[i]), entries[i + 1]);
            }
        }
        return map;
    }

    private Integer firstCandidateId(JsonNode result, String preferredField, String fallbackField) {
        JsonNode candidates = result.path("candidates");
        if (candidates.isArray() && !candidates.isEmpty()) {
            JsonNode first = candidates.get(0);
            Integer preferred = positiveInt(first.path(preferredField));
            return preferred == null ? positiveInt(first.path(fallbackField)) : preferred;
        }
        Integer direct = positiveInt(result.path(preferredField));
        return direct == null ? positiveInt(result.path(fallbackField)) : direct;
    }

    private String firstCandidateLabel(JsonNode result, boolean productLabel) {
        JsonNode candidates = result.path("candidates");
        if (candidates.isArray() && !candidates.isEmpty()) {
            JsonNode first = candidates.get(0);
            String label = firstText(first, "displayLabel", productLabel ? "productName" : "warehouseName", "name", "code");
            if (label != null) {
                return safeBusinessLabel(label);
            }
        }
        return safeBusinessLabel(firstText(result, "displayLabel", productLabel ? "productName" : "warehouseName", "name", "code"));
    }
    private Integer positiveInt(JsonNode node) {
        if (node != null && node.canConvertToInt() && node.asInt() > 0) {
            return node.asInt();
        }
        return null;
    }

    private List<AgentChoiceOptionVO> extractOptions(JsonNode result, boolean productOptions) {
        List<AgentChoiceOptionVO> options = new ArrayList<>();
        JsonNode optionNodes = result.path("options");
        if (optionNodes.isArray()) {
            for (JsonNode node : optionNodes) {
                AgentChoiceOptionVO option = new AgentChoiceOptionVO();
                option.setOptionType(node.path("optionType").asText(productOptions ? "SINGLE_PRODUCT" : "SINGLE_WAREHOUSE"));
                option.setDisplayLabel(defaultText(node.path("displayLabel").asText(null), node.path("name").asText("候选项")));
                option.setProductId(positiveInt(node.path("productId")));
                option.setWarehouseId(positiveInt(node.path("warehouseId")));
                option.setSupported(node.has("supported") ? node.path("supported").asBoolean() : Boolean.TRUE);
                options.add(option);
            }
        }
        if (!options.isEmpty()) {
            return options;
        }
        JsonNode candidates = result.path("candidates");
        if (candidates.isArray()) {
            for (JsonNode node : candidates) {
                AgentChoiceOptionVO option = new AgentChoiceOptionVO();
                option.setOptionType(productOptions ? "SINGLE_PRODUCT" : "SINGLE_WAREHOUSE");
                option.setDisplayLabel(defaultText(firstText(node, "displayLabel", "productName", "warehouseName", "name", "code"), "候选项"));
                option.setProductId(positiveInt(node.path("productId")) == null ? positiveInt(node.path("id")) : positiveInt(node.path("productId")));
                option.setWarehouseId(positiveInt(node.path("warehouseId")) == null ? positiveInt(node.path("id")) : positiveInt(node.path("warehouseId")));
                option.setSupported(Boolean.TRUE);
                options.add(option);
            }
        }
        return options;
    }

    private String formatInventoryAnswer(String query, JsonNode node, boolean locationQuestion) {
        if (locationQuestion) {
            return formatInventoryLocationAnswer(query, node);
        }
        if (isToolNotFound(node)) {
            return "未查询到 " + query + " 的库存记录。";
        }
        JsonNode summary = childObject(node, "summary");
        JsonNode firstRecord = firstArrayElement(node, "records");
        if (isEmptyInventory(node, summary, firstRecord)) {
            return "未查询到 " + query + " 的库存记录。";
        }

        JsonNode quantityNode = hasAny(summary,
                "displayStockInfo", "normalizedPallets", "normalizedLoosePieces", "totalEquivalentPieces", "totalWeight")
                ? summary
                : firstRecord;
        String display = firstText(quantityNode, "displayStockInfo", "stockInfo");
        Integer normalizedPallets = nullableInt(quantityNode.path("normalizedPallets"));
        Integer normalizedLoosePieces = nullableInt(quantityNode.path("normalizedLoosePieces"));
        Integer totalEquivalentPieces = nullableInt(quantityNode.path("totalEquivalentPieces"));
        String weight = firstText(quantityNode, "totalWeight");
        String calculationNote = firstText(quantityNode, "calculationNote");

        if (display != null) {
            return query + " 当前库存：" + display + appendTotalPieces(totalEquivalentPieces) + appendWeight(weight) + appendCalculationNote(calculationNote) + "。";
        }
        if (normalizedPallets != null && normalizedLoosePieces != null) {
            return query + " 当前库存：" + normalizedPallets + "板" + normalizedLoosePieces + "件" + appendTotalPieces(totalEquivalentPieces) + appendWeight(weight) + appendCalculationNote(calculationNote) + "。";
        }
        if (totalEquivalentPieces != null) {
            return query + " 当前库存折合总件数 " + totalEquivalentPieces + appendWeight(weight) + appendCalculationNote(calculationNote) + "。";
        }
        return query + " 的库存已查询到，但返回字段不足以生成可靠摘要，请联系管理员开启调试信息排查。";
    }

    private String formatInventoryLocationAnswer(String query, JsonNode node) {
        if (isToolNotFound(node)) {
            return "未查询到 " + query + " 的库存记录，因此无法判断存放库位。";
        }
        JsonNode records = node == null ? null : node.path("records");
        if (records == null || !records.isArray() || records.isEmpty()) {
            String stockSummary = formatInventoryAnswer(query, node, false);
            return stockSummary + " 当前返回结果没有包含库位明细，暂时无法判断主要存放在哪些库位。";
        }
        List<String> locations = new ArrayList<>();
        for (JsonNode record : records) {
            if (locations.size() >= 5) {
                break;
            }
            String warehouse = firstText(record, "warehouseName", "warehouse", "location", "currentWarehouseName");
            if (warehouse == null) {
                continue;
            }
            String stock = firstText(record, "displayStockInfo", "stockInfo");
            String weight = firstText(record, "totalWeight", "weight");
            StringBuilder item = new StringBuilder(warehouse);
            if (stock != null) {
                item.append(" ").append(stock);
            }
            if (weight != null) {
                item.append("，重量 ").append(weight);
            }
            locations.add(item.toString());
        }
        if (locations.isEmpty()) {
            String stockSummary = formatInventoryAnswer(query, node, false);
            return stockSummary + " 当前返回结果没有包含可读的库位名称，暂时无法判断主要存放在哪些库位。";
        }
        int total = records.size();
        String suffix = total > locations.size() ? "；其余 " + (total - locations.size()) + " 条明细未展开" : "";
        return query + " 目前主要存放在：" + String.join("；", locations) + suffix + "。";
    }
    private String formatWarehouseAnswer(String query, JsonNode node) {
        String name = firstText(node, "warehouseName", "name");
        String status = firstText(node, "status", "warehouseStatus");
        String capacity = firstText(node, "capacityStatus", "usageText", "freeCapacity");
        StringBuilder builder = new StringBuilder();
        builder.append(defaultText(name, query)).append(" 当前状态");
        if (status != null) {
            builder.append("：").append(status);
        }
        if (capacity != null) {
            builder.append("，容量/占用：").append(capacity);
        }
        builder.append("。");
        return builder.toString();
    }

    private String formatPalletAnswer(String code, JsonNode node) {
        if (isToolNotFound(node)) {
            return "未找到托盘码 " + code + " 的记录。";
        }
        String status = firstText(node, "status", "codeStatus", "palletStatus");
        String warehouse = firstText(node, "warehouseName", "currentWarehouseName", "location");
        boolean partial = node.path("partial").asBoolean(false);
        StringBuilder builder = new StringBuilder();
        builder.append("托盘 ").append(code).append(" 状态已查询");
        if (status != null) {
            builder.append("：").append(status);
        }
        if (warehouse != null) {
            builder.append("，当前位置：").append(warehouse);
        }
        if (partial) {
            builder.append("。部分子查询失败，请查看 warnings。");
        } else {
            builder.append("。");
        }
        return builder.toString();
    }

    private String formatAssayAnswer(String query, String productionDate, JsonNode node) {
        if (node.path("needsAssay").asBoolean(false) || STATUS_NOT_FOUND.equals(node.path("resolutionStatus").asText(null)) || isToolNotFound(node)) {
            return query + " 在 " + productionDate + " 未查询到化验记录，需要化验或补录后才能判断合格状态。";
        }
        String judge = firstText(node, "judgeResult", "result", "qualityResult");
        if (judge == null) {
            return query + " 在 " + productionDate + " 的化验信息已查询到，但未返回明确判定结果。";
        }
        return query + " 在 " + productionDate + " 的化验判定为：" + judge + "。";
    }

    private boolean isToolNotFound(JsonNode node) {
        String code = node == null ? null : firstText(node, "code", "errorCode");
        return Objects.equals(code, "NOT_FOUND") || Objects.equals(code, "UPSTREAM_NOT_FOUND");
    }


    private boolean isEmptyInventory(JsonNode root, JsonNode summary, JsonNode firstRecord) {
        Integer totalRecords = nullableInt(summary.path("totalRecords"));
        if (totalRecords != null && totalRecords == 0) {
            return true;
        }
        JsonNode records = root == null ? null : root.path("records");
        return (records == null || records.isMissingNode() || (records.isArray() && records.isEmpty()))
                && !hasAny(summary, "displayStockInfo", "normalizedPallets", "totalEquivalentPieces")
                && (firstRecord == null || firstRecord.isMissingNode() || firstRecord.isNull());
    }

    private JsonNode childObject(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return objectMapper.createObjectNode();
        }
        JsonNode child = node.path(field);
        return child.isObject() ? child : objectMapper.createObjectNode();
    }

    private JsonNode firstArrayElement(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return objectMapper.createObjectNode();
        }
        JsonNode array = node.path(field);
        return array.isArray() && !array.isEmpty() ? array.get(0) : objectMapper.createObjectNode();
    }

    private boolean hasAny(JsonNode node, String... fields) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return false;
        }
        for (String field : fields) {
            JsonNode value = node.path(field);
            if (!value.isMissingNode() && !value.isNull()) {
                if (value.isTextual() && value.asText().isBlank()) {
                    continue;
                }
                return true;
            }
        }
        return false;
    }
    private String appendTotalPieces(Integer totalEquivalentPieces) {
        return totalEquivalentPieces == null ? "" : "，折合总件数 " + totalEquivalentPieces;
    }

    private String appendWeight(String weight) {
        return weight == null ? "" : "，总重量 " + weight;
    }


    private String appendCalculationNote(String calculationNote) {
        return "";
    }
    private Integer positiveInt(Object value) {
        if (value instanceof Number number && number.intValue() > 0) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                int parsed = Integer.parseInt(text);
                return parsed > 0 ? parsed : null;
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
    private Integer nullableInt(JsonNode node) {
        return node != null && node.canConvertToInt() ? node.asInt() : null;
    }

    private String firstText(JsonNode node, String... fields) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        for (String field : fields) {
            JsonNode value = node.path(field);
            if (!value.isMissingNode() && !value.isNull()) {
                String text = value.isTextual() ? value.asText() : value.toString();
                if (text != null && !text.isBlank()) {
                    return text;
                }
            }
        }
        return null;
    }

    private boolean containsAny(String text, String... keywords) {
        if (text == null) {
            return false;
        }
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
    private String normalizeQuery(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String safeBusinessLabel(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.replaceAll("\\s*[(（]#\\d+[)）]\\s*", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return cleaned.isBlank() ? null : cleaned;
    }

    private String toSafeSummary(Object value) {
        try {
            String json = value instanceof String text ? text : objectMapper.writeValueAsString(value);
            return sanitize(limit(json, 1000));
        } catch (JsonProcessingException e) {
            return "<summary unavailable>";
        }
    }

    private Map<String, Object> summarizeResult(JsonNode result) {
        Map<String, Object> summary = new LinkedHashMap<>();
        if (result == null || result.isNull() || result.isMissingNode()) {
            summary.put("empty", true);
            return summary;
        }
        for (String field : List.of("resolutionStatus", "needsUserSelection", "needsAssay", "partial", "code", "severity")) {
            if (result.has(field)) {
                JsonNode value = result.get(field);
                summary.put(field, value.isValueNode() ? value.asText() : value.toString());
            }
        }
        if (result.path("candidates").isArray()) {
            summary.put("candidateCount", result.path("candidates").size());
        }
        if (result.path("options").isArray()) {
            summary.put("optionCount", result.path("options").size());
        }
        return summary;
    }

    private String sanitize(String value) {
        if (value == null) {
            return null;
        }
        return value
                .replaceAll("(?i)authorization\\s*[:=]\\s*bearer\\s+[^\\s,;]+", "Authorization: <redacted>")
                .replaceAll("(?i)bearer\\s+[^\\s,;]+", "Bearer <redacted>")
                .replaceAll("(?i)(token|api[_-]?key|password|secret)\\s*[:=]\\s*[^\\s,;]+", "$1=<redacted>")
                .replaceAll("(?i)jdbc:[^\\s,;]+", "jdbc:<redacted>")
                .replaceAll("(?m)^\\s*at\\s+.+$", "<stack redacted>");
    }

    private String limit(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}













