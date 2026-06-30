package com.Laibin.SugarInventory.agent.security;

public final class AgentSecurityContext {
    public static final String ATTR_AGENT_SESSION_ID = "agentSessionId";
    public static final String ATTR_AGENT_USER_ID = "agentUserId";
    public static final String ATTR_AGENT_TOOL_NAME = "agentToolName";
    public static final String HEADER_AGENT_SESSION_ID = "X-Agent-Session-Id";
    public static final String HEADER_AGENT_TOOL_NAME = "X-Agent-Tool-Name";

    private AgentSecurityContext() {
    }
}
