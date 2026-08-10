package com.Laibin.SugarInventory.agent;

import com.Laibin.SugarInventory.SpringSecurity.LoginUser;
import com.Laibin.SugarInventory.agent.dto.AgentToolAuditDTO;
import com.Laibin.SugarInventory.agent.internal.dto.InternalAgentToolClientDTO;
import com.Laibin.SugarInventory.agent.internal.dto.InternalAgentToolRequestDTO;
import com.Laibin.SugarInventory.agent.internal.service.impl.McpInternalAgentToolGatewayService;
import com.Laibin.SugarInventory.agent.internal.vo.InternalAgentToolResponseVO;
import com.Laibin.SugarInventory.agent.mcp.McpSession;
import com.Laibin.SugarInventory.agent.mcp.McpSessionManager;
import com.Laibin.SugarInventory.agent.mcp.McpToolCall;
import com.Laibin.SugarInventory.agent.mcp.McpToolResult;
import com.Laibin.SugarInventory.agent.security.AgentSessionAuthenticationException;
import com.Laibin.SugarInventory.agent.service.AgentSessionService;
import com.Laibin.SugarInventory.agent.service.InternalAgentSessionAccess;
import com.Laibin.SugarInventory.domain.po.AgentSession;
import com.Laibin.SugarInventory.domain.po.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InternalAgentToolGatewayServiceTest {
    private static final String SERVICE_KEY = "internal-test-key";

    private AgentSessionService agentSessionService;
    private McpSessionManager mcpSessionManager;
    private McpSession mcpSession;
    private ObjectMapper objectMapper;
    private McpInternalAgentToolGatewayService gateway;
    private AgentSession session;
    private LoginUser loginUser;

    @BeforeEach
    void setUp() {
        agentSessionService = mock(AgentSessionService.class);
        mcpSessionManager = mock(McpSessionManager.class);
        mcpSession = mock(McpSession.class);
        objectMapper = new ObjectMapper();
        gateway = new McpInternalAgentToolGatewayService(
                agentSessionService,
                mcpSessionManager,
                objectMapper,
                SERVICE_KEY,
                16_384);

        User user = new User();
        user.setId(7);
        user.setName("测试用户");
        user.setRoleCode("ADMIN");
        loginUser = new LoginUser(user, List.of(new SimpleGrantedAuthority("record:query")));

        session = new AgentSession();
        session.setId("agt_001");
        session.setUserId(7);
        session.setStatus("ACTIVE");
        session.setScopes(AgentSessionService.SCOPE_WAREHOUSE_READ);
        session.setExpiresAt(LocalDateTime.now().plusMinutes(10));

        when(agentSessionService.requireActiveInternalToolSession("agt_001"))
                .thenReturn(new InternalAgentSessionAccess(session, loginUser));
        when(mcpSessionManager.bindSession(loginUser, session)).thenReturn(mcpSession);
        when(agentSessionService.recordToolAudit(anyString(), anyInt(), any(AgentToolAuditDTO.class)))
                .thenReturn("audit_001");
    }

    @Test
    void allowsWhitelistedResolveProductsAndRecordsCorrelatedAudit() {
        when(mcpSession.callTool(any())).thenReturn(successResult("resolve_products"));

        InternalAgentToolResponseVO response = gateway.invoke(
                SERVICE_KEY,
                "resolve_products",
                request(Map.of("query", "黄冰糖", "limit", 10)));

        assertThat(response.getStatus()).isEqualTo("SUCCESS");
        assertThat(response.getToolName()).isEqualTo("resolve_products");
        assertThat(response.getToolCallId()).isEqualTo("tool_001");
        assertThat(response.getAuditRef()).isEqualTo("audit_001");
        assertThat(response.getResult().path("resolutionStatus").asText()).isEqualTo("UNIQUE");

        ArgumentCaptor<McpToolCall> callCaptor = ArgumentCaptor.forClass(McpToolCall.class);
        verify(mcpSession).callTool(callCaptor.capture());
        assertThat(callCaptor.getValue().toolName()).isEqualTo("resolve_products");
        assertThat(callCaptor.getValue().arguments()).containsEntry("query", "黄冰糖");

        ArgumentCaptor<AgentToolAuditDTO> auditCaptor = ArgumentCaptor.forClass(AgentToolAuditDTO.class);
        verify(agentSessionService).recordToolAudit(anyString(), anyInt(), auditCaptor.capture());
        AgentToolAuditDTO audit = auditCaptor.getValue();
        assertThat(audit.getToolName()).isEqualTo("resolve_products");
        assertThat(audit.getToolCallId()).isEqualTo("tool_001");
        assertThat(audit.getMessageId()).isEqualTo("msg_001");
        assertThat(audit.getResultCode()).isEqualTo("SUCCESS");
        assertThat(audit.getErrorCode()).isNull();
        assertThat(audit.getDurationMs()).isNotNegative();
        assertThat(audit.getRequestSummary()).contains(
                "inventory_expert", "inventory", "delegate", "trace_001", "req_001");
    }

    @Test
    void rejectsSqlPreviewAndExecuteToolsBeforeBindingMcpSession() {
        for (String toolName : List.of("execute_sql", "preview_inbound_plan", "execute_inbound_plan")) {
            InternalAgentToolResponseVO response = gateway.invoke(SERVICE_KEY, toolName, request(Map.of()));

            assertThat(response.getStatus()).isEqualTo("ERROR");
            assertThat(response.getError().getCode()).isEqualTo("TOOL_NOT_ALLOWED");
        }

        verify(mcpSessionManager, never()).bindSession(any(), any());
        verify(mcpSession, never()).callTool(any());
    }

    @Test
    void rejectsMissingAndRevokedSessionsAsInvalid() {
        doThrow(new AgentSessionAuthenticationException(401, "AGENT_SESSION_NOT_FOUND", "internal detail"))
                .when(agentSessionService).requireActiveInternalToolSession("agt_001");

        InternalAgentToolResponseVO missing = gateway.invoke(
                SERVICE_KEY, "resolve_products", request(Map.of("query", "黄冰糖")));

        assertThat(missing.getStatus()).isEqualTo("ERROR");
        assertThat(missing.getError().getCode()).isEqualTo("AGENT_SESSION_NOT_FOUND");
        assertThat(missing.getError().getMessage()).doesNotContain("internal detail");

        doThrow(new AgentSessionAuthenticationException(401, "AGENT_SESSION_INACTIVE", "revoked detail"))
                .when(agentSessionService).requireActiveInternalToolSession("agt_001");

        InternalAgentToolResponseVO revoked = gateway.invoke(
                SERVICE_KEY, "resolve_products", request(Map.of("query", "黄冰糖")));

        assertThat(revoked.getStatus()).isEqualTo("ERROR");
        assertThat(revoked.getError().getCode()).isEqualTo("AGENT_SESSION_INACTIVE");
        verify(mcpSessionManager, never()).bindSession(any(), any());
    }

    @Test
    void rejectsMissingOrWrongInternalServiceKey() {
        InternalAgentToolResponseVO missing = gateway.invoke(null, "resolve_products", request(Map.of("query", "黄冰糖")));
        InternalAgentToolResponseVO wrong = gateway.invoke("wrong-key", "resolve_products", request(Map.of("query", "黄冰糖")));

        assertThat(missing.getError().getCode()).isEqualTo("SERVICE_AUTHENTICATION_FAILED");
        assertThat(wrong.getError().getCode()).isEqualTo("SERVICE_AUTHENTICATION_FAILED");
        verify(agentSessionService, never()).requireActiveInternalToolSession(anyString());
    }

    @Test
    void rejectsOversizedArgumentsBeforeSessionLookup() {
        McpInternalAgentToolGatewayService smallGateway = new McpInternalAgentToolGatewayService(
                agentSessionService, mcpSessionManager, objectMapper, SERVICE_KEY, 1024);

        InternalAgentToolResponseVO response = smallGateway.invoke(
                SERVICE_KEY,
                "resolve_products",
                request(Map.of("query", "黄".repeat(2048))));

        assertThat(response.getStatus()).isEqualTo("ERROR");
        assertThat(response.getError().getCode()).isEqualTo("ARGUMENTS_TOO_LARGE");
        verify(agentSessionService, never()).requireActiveInternalToolSession(anyString());
    }

    @Test
    void removesSensitiveFieldsAndValuesFromToolResponse() throws Exception {
        ObjectNode result = objectMapper.createObjectNode();
        result.put("resolutionStatus", "UNIQUE");
        result.put("delegationToken", "delegated-secret");
        result.put("Authorization", "Bearer secret-value");
        result.put("password", "password-value");
        result.put("diagnostic", "jdbc:mysql://localhost/db");
        result.put("stack", "java.lang.IllegalStateException\n at internal.Server.run(Server.java:1)");
        when(mcpSession.callTool(any())).thenReturn(new McpToolResult(
                "resolve_products", result, "SUCCESS", null, 5L));

        InternalAgentToolResponseVO response = gateway.invoke(
                SERVICE_KEY, "resolve_products", request(Map.of("query", "黄冰糖")));
        String json = objectMapper.writeValueAsString(response);

        assertThat(json).doesNotContain(
                "delegated-secret",
                "secret-value",
                "password-value",
                "jdbc:mysql",
                "internal.Server.run",
                "delegationToken",
                "Authorization",
                "password");
    }

    @Test
    void whitelistContainsExactlyFiftyFourReadOrPreviewToolsAndNeverDispatchesUnknownPreviewOrWriteToolNames() {
        Set<String> expected = Set.of(
                "resolve_products",
                "resolve_warehouses",
                "get_inventory_overview",
                "get_inventory_distribution",
                "query_assay_records",
                "get_assay_report_detail",
                "query_assay_abnormalities",
                "query_products_without_recent_assay",
                "query_assay_standard_coverage",
                "query_unqualified_inventory",
                "query_inventory_by_quality_standard",
                "query_inventory_by_assay_metrics",
                "query_qr_code_lifecycle",
                "query_printed_not_inbound_codes",
                "query_pallet_anomalies",
                "query_pallet_flow_records",
                "query_qr_batch_inbound_completion",
                "resolve_production_entities",
                "query_boiling_batches",
                "query_production_order_progress",
                "query_boiling_batch_trace",
                "query_material_pick_trace",
                "query_production_label_completion",
                "query_in_process_materials",
                "query_material_candidates",
                "query_pallet_tasks",
                "preview_task_transition",
                "preview_finish_inbound_execution",
                "query_stock_documents",
                "query_auto_inbound_batches",
                "get_auto_inbound_batch_detail",
                "query_warehouse_capacity_distribution",
                "query_warehouse_recent_operations",
                "query_warehouse_mixed_storage_facts",
                "query_product_catalog",
                "get_product_detail",
                "query_screen_mesh_catalog",
                "query_assay_groups",
                "query_quality_standard_catalog",
                "get_quality_standard_detail",
                "query_product_standard_relations",
                "query_product_quality_configuration",
                "query_employee_roster",
                "query_roles",
                "get_role_permission_summary",
                "search_operation_logs",
                "query_agent_tool_audit",
                "query_agent_answer_reviews",
                "query_inventory_ledger",
                "query_fixed_product_qr_pool",
                "run_registered_report",
                "get_warehouse_status",
                "get_pallet_status",
                "get_assay_status");
        assertThat(McpInternalAgentToolGatewayService.allowedTools()).containsExactlyInAnyOrderElementsOf(expected);

        when(mcpSession.callTool(any())).thenAnswer(invocation -> {
            McpToolCall call = invocation.getArgument(0);
            return successResult(call.toolName());
        });

        for (String toolName : expected) {
            String expertAgent = McpInternalAgentToolGatewayService.expertAllowedTools().entrySet().stream()
                    .filter(entry -> entry.getValue().contains(toolName))
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .orElseThrow();
            InternalAgentToolRequestDTO toolRequest = request(Map.of());
            toolRequest.getClient().setExpertAgent(expertAgent);
            InternalAgentToolResponseVO response = gateway.invoke(SERVICE_KEY, toolName, toolRequest);
            assertThat(response.getStatus()).isEqualTo("SUCCESS");
        }

        ArgumentCaptor<McpToolCall> callCaptor = ArgumentCaptor.forClass(McpToolCall.class);
        verify(mcpSession, times(expected.size())).callTool(callCaptor.capture());
        assertThat(callCaptor.getAllValues())
                .extracting(McpToolCall::toolName)
                .containsExactlyInAnyOrderElementsOf(expected)
                .noneMatch(name -> (name.startsWith("preview_")
                        && !Set.of("preview_task_transition", "preview_finish_inbound_execution").contains(name))
                        || name.startsWith("execute_")
                        || name.contains("sql")
                        || name.contains("http"));
    }

    @Test
    void rejectsCrossExpertMainAgentUnknownExpertAndMissingExpertBeforeMcpDispatch() {
        InternalAgentToolRequestDTO crossExpert = request(Map.of("query", "黄冰糖"));
        crossExpert.getClient().setExpertAgent("warehouse_expert");
        assertThat(gateway.invoke(SERVICE_KEY, "resolve_products", crossExpert).getError().getCode())
                .isEqualTo("EXPERT_TOOL_NOT_ALLOWED");

        InternalAgentToolRequestDTO mainAgent = request(Map.of("query", "黄冰糖"));
        mainAgent.getClient().setExpertAgent("main_agent");
        assertThat(gateway.invoke(SERVICE_KEY, "resolve_products", mainAgent).getError().getCode())
                .isEqualTo("EXPERT_TOOL_NOT_ALLOWED");

        InternalAgentToolRequestDTO unknownExpert = request(Map.of("query", "黄冰糖"));
        unknownExpert.getClient().setExpertAgent("unknown_expert");
        assertThat(gateway.invoke(SERVICE_KEY, "resolve_products", unknownExpert).getError().getCode())
                .isEqualTo("EXPERT_TOOL_NOT_ALLOWED");

        InternalAgentToolRequestDTO missingExpert = request(Map.of("query", "黄冰糖"));
        missingExpert.getClient().setExpertAgent(null);
        assertThat(gateway.invoke(SERVICE_KEY, "resolve_products", missingExpert).getError().getCode())
                .isEqualTo("INVALID_ARGUMENT");

        verify(mcpSession, never()).callTool(any());
    }

    @Test
    void doesNotExposeDelegationOrAuthorizationInNormalErrorResponse() throws Exception {
        when(mcpSession.callTool(any())).thenReturn(new McpToolResult(
                "resolve_products",
                objectMapper.createObjectNode().put("message", "Authorization: Bearer hidden-token"),
                "ERROR",
                "MCP_CALL_FAILED",
                3L));

        InternalAgentToolResponseVO response = gateway.invoke(
                SERVICE_KEY, "resolve_products", request(Map.of("query", "黄冰糖")));
        String json = objectMapper.writeValueAsString(response);

        assertThat(response.getStatus()).isEqualTo("ERROR");
        assertThat(json).doesNotContain("hidden-token", "Bearer hidden-token", "stackTrace", "delegationToken");
    }

    @Test
    void auditsMcpToolErrorResultAsErrorNotEmptySuccess() {
        ObjectNode result = objectMapper.createObjectNode();
        result.put("code", "MCP_TOOL_ERROR");
        result.put("message", "Conversion from JSON to ProductScope failed");
        result.put("isError", true);
        when(mcpSession.callTool(any())).thenReturn(new McpToolResult(
                "get_inventory_distribution", result, "ERROR", "MCP_TOOL_ERROR", 4L));

        InternalAgentToolResponseVO response = gateway.invoke(
                SERVICE_KEY,
                "get_inventory_distribution",
                request(Map.of(
                        "productScope", Map.of("type", "ALL"),
                        "warehouseScope", Map.of("type", "SINGLE_WAREHOUSE", "warehouseId", 8),
                        "groupBy", "product")));

        assertThat(response.getStatus()).isEqualTo("ERROR");
        assertThat(response.getError().getCode()).isEqualTo("MCP_TOOL_ERROR");

        ArgumentCaptor<AgentToolAuditDTO> auditCaptor = ArgumentCaptor.forClass(AgentToolAuditDTO.class);
        verify(agentSessionService).recordToolAudit(anyString(), anyInt(), auditCaptor.capture());
        AgentToolAuditDTO audit = auditCaptor.getValue();
        assertThat(audit.getResultCode()).isEqualTo("ERROR");
        assertThat(audit.getErrorCode()).isEqualTo("MCP_TOOL_ERROR");
        assertThat(audit.getResponseSummary()).contains("MCP_TOOL_ERROR", "isError");
        assertThat(audit.getResponseSummary()).doesNotContain("stackTrace", "Authorization", "Bearer");
    }

    private InternalAgentToolRequestDTO request(Map<String, Object> arguments) {
        InternalAgentToolClientDTO client = new InternalAgentToolClientDTO();
        client.setTraceId("trace_001");
        client.setRequestId("req_001");
        client.setExpertAgent("inventory_expert");
        client.setBusinessDomain("inventory");
        client.setHandoffMode("delegate");

        InternalAgentToolRequestDTO request = new InternalAgentToolRequestDTO();
        request.setAgentSessionId("agt_001");
        request.setToolCallId("tool_001");
        request.setMessageId("msg_001");
        request.setArguments(new LinkedHashMap<>(arguments));
        request.setClient(client);
        return request;
    }

    private McpToolResult successResult(String toolName) {
        ObjectNode result = objectMapper.createObjectNode();
        result.put("resolutionStatus", "UNIQUE");
        return new McpToolResult(toolName, result, "SUCCESS", null, 12L);
    }
}
