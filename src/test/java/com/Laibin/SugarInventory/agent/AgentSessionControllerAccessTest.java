package com.Laibin.SugarInventory.agent;

import com.Laibin.SugarInventory.SpringSecurity.LoginUser;
import com.Laibin.SugarInventory.agent.context.AgentConversationMemory;
import com.Laibin.SugarInventory.agent.controller.AgentSessionController;
import com.Laibin.SugarInventory.agent.dto.AgentMessageRequestDTO;
import com.Laibin.SugarInventory.agent.dto.AgentSessionCreateDTO;
import com.Laibin.SugarInventory.agent.gateway.AgentGatewayService;
import com.Laibin.SugarInventory.agent.mcp.McpSessionManager;
import com.Laibin.SugarInventory.agent.service.AgentMcpWarmupService;
import com.Laibin.SugarInventory.agent.service.AgentMessageReviewService;
import com.Laibin.SugarInventory.agent.service.AgentSessionService;
import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.domain.po.User;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class AgentSessionControllerAccessTest {
    @Test
    void nonAdminCannotCreateSessionOrSendMessage() {
        AgentSessionService sessionService = mock(AgentSessionService.class);
        AgentGatewayService gatewayService = mock(AgentGatewayService.class);
        AgentMcpWarmupService warmupService = mock(AgentMcpWarmupService.class);
        AgentSessionController controller = controller(sessionService, gatewayService, warmupService);
        LoginUser staff = loginUser("STAFF");

        assertThatThrownBy(() -> controller.createSession(
                staff,
                new AgentSessionCreateDTO(),
                new MockHttpServletRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(403);
        assertThatThrownBy(() -> controller.sendMessage(
                staff,
                "session-1",
                new AgentMessageRequestDTO()))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(403);

        verifyNoInteractions(sessionService, gatewayService, warmupService);
    }

    private AgentSessionController controller(AgentSessionService sessionService,
                                              AgentGatewayService gatewayService,
                                              AgentMcpWarmupService warmupService) {
        return new AgentSessionController(
                sessionService,
                gatewayService,
                mock(McpSessionManager.class),
                mock(AgentConversationMemory.class),
                mock(AgentMessageReviewService.class),
                warmupService);
    }

    private LoginUser loginUser(String roleCode) {
        User user = new User();
        user.setId(7);
        user.setRoleCode(roleCode);
        return new LoginUser(user, List.of());
    }
}
