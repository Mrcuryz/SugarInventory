package com.Laibin.SugarInventory.agent.mcp;

import com.fasterxml.jackson.databind.JsonNode;

public record McpToolResult(String toolName, JsonNode result, String resultCode, String errorCode, long durationMs) {
    public boolean success() {
        return errorCode == null || errorCode.isBlank();
    }
}
