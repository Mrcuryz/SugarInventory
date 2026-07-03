package com.Laibin.SugarInventory.agent.gateway;

import com.Laibin.SugarInventory.SpringSecurity.LoginUser;
import com.Laibin.SugarInventory.agent.dto.AgentMessageRequestDTO;
import com.Laibin.SugarInventory.agent.vo.AgentMessageResponseVO;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface AgentGatewayService {
    AgentMessageResponseVO handleMessage(LoginUser loginUser, String agentSessionId, AgentMessageRequestDTO request);

    default SseEmitter streamMessage(LoginUser loginUser, String agentSessionId, AgentMessageRequestDTO request) {
        throw new UnsupportedOperationException("Agent streaming is not available.");
    }

    default boolean cancelMessage(LoginUser loginUser, String agentSessionId, String messageId) {
        return false;
    }

    default void clearSession(String agentSessionId) {
    }
}
