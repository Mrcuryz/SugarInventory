package com.Laibin.SugarInventory.agent.mcp;

import com.Laibin.SugarInventory.SpringSecurity.LoginUser;
import com.Laibin.SugarInventory.agent.service.AgentSessionService;
import com.Laibin.SugarInventory.domain.po.AgentSession;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class StdioMcpSessionManager implements McpSessionManager {
    private static final Logger log = LoggerFactory.getLogger(StdioMcpSessionManager.class);

    private final AgentSessionService agentSessionService;
    private final McpProcessFactory processFactory;
    private final ObjectMapper objectMapper;
    private final String jarPath;
    private final String apiBaseUrl;
    private final String logFileDirectory;
    private final Map<String, ManagedSession> sessions = new ConcurrentHashMap<>();

    public StdioMcpSessionManager(AgentSessionService agentSessionService,
                                  McpProcessFactory processFactory,
                                  ObjectMapper objectMapper,
                                  @Value("${agent.mcp.jar-path:warehouse-mcp/target/warehouse-mcp-0.1.0.jar}") String jarPath,
                                  @Value("${agent.mcp.api-base-url:http://localhost:8080}") String apiBaseUrl,
                                  @Value("${agent.mcp.log-dir:logs/mcp}") String logFileDirectory) {
        this.agentSessionService = agentSessionService;
        this.processFactory = processFactory;
        this.objectMapper = objectMapper;
        this.jarPath = jarPath;
        this.apiBaseUrl = apiBaseUrl;
        this.logFileDirectory = logFileDirectory;
    }

    @Override
    public McpSession bindSession(LoginUser loginUser, AgentSession agentSession) {
        cleanupExpiredSessions();
        ManagedSession existing = sessions.get(agentSession.getId());
        if (existing != null && existing.process().isAlive()) {
            return existing.session();
        }
        String delegatedToken = agentSessionService.issueDelegationTokenForInternalUse(loginUser, agentSession.getId());
        Map<String, String> environment = Map.of(
                "WAREHOUSE_DELEGATED_TOKEN", delegatedToken,
                "WAREHOUSE_AGENT_SESSION_ID", agentSession.getId(),
                "WAREHOUSE_API_BASE_URL", apiBaseUrl,
                "WAREHOUSE_MCP_LOG_FILE", Path.of(logFileDirectory, "warehouse-mcp-" + agentSession.getId() + ".log").toString()
        );
        try {
            Process process = processFactory.start(List.of("java", "-jar", jarPath), environment);
            StdioMcpSession session = new StdioMcpSession(agentSession.getId(), process, objectMapper);
            sessions.put(agentSession.getId(), new ManagedSession(session, process, agentSession.getExpiresAt()));
            return session;
        } catch (IOException e) {
            log.warn("Failed to start MCP process for agent session {}: {}", agentSession.getId(), e.getMessage());
            throw new IllegalStateException("Unable to start MCP session.");
        }
    }

    @Override
    public void closeSession(String agentSessionId) {
        ManagedSession managed = sessions.remove(agentSessionId);
        if (managed != null) {
            managed.session().close();
        }
    }

    @Override
    public void cleanupExpiredSessions() {
        LocalDateTime now = LocalDateTime.now();
        sessions.entrySet().removeIf(entry -> {
            ManagedSession managed = entry.getValue();
            boolean expired = managed.expiresAt() != null && managed.expiresAt().isBefore(now);
            boolean dead = !managed.process().isAlive();
            if (expired || dead) {
                managed.session().close();
                return true;
            }
            return false;
        });
    }

    private record ManagedSession(McpSession session, Process process, LocalDateTime expiresAt) {
    }
}
