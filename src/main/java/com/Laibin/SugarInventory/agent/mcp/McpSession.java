package com.Laibin.SugarInventory.agent.mcp;

import java.util.Set;

public interface McpSession extends AutoCloseable {
    String agentSessionId();

    McpToolResult callTool(McpToolCall call);

    Set<String> listTools();

    @Override
    void close();
}
