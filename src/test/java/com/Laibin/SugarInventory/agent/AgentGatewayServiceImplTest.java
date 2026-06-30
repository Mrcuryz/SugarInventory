package com.Laibin.SugarInventory.agent;

import com.Laibin.SugarInventory.SpringSecurity.LoginUser;
import com.Laibin.SugarInventory.agent.context.AgentConversationMemory;
import com.Laibin.SugarInventory.agent.dto.AgentMessageRequestDTO;
import com.Laibin.SugarInventory.agent.gateway.AgentGatewayServiceImpl;
import com.Laibin.SugarInventory.agent.mcp.McpSession;
import com.Laibin.SugarInventory.agent.mcp.McpSessionManager;
import com.Laibin.SugarInventory.agent.mcp.McpToolCall;
import com.Laibin.SugarInventory.agent.mcp.McpToolResult;
import com.Laibin.SugarInventory.agent.model.AgentIntent;
import com.Laibin.SugarInventory.agent.model.AgentModelClient;
import com.Laibin.SugarInventory.agent.model.AgentPlan;
import com.Laibin.SugarInventory.agent.service.AgentSessionService;
import com.Laibin.SugarInventory.agent.vo.AgentMessageResponseVO;
import com.Laibin.SugarInventory.agent.vo.AgentSessionVO;
import com.Laibin.SugarInventory.domain.po.AgentSession;
import com.Laibin.SugarInventory.domain.po.User;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentGatewayServiceImplTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private AgentSessionService sessionService;
    private McpSessionManager mcpSessionManager;
    private AgentModelClient modelClient;
    private McpSession mcpSession;
    private AgentGatewayServiceImpl gateway;
    private AgentConversationMemory conversationMemory;
    private LoginUser loginUser;
    private AgentSession session;
    private AgentSessionVO sessionVO;

    @BeforeEach
    void setUp() {
        sessionService = mock(AgentSessionService.class);
        mcpSessionManager = mock(McpSessionManager.class);
        modelClient = mock(AgentModelClient.class);
        mcpSession = mock(McpSession.class);
        conversationMemory = new AgentConversationMemory();
        gateway = new AgentGatewayServiceImpl(sessionService, mcpSessionManager, modelClient, objectMapper, conversationMemory);

        User user = new User();
        user.setId(7);
        user.setName("测试用户");
        user.setRoleCode("ADMIN");
        loginUser = new LoginUser(user, List.of(new SimpleGrantedAuthority("record:query")));

        session = new AgentSession();
        session.setId("session-1");
        session.setUserId(7);
        session.setStatus("ACTIVE");
        session.setExpiresAt(LocalDateTime.now().plusMinutes(10));

        sessionVO = new AgentSessionVO();
        sessionVO.setAgentSessionId("session-1");
        sessionVO.setUserId(7);
        sessionVO.setStatus("ACTIVE");

        when(sessionService.requireOwnedActiveSession(loginUser, "session-1")).thenReturn(session);
        when(sessionService.toSessionVO(session, loginUser)).thenReturn(sessionVO);
        when(mcpSessionManager.bindSession(loginUser, session)).thenReturn(mcpSession);
    }

    @Test
    void resolveProductsAmbiguousReturnsUserSelectionAndDoesNotQueryInventory() throws Exception {
        AgentPlan plan = new AgentPlan();
        plan.setIntent(AgentIntent.INVENTORY_OVERVIEW);
        plan.setEntityQuery("黄冰糖");
        when(modelClient.plan(any(), any())).thenReturn(plan);
        when(mcpSession.callTool(any())).thenReturn(result("resolve_products", """
                {"resolutionStatus":"AMBIGUOUS","needsUserSelection":true,"clarificationPrompt":"请选择黄冰糖范围","options":[{"optionType":"PRODUCT_TYPE_GROUP","displayLabel":"全部黄冰糖","supported":false},{"optionType":"SINGLE_PRODUCT","displayLabel":"黄冰糖（袋）","productId":84,"supported":true}]}
                """));

        AgentMessageResponseVO response = gateway.handleMessage(loginUser, "session-1", request("查黄冰糖库存"));

        assertThat(response.isNeedsUserSelection()).isTrue();
        assertThat(response.getAnswer()).contains("请选择黄冰糖范围");
        assertThat(response.getOptions()).hasSize(2);
        verify(mcpSession, times(1)).callTool(any(McpToolCall.class));
        verify(sessionService, times(1)).recordToolAudit(any(), any(), any());
    }

    @Test
    void normalizedWarehouseUniqueContinuesToWarehouseStatus() throws Exception {
        AgentPlan plan = new AgentPlan();
        plan.setIntent(AgentIntent.WAREHOUSE_STATUS);
        plan.setEntityQuery("2号库位容量");
        when(modelClient.plan(any(), any())).thenReturn(plan);
        when(mcpSession.callTool(any())).thenAnswer(invocation -> {
            McpToolCall call = invocation.getArgument(0);
            if ("resolve_warehouses".equals(call.toolName())) {
                return result(call.toolName(), """
                        {"resolutionStatus":"UNIQUE","needsUserSelection":false,"candidates":[{"warehouseId":2,"warehouseName":"2","matchType":"NORMALIZED_NAME"}]}
                        """);
            }
            return result(call.toolName(), """
                    {"warehouseName":"2","status":"正常","capacityStatus":"有库存"}
                    """);
        });

        AgentMessageResponseVO response = gateway.handleMessage(loginUser, "session-1", request("2号库位现在还有多少容量？"));

        assertThat(response.isNeedsUserSelection()).isFalse();
        assertThat(response.getAnswer()).contains("2").contains("正常");
        ArgumentCaptor<McpToolCall> captor = ArgumentCaptor.forClass(McpToolCall.class);
        verify(mcpSession, times(2)).callTool(captor.capture());
        assertThat(captor.getAllValues()).extracting(McpToolCall::toolName)
                .containsExactly("resolve_warehouses", "get_warehouse_status");
        assertThat(captor.getAllValues().get(0).arguments()).containsEntry("query", "2号库位");
    }

    @Test
    void upstreamUnauthorizedIsNotExplainedAsEmptyInventory() throws Exception {
        AgentPlan plan = new AgentPlan();
        plan.setIntent(AgentIntent.INVENTORY_OVERVIEW);
        plan.setEntityQuery("黄冰糖（袋）");
        when(modelClient.plan(any(), any())).thenReturn(plan);
        when(mcpSession.callTool(any())).thenReturn(result("resolve_products", """
                {"code":"UPSTREAM_UNAUTHORIZED","message":"Authentication failed.","severity":"ERROR","retryable":false,"suggestedActions":["重新授权"],"upstreamStatus":401}
                """));

        AgentMessageResponseVO response = gateway.handleMessage(loginUser, "session-1", request("查黄冰糖（袋）库存"));

        assertThat(response.getAnswer()).contains("权限");
        assertThat(response.getAnswer()).doesNotContain("无库存", "空库存");
        assertThat(response.getToolCalls()).first().extracting("errorCode").isEqualTo("UPSTREAM_UNAUTHORIZED");
        verify(mcpSession, times(1)).callTool(any(McpToolCall.class));
    }

    @Test
    void nonAdminResponseDoesNotExposeToolCalls() throws Exception {
        User user = new User();
        user.setId(8);
        user.setName("普通用户");
        user.setRoleCode("USER");
        LoginUser normalUser = new LoginUser(user, List.of(new SimpleGrantedAuthority("record:query")));
        AgentSession normalSession = new AgentSession();
        normalSession.setId("session-2");
        normalSession.setUserId(8);
        normalSession.setStatus("ACTIVE");
        normalSession.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        AgentSessionVO normalVO = new AgentSessionVO();
        normalVO.setAgentSessionId("session-2");
        normalVO.setUserId(8);
        normalVO.setRoleCode("USER");
        normalVO.setStatus("ACTIVE");
        when(sessionService.requireOwnedActiveSession(normalUser, "session-2")).thenReturn(normalSession);
        when(sessionService.toSessionVO(normalSession, normalUser)).thenReturn(normalVO);
        when(mcpSessionManager.bindSession(normalUser, normalSession)).thenReturn(mcpSession);

        AgentPlan plan = new AgentPlan();
        plan.setIntent(AgentIntent.INVENTORY_OVERVIEW);
        plan.setEntityQuery("黄冰糖（袋）");
        when(modelClient.plan(any(), any())).thenReturn(plan);
        when(mcpSession.callTool(any())).thenAnswer(invocation -> {
            McpToolCall call = invocation.getArgument(0);
            if ("resolve_products".equals(call.toolName())) {
                return result(call.toolName(), """
                        {"resolutionStatus":"UNIQUE","needsUserSelection":false,"candidates":[{"productId":84,"productName":"黄冰糖（袋）"}]}
                        """);
            }
            return result(call.toolName(), """
                    {"pageInfo":{"page":1,"size":10,"total":1},"summary":{"totalRecords":1,"displayStockInfo":"11板30件","totalEquivalentPieces":470},"records":[{"productId":84,"productName":"黄冰糖（袋）","displayStockInfo":"11板30件"}]}
                    """);
        });

        AgentMessageResponseVO response = gateway.handleMessage(normalUser, "session-2", request("查黄冰糖（袋）库存"));

        assertThat(response.getAnswer()).contains("11板30件");
        assertThat(response.getToolCalls()).isEmpty();
    }
    @Test
    void unsupportedMessageDoesNotBindMcpProcess() {
        AgentPlan plan = new AgentPlan();
        plan.setIntent(AgentIntent.UNSUPPORTED);
        when(modelClient.plan(any(), any())).thenReturn(plan);

        AgentMessageResponseVO response = gateway.handleMessage(loginUser, "session-1", request("帮我入库"));

        assertThat(response.getAnswer()).contains("只支持");
        verify(mcpSessionManager, never()).bindSession(any(), any());
        verify(mcpSession, never()).callTool(any());
    }


    @Test
    void inventoryOverviewReadsNestedSummary() throws Exception {
        AgentPlan plan = new AgentPlan();
        plan.setIntent(AgentIntent.INVENTORY_OVERVIEW);
        plan.setEntityQuery("黄冰糖（袋）");
        when(modelClient.plan(any(), any())).thenReturn(plan);
        when(mcpSession.callTool(any())).thenAnswer(invocation -> {
            McpToolCall call = invocation.getArgument(0);
            if ("resolve_products".equals(call.toolName())) {
                return result(call.toolName(), """
                        {"resolutionStatus":"UNIQUE","needsUserSelection":false,"candidates":[{"productId":84,"productName":"黄冰糖（袋）"}]}
                        """);
            }
            return result(call.toolName(), """
                    {"pageInfo":{"page":1,"size":10,"total":1},"summary":{"totalRecords":1,"rawFullPallets":10,"rawLoosePieces":70,"normalizedPallets":11,"normalizedLoosePieces":30,"totalEquivalentPieces":470,"displayStockInfo":"11板30件","totalWeight":11750.0},"records":[{"productId":84,"productName":"黄冰糖（袋）","displayStockInfo":"11板30件"}]}
                    """);
        });

        AgentMessageResponseVO response = gateway.handleMessage(loginUser, "session-1", request("查黄冰糖（袋）库存"));

        assertThat(response.getAnswer()).contains("11板30件", "折合总件数 470", "总重量 11750.0");
        assertThat(response.getAnswer()).doesNotContain("字段不足");
    }

    @Test
    void unsupportedCanUseModelAssistantReply() {
        AgentPlan plan = new AgentPlan();
        plan.setIntent(AgentIntent.UNSUPPORTED);
        plan.getHints().put("assistantReply", "我是智能仓储 AI 助手，可以帮你查询库存、库位、托盘和化验状态。");
        when(modelClient.plan(any(), any())).thenReturn(plan);

        AgentMessageResponseVO response = gateway.handleMessage(loginUser, "session-1", request("你是谁"));

        assertThat(response.getAnswer()).contains("智能仓储 AI 助手");
        verify(mcpSessionManager, never()).bindSession(any(), any());
    }
    @Test
    void followUpLocationQuestionUsesLastProductContext() throws Exception {
        AgentPlan firstPlan = new AgentPlan();
        firstPlan.setIntent(AgentIntent.INVENTORY_OVERVIEW);
        firstPlan.setEntityQuery("黄冰糖（袋）");
        AgentPlan followUpPlan = new AgentPlan();
        followUpPlan.setIntent(AgentIntent.WAREHOUSE_STATUS);
        when(modelClient.plan(any(), any())).thenReturn(firstPlan, followUpPlan);
        when(mcpSession.callTool(any())).thenAnswer(invocation -> {
            McpToolCall call = invocation.getArgument(0);
            if ("resolve_products".equals(call.toolName())) {
                return result(call.toolName(), """
                        {"resolutionStatus":"UNIQUE","needsUserSelection":false,"candidates":[{"productId":84,"productName":"黄冰糖（袋）"}]}
                        """);
            }
            return result(call.toolName(), """
                    {"pageInfo":{"page":1,"size":10,"total":2},"summary":{"totalRecords":2,"displayStockInfo":"11板30件","totalEquivalentPieces":470,"totalWeight":11750.0},"records":[{"productId":84,"productName":"黄冰糖（袋）","warehouseName":"2号库位","displayStockInfo":"6板10件","totalWeight":6500.0},{"productId":84,"productName":"黄冰糖（袋）","warehouseName":"3号库位","displayStockInfo":"5板20件","totalWeight":5250.0}]}
                    """);
        });

        gateway.handleMessage(loginUser, "session-1", request("查黄冰糖（袋）库存"));
        AgentMessageResponseVO followUp = gateway.handleMessage(loginUser, "session-1", request("这些糖主要存放在哪个库位？"));

        assertThat(followUp.getAnswer()).contains("黄冰糖（袋）", "2号库位", "3号库位");
        ArgumentCaptor<McpToolCall> captor = ArgumentCaptor.forClass(McpToolCall.class);
        verify(mcpSession, times(3)).callTool(captor.capture());
        assertThat(captor.getAllValues()).extracting(McpToolCall::toolName)
                .containsExactly("resolve_products", "get_inventory_overview", "get_inventory_overview");
    }
    @Test
    void followUpAssayQuestionUsesLastProductContextAndToday() throws Exception {
        AgentPlan firstPlan = new AgentPlan();
        firstPlan.setIntent(AgentIntent.INVENTORY_OVERVIEW);
        firstPlan.setEntityQuery("黄冰糖（袋）");
        AgentPlan followUpPlan = new AgentPlan();
        followUpPlan.setIntent(AgentIntent.UNSUPPORTED);
        when(modelClient.plan(any(), any())).thenReturn(firstPlan, followUpPlan);
        when(mcpSession.callTool(any())).thenAnswer(invocation -> {
            McpToolCall call = invocation.getArgument(0);
            if ("resolve_products".equals(call.toolName())) {
                return result(call.toolName(), """
                        {"resolutionStatus":"UNIQUE","needsUserSelection":false,"candidates":[{"productId":84,"productName":"黄冰糖（袋）"}]}
                        """);
            }
            if ("get_inventory_overview".equals(call.toolName())) {
                return result(call.toolName(), """
                        {"pageInfo":{"page":1,"size":10,"total":1},"summary":{"totalRecords":1,"displayStockInfo":"11板30件","totalEquivalentPieces":470},"records":[{"productId":84,"productName":"黄冰糖（袋）","displayStockInfo":"11板30件"}]}
                        """);
            }
            return result(call.toolName(), """
                    {"needsAssay":false,"judgeResult":"合格","failedMetrics":[]}
                    """);
        });

        gateway.handleMessage(loginUser, "session-1", request("查黄冰糖（袋）库存"));
        AgentMessageResponseVO followUp = gateway.handleMessage(loginUser, "session-1", request("它今天有没有化验？"));

        assertThat(followUp.getAnswer()).contains("黄冰糖（袋）", LocalDate.now().toString(), "合格");
        ArgumentCaptor<McpToolCall> captor = ArgumentCaptor.forClass(McpToolCall.class);
        verify(mcpSession, times(3)).callTool(captor.capture());
        assertThat(captor.getAllValues()).extracting(McpToolCall::toolName)
                .containsExactly("resolve_products", "get_inventory_overview", "get_assay_status");
        assertThat(captor.getAllValues().get(2).arguments())
                .containsEntry("productId", 84)
                .containsEntry("productionDate", LocalDate.now().toString());
    }
    @Test
    void selectedProductOptionUsesContextIdWithoutResolvingDisplayTextAgain() throws Exception {
        AgentPlan plan = new AgentPlan();
        plan.setIntent(AgentIntent.INVENTORY_OVERVIEW);
        plan.setEntityQuery("黄冰糖");
        when(modelClient.plan(any(), any())).thenReturn(plan);
        when(mcpSession.callTool(any())).thenAnswer(invocation -> {
            McpToolCall call = invocation.getArgument(0);
            if ("resolve_products".equals(call.toolName())) {
                return result(call.toolName(), """
                        {"resolutionStatus":"AMBIGUOUS","needsUserSelection":true,"clarificationPrompt":"请选择黄冰糖范围","options":[{"optionType":"SINGLE_PRODUCT","displayLabel":"黄冰糖（袋） (#84) 25.0kg/件 40件/板","productId":84,"supported":true}]}
                        """);
            }
            return result(call.toolName(), """
                    {"pageInfo":{"page":1,"size":10,"total":1},"summary":{"totalRecords":1,"normalizedPallets":11,"normalizedLoosePieces":30,"totalEquivalentPieces":470,"displayStockInfo":"11板30件","totalWeight":11750.0},"records":[{"productId":84,"productName":"黄冰糖（袋）","displayStockInfo":"11板30件"}]}
                    """);
        });

        gateway.handleMessage(loginUser, "session-1", request("当前黄冰糖库存情况如何？"));
        AgentMessageResponseVO selected = gateway.handleMessage(loginUser, "session-1", request("用户选择了候选项", Map.of(
                "path", "/dashboard",
                "selectedOption", Map.of(
                        "optionType", "SINGLE_PRODUCT",
                        "displayLabel", "黄冰糖（袋） 25.0kg/件 40件/板",
                        "rawDisplayLabel", "黄冰糖（袋） (#84) 25.0kg/件 40件/板",
                        "productId", 84
                )
        )));

        assertThat(selected.getAnswer()).contains("11板30件", "折合总件数 470");
        assertThat(selected.getAnswer()).doesNotContain("#84");
        ArgumentCaptor<McpToolCall> captor = ArgumentCaptor.forClass(McpToolCall.class);
        verify(mcpSession, times(2)).callTool(captor.capture());
        assertThat(captor.getAllValues()).extracting(McpToolCall::toolName)
                .containsExactly("resolve_products", "get_inventory_overview");
        assertThat(captor.getAllValues().get(1).arguments()).containsEntry("productId", 84);
        verify(modelClient, times(1)).plan(any(), any());
    }
    private AgentMessageRequestDTO request(String message) {
        return request(message, Map.of("path", "/dashboard"));
    }

    private AgentMessageRequestDTO request(String message, Map<String, Object> pageContext) {
        AgentMessageRequestDTO dto = new AgentMessageRequestDTO();
        dto.setMessage(message);
        dto.setPageContext(pageContext);
        return dto;
    }

    private McpToolResult result(String toolName, String json) throws Exception {
        JsonNode node = objectMapper.readTree(json);
        return new McpToolResult(toolName, node, "SUCCESS", null, 3L);
    }
}






