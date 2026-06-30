package com.Laibin.SugarInventory.agent.mcp;

public interface McpSession extends AutoCloseable {
    String agentSessionId();

    McpToolResult callTool(McpToolCall call);

    @Override
    void close();
}
