package com.Laibin.SugarInventory.agent.service;

import com.Laibin.SugarInventory.SpringSecurity.LoginUser;
import com.Laibin.SugarInventory.agent.mcp.McpSessionManager;
import com.Laibin.SugarInventory.domain.po.AgentSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

@Service
public class AgentMcpWarmupService {
    private static final Logger log = LoggerFactory.getLogger(AgentMcpWarmupService.class);

    private final AgentSessionService agentSessionService;
    private final McpSessionManager mcpSessionManager;
    private final Executor warmupExecutor;
    private final boolean warmupEnabled;

    public AgentMcpWarmupService(AgentSessionService agentSessionService,
                                 McpSessionManager mcpSessionManager,
                                 @Qualifier("agentMcpWarmupExecutor") Executor warmupExecutor,
                                 @Value("${agent.mcp.warmup-enabled:true}") boolean warmupEnabled) {
        this.agentSessionService = agentSessionService;
        this.mcpSessionManager = mcpSessionManager;
        this.warmupExecutor = warmupExecutor;
        this.warmupEnabled = warmupEnabled;
    }

    public void warmUp(LoginUser loginUser, String agentSessionId) {
        if (!warmupEnabled || loginUser == null || agentSessionId == null || agentSessionId.isBlank()) {
            return;
        }
        try {
            warmupExecutor.execute(() -> performWarmup(loginUser, agentSessionId));
        } catch (RejectedExecutionException e) {
            log.warn("Agent MCP warm-up skipped; result=QUEUE_FULL");
        }
    }

    private void performWarmup(LoginUser loginUser, String agentSessionId) {
        long start = System.nanoTime();
        try {
            AgentSession session = agentSessionService.requireOwnedActiveSession(loginUser, agentSessionId);
            mcpSessionManager.bindSession(loginUser, session);
            log.info("Agent MCP warm-up completed; result=SUCCESS; durationMs={}", elapsedMs(start));
        } catch (RuntimeException e) {
            log.warn("Agent MCP warm-up did not complete; result=FAILED; errorType={}; durationMs={}",
                    e.getClass().getSimpleName(), elapsedMs(start));
        }
    }

    private long elapsedMs(long start) {
        return (System.nanoTime() - start) / 1_000_000L;
    }
}
