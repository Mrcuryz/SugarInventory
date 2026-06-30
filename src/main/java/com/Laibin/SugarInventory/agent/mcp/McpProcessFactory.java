package com.Laibin.SugarInventory.agent.mcp;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public interface McpProcessFactory {
    Process start(List<String> command, Map<String, String> environment) throws IOException;
}
