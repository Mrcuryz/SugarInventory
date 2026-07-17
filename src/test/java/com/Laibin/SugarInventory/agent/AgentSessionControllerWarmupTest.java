package com.Laibin.SugarInventory.agent;

import com.Laibin.SugarInventory.SpringSecurity.LoginUser;
import com.Laibin.SugarInventory.agent.context.AgentConversationMemory;
import com.Laibin.SugarInventory.agent.controller.AgentSessionController;
import com.Laibin.SugarInventory.agent.dto.AgentSessionCreateDTO;
import com.Laibin.SugarInventory.agent.gateway.AgentGatewayService;
import com.Laibin.SugarInventory.agent.mcp.McpSessionManager;
import com.Laibin.SugarInventory.agent.service.AgentMcpWarmupService;
import com.Laibin.SugarInventory.agent.service.AgentMessageReviewService;
import com.Laibin.SugarInventory.agent.service.AgentSessionService;
import com.Laibin.SugarInventory.agent.vo.AgentSessionVO;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentSessionControllerWarmupTest {
    @Test
    void schedulesMcpWarmupAfterSessionCreation() {
        AgentSessionService sessionService = mock(AgentSessionService.class);
        AgentGatewayService gatewayService = mock(AgentGatewayService.class);
        McpSessionManager mcpSessionManager = mock(McpSessionManager.class);
        AgentConversationMemory conversationMemory = mock(AgentConversationMemory.class);
        AgentMessageReviewService reviewService = mock(AgentMessageReviewService.class);
        AgentMcpWarmupService warmupService = mock(AgentMcpWarmupService.class);
        LoginUser loginUser = mock(LoginUser.class);
        AgentSessionCreateDTO request = new AgentSessionCreateDTO();
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        AgentSessionVO created = new AgentSessionVO();
        created.setAgentSessionId("session-1");
        when(sessionService.createSession(loginUser, request, servletRequest)).thenReturn(created);
        AgentSessionController controller = new AgentSessionController(
                sessionService,
                gatewayService,
                mcpSessionManager,
                conversationMemory,
                reviewService,
                warmupService);

        controller.createSession(loginUser, request, servletRequest);

        verify(warmupService).warmUp(loginUser, "session-1");
    }
}
