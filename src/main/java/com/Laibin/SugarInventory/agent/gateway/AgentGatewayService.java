package com.Laibin.SugarInventory.agent.gateway;

import com.Laibin.SugarInventory.SpringSecurity.LoginUser;
import com.Laibin.SugarInventory.agent.dto.AgentMessageRequestDTO;
import com.Laibin.SugarInventory.agent.vo.AgentMessageResponseVO;

public interface AgentGatewayService {
    AgentMessageResponseVO handleMessage(LoginUser loginUser, String agentSessionId, AgentMessageRequestDTO request);

    default void clearSession(String agentSessionId) {
    }
}
