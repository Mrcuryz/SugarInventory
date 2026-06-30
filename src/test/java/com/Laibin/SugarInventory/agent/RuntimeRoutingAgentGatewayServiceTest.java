package com.Laibin.SugarInventory.agent;

import com.Laibin.SugarInventory.SpringSecurity.LoginUser;
import com.Laibin.SugarInventory.agent.dto.AgentMessageRequestDTO;
import com.Laibin.SugarInventory.agent.dto.AgentToolAuditDTO;
import com.Laibin.SugarInventory.agent.gateway.AgentGatewayService;
import com.Laibin.SugarInventory.agent.gateway.RuntimeRoutingAgentGatewayService;
import com.Laibin.SugarInventory.agent.python.PythonAgentClient;
import com.Laibin.SugarInventory.agent.python.PythonAgentClientException;
import com.Laibin.SugarInventory.agent.python.dto.PythonAgentChatRequestDTO;
import com.Laibin.SugarInventory.agent.python.dto.PythonAgentChatResponseDTO;
import com.Laibin.SugarInventory.agent.runtime.AgentRuntimeProperties;
import com.Laibin.SugarInventory.agent.service.AgentSessionService;
import com.Laibin.SugarInventory.agent.vo.AgentChoiceOptionVO;
import com.Laibin.SugarInventory.agent.vo.AgentMessageResponseVO;
import com.Laibin.SugarInventory.agent.vo.AgentSessionVO;
import com.Laibin.SugarInventory.domain.po.AgentSession;
import com.Laibin.SugarInventory.domain.po.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RuntimeRoutingAgentGatewayServiceTest {
    private final AgentSessionService sessionService = mock(AgentSessionService.class);
    private final AgentGatewayService legacyGateway = mock(AgentGatewayService.class);
    private final PythonAgentClient pythonClient = mock(PythonAgentClient.class);
    private final AgentRuntimeProperties properties = new AgentRuntimeProperties();
    private final AgentSession session = new AgentSession();
    private final AgentSessionVO sessionVO = new AgentSessionVO();
    private final LoginUser loginUser;
    private RuntimeRoutingAgentGatewayService gateway;

    RuntimeRoutingAgentGatewayServiceTest() {
        User user = new User();
        user.setId(2);
        user.setName("测试用户");
        user.setRoleCode("USER");
        loginUser = new LoginUser(user, List.of(new SimpleGrantedAuthority("inventory:view")));
    }

    @BeforeEach
    void setUp() {
        properties.setMode(AgentRuntimeProperties.Mode.PYTHON);
        properties.setFallbackEnabled(true);
        properties.setPythonServiceKey("service-secret");
        session.setId("agt_001");
        session.setUserId(2);
        session.setScopes("mcp:warehouse:read");
        sessionVO.setAgentSessionId("agt_001");
        sessionVO.setUserId(2);
        sessionVO.setName("测试用户");
        sessionVO.setRoleCode("USER");
        sessionVO.setPermissionCodes(List.of("inventory:view"));
        sessionVO.setScopes(List.of("mcp:warehouse:read"));
        sessionVO.setStatus("ACTIVE");
        when(sessionService.requireOwnedActiveSession(loginUser, "agt_001")).thenReturn(session);
        when(sessionService.toSessionVO(session, loginUser)).thenReturn(sessionVO);
        when(pythonClient.isHealthy()).thenReturn(true);
        gateway = new RuntimeRoutingAgentGatewayService(sessionService, legacyGateway, pythonClient, properties);
    }

    @Test
    void pythonModeForwardsUserMessageWithoutAuthenticationSecrets() throws Exception {
        when(pythonClient.chat(any())).thenReturn(answer("黄冰糖（袋）当前库存为 11板30件。"));

        AgentMessageResponseVO response = gateway.handleMessage(loginUser, "agt_001", request("查黄冰糖库存"));

        ArgumentCaptor<PythonAgentChatRequestDTO> captor = ArgumentCaptor.forClass(PythonAgentChatRequestDTO.class);
        verify(pythonClient).chat(captor.capture());
        PythonAgentChatRequestDTO forwarded = captor.getValue();
        assertThat(forwarded.getMessage().getType()).isEqualTo("user_message");
        assertThat(forwarded.getMessage().getContent()).isEqualTo("查黄冰糖库存");
        assertThat(forwarded.getAgentSessionId()).isEqualTo("agt_001");
        assertThat(forwarded.getUser().getPermissionCodes()).containsExactly("inventory:view");
        String json = new ObjectMapper().writeValueAsString(forwarded);
        assertThat(json).doesNotContain("delegationToken", "Authorization", "refreshToken", "password", "service-secret");
        assertThat(response.getAnswer()).contains("11板30件");
    }

    @Test
    void candidateSelectionIsForwardedAsConversationEventWithoutInternalIds() throws Exception {
        when(pythonClient.chat(any())).thenReturn(answer("黄冰糖（袋）当前库存为 11板30件。"));
        AgentMessageRequestDTO request = request("用户选择了候选项");
        request.setPageContext(Map.of(
                "path", "/dashboard",
                "selectedOption", Map.of(
                        "optionType", "SINGLE_PRODUCT",
                        "displayLabel", "黄冰糖（袋）",
                        "productId", 84
                )
        ));

        gateway.handleMessage(loginUser, "agt_001", request);

        ArgumentCaptor<PythonAgentChatRequestDTO> captor = ArgumentCaptor.forClass(PythonAgentChatRequestDTO.class);
        verify(pythonClient).chat(captor.capture());
        PythonAgentChatRequestDTO.Message message = captor.getValue().getMessage();
        assertThat(message.getType()).isEqualTo("candidate_selected");
        assertThat(message.getContent()).isNull();
        assertThat(message.getSelection().getOptionType()).isEqualTo("SINGLE_PRODUCT");
        assertThat(message.getSelection().getDisplayLabel()).isEqualTo("黄冰糖（袋）");
        String json = new ObjectMapper().writeValueAsString(captor.getValue());
        assertThat(json).doesNotContain("productId", "warehouseId");
    }

    @Test
    void pythonCandidateCardsMapToExistingFrontendOptionsWithoutIds() {
        PythonAgentChatResponseDTO source = answer("“黄冰糖”有多个规格，请选择。");
        source.setNeedsUserSelection(true);
        PythonAgentChatResponseDTO.UserOption sourceOption = new PythonAgentChatResponseDTO.UserOption();
        sourceOption.setOptionId("opt_001");
        sourceOption.setOptionType("SINGLE_PRODUCT");
        sourceOption.setDisplayLabel("黄冰糖（袋）");
        sourceOption.setDescription("25kg/件，40件/板");
        sourceOption.setSupported(true);
        PythonAgentChatResponseDTO.BusinessCard card = new PythonAgentChatResponseDTO.BusinessCard();
        card.setCardType("candidate_selection");
        card.setTitle("请选择查询范围");
        card.setOptions(List.of(sourceOption));
        source.setCards(List.of(card));
        when(pythonClient.chat(any())).thenReturn(source);

        AgentMessageResponseVO response = gateway.handleMessage(loginUser, "agt_001", request("查黄冰糖库存"));

        assertThat(response.isNeedsUserSelection()).isTrue();
        assertThat(response.getOptions()).hasSize(1);
        AgentChoiceOptionVO option = response.getOptions().get(0);
        assertThat(option.getOptionId()).isEqualTo("opt_001");
        assertThat(option.getDisplayLabel()).isEqualTo("黄冰糖（袋）");
        assertThat(option.getProductId()).isNull();
        assertThat(option.getWarehouseId()).isNull();
        assertThat(response.getCards()).hasSize(1);
    }

    @Test
    void pythonResponseWithInternalMarkersIsReplacedBySafeBusinessMessage() {
        when(pythonClient.chat(any())).thenReturn(answer("SUCCESS toolName=get_inventory_overview productId=84"));

        AgentMessageResponseVO response = gateway.handleMessage(loginUser, "agt_001", request("查黄冰糖库存"));

        assertThat(response.getAnswer()).isEqualTo("AI 助手暂时不可用，请稍后重试。");
        assertThat(response.getAnswer()).doesNotContain("SUCCESS", "toolName", "productId");
    }

    @Test
    void timeoutFallsBackOnlyForFirstSimpleReadAndPinsSessionToLegacy() {
        when(pythonClient.chat(any())).thenThrow(new PythonAgentClientException("PYTHON_AGENT_TIMEOUT", true));
        AgentMessageResponseVO legacy = new AgentMessageResponseVO();
        legacy.setAnswer("已通过降级路径查询库存。");
        when(legacyGateway.handleMessage(eq(loginUser), eq("agt_001"), any())).thenReturn(legacy);

        AgentMessageResponseVO first = gateway.handleMessage(loginUser, "agt_001", request("查黄冰糖库存"));
        AgentMessageResponseVO second = gateway.handleMessage(loginUser, "agt_001", request("这些在哪些库位？"));

        assertThat(first.getAnswer()).contains("降级路径");
        assertThat(second.getAnswer()).contains("降级路径");
        verify(pythonClient).chat(any());
        verify(legacyGateway, org.mockito.Mockito.times(2)).handleMessage(eq(loginUser), eq("agt_001"), any());
    }

    @Test
    void timeoutAfterPythonContextDoesNotFallback() {
        when(pythonClient.chat(any()))
                .thenReturn(answer("已建立 Python 会话上下文。"))
                .thenThrow(new PythonAgentClientException("PYTHON_AGENT_TIMEOUT", true));

        gateway.handleMessage(loginUser, "agt_001", request("查黄冰糖库存"));
        AgentMessageResponseVO response = gateway.handleMessage(loginUser, "agt_001", request("它今天有化验吗？"));

        assertThat(response.getAnswer()).isEqualTo("AI 助手暂时不可用，请稍后重试。");
        verify(legacyGateway, never()).handleMessage(any(), any(), any());
    }

    @Test
    void candidateSelectionTimeoutNeverFallsBackWithoutPythonState() {
        when(pythonClient.chat(any())).thenThrow(new PythonAgentClientException("PYTHON_AGENT_TIMEOUT", true));
        AgentMessageRequestDTO request = request("用户选择了候选项");
        request.setPageContext(Map.of(
                "selectedOption", Map.of(
                        "optionType", "SINGLE_PRODUCT",
                        "displayLabel", "黄冰糖（袋）"
                )
        ));

        AgentMessageResponseVO response = gateway.handleMessage(loginUser, "agt_001", request);

        assertThat(response.getAnswer()).isEqualTo("AI 助手暂时不可用，请稍后重试。");
        verify(legacyGateway, never()).handleMessage(any(), any(), any());
    }

    @Test
    void legacyModeKeepsExistingGatewayPath() {
        properties.setMode(AgentRuntimeProperties.Mode.LEGACY);
        AgentMessageResponseVO legacy = new AgentMessageResponseVO();
        legacy.setAnswer("legacy answer");
        when(legacyGateway.handleMessage(loginUser, "agt_001", request("查库存"))).thenReturn(legacy);

        AgentMessageResponseVO response = gateway.handleMessage(loginUser, "agt_001", request("查库存"));

        assertThat(response.getAnswer()).isEqualTo("legacy answer");
        verify(pythonClient, never()).chat(any());
    }

    @Test
    void runtimeAuditContainsRoutingFieldsButNotServiceKey() {
        when(pythonClient.chat(any())).thenReturn(answer("库存查询完成。"));

        gateway.handleMessage(loginUser, "agt_001", request("查黄冰糖库存"));

        ArgumentCaptor<AgentToolAuditDTO> captor = ArgumentCaptor.forClass(AgentToolAuditDTO.class);
        verify(sessionService).recordToolAudit(eq("agt_001"), eq(2), captor.capture());
        AgentToolAuditDTO audit = captor.getValue();
        assertThat(audit.getArgumentsSummary()).contains(
                "runtimeMode=python", "path=python", "requestId=", "traceId=", "fallbackUsed=false");
        assertThat(audit.getArgumentsSummary()).doesNotContain("service-secret", "Authorization", "token");
        assertThat(audit.getResultCode()).isEqualTo("SUCCESS");
    }

    private AgentMessageRequestDTO request(String message) {
        AgentMessageRequestDTO request = new AgentMessageRequestDTO();
        request.setMessage(message);
        request.setPageContext(Map.of("path", "/dashboard"));
        return request;
    }

    private PythonAgentChatResponseDTO answer(String text) {
        PythonAgentChatResponseDTO response = new PythonAgentChatResponseDTO();
        response.setAgentSessionId("agt_001");
        response.setAnswer(text);
        return response;
    }
}