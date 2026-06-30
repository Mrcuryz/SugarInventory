package com.Laibin.SugarInventory.agent.mcp;

import com.Laibin.SugarInventory.SpringSecurity.LoginUser;
import com.Laibin.SugarInventory.domain.po.AgentSession;

public interface McpSessionManager {
    McpSession bindSession(LoginUser loginUser, AgentSession agentSession);

    void closeSession(String agentSessionId);

    void cleanupExpiredSessions();
}
