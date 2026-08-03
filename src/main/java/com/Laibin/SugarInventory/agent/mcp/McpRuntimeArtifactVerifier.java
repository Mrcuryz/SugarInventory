package com.Laibin.SugarInventory.agent.mcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class McpRuntimeArtifactVerifier implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(McpRuntimeArtifactVerifier.class);

    private final StdioMcpSessionManager sessionManager;
    private final boolean verificationEnabled;

    public McpRuntimeArtifactVerifier(
            StdioMcpSessionManager sessionManager,
            @Value("${agent.mcp.runtime-verification-enabled:true}") boolean verificationEnabled) {
        this.sessionManager = sessionManager;
        this.verificationEnabled = verificationEnabled;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!verificationEnabled) {
            log.warn("Warehouse MCP runtime artifact verification is disabled.");
            return;
        }
        long start = System.nanoTime();
        sessionManager.verifyRuntimeCapabilities();
        log.info("Warehouse MCP runtime artifact verified; result=SUCCESS; durationMs={}", elapsedMs(start));
    }

    private long elapsedMs(long start) {
        return (System.nanoTime() - start) / 1_000_000L;
    }
}
