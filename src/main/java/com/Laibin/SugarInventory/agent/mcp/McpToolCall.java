package com.Laibin.SugarInventory.agent.mcp;

import java.util.Map;

public record McpToolCall(String toolName, Map<String, Object> arguments) {
}
