package com.Laibin.SugarInventory.agent.mcp;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Component
public class DefaultMcpProcessFactory implements McpProcessFactory {
    @Override
    public Process start(List<String> command, Map<String, String> environment) throws IOException {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.environment().putAll(environment);
        return builder.start();
    }
}
