package com.Laibin.SugarInventory.agent;

import com.Laibin.SugarInventory.SpringSecurity.LoginUser;
import com.Laibin.SugarInventory.agent.mcp.McpProcessFactory;
import com.Laibin.SugarInventory.agent.mcp.McpSession;
import com.Laibin.SugarInventory.agent.mcp.StdioMcpSessionManager;
import com.Laibin.SugarInventory.agent.service.AgentSessionService;
import com.Laibin.SugarInventory.domain.po.AgentSession;
import com.Laibin.SugarInventory.domain.po.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StdioMcpSessionManagerTest {
    @Test
    void startsDedicatedProcessWithDelegatedTokenEnvironmentWithoutPuttingTokenInCommand() throws Exception {
        AgentSessionService sessionService = mock(AgentSessionService.class);
        LoginUser loginUser = loginUser();
        AgentSession session = new AgentSession();
        session.setId("session-1");
        session.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        when(sessionService.issueDelegationTokenForInternalUse(loginUser, "session-1")).thenReturn("delegated.jwt.secret");

        AtomicReference<List<String>> commandRef = new AtomicReference<>();
        AtomicReference<Map<String, String>> envRef = new AtomicReference<>();
        McpProcessFactory factory = (command, environment) -> {
            commandRef.set(command);
            envRef.set(environment);
            return new FakeProcess();
        };
        StdioMcpSessionManager manager = new StdioMcpSessionManager(
                sessionService,
                factory,
                new ObjectMapper(),
                "warehouse-mcp/target/warehouse-mcp-0.1.0.jar",
                "http://localhost:8080",
                "logs/mcp");

        McpSession mcpSession = manager.bindSession(loginUser, session);

        assertThat(mcpSession.agentSessionId()).isEqualTo("session-1");
        assertThat(envRef.get()).containsEntry("WAREHOUSE_DELEGATED_TOKEN", "delegated.jwt.secret")
                .containsEntry("WAREHOUSE_AGENT_SESSION_ID", "session-1")
                .containsEntry("WAREHOUSE_API_BASE_URL", "http://localhost:8080");
        assertThat(envRef.get().get("WAREHOUSE_MCP_LOG_FILE")).contains("warehouse-mcp-session-1.log");
        assertThat(String.join(" ", commandRef.get())).doesNotContain("delegated.jwt.secret", "Authorization");
    }

    private LoginUser loginUser() {
        User user = new User();
        user.setId(7);
        user.setName("测试用户");
        user.setRoleCode("ADMIN");
        return new LoginUser(user, List.of(new SimpleGrantedAuthority("record:query")));
    }

    private static class FakeProcess extends Process {
        private boolean alive = true;

        @Override
        public OutputStream getOutputStream() {
            return new ByteArrayOutputStream();
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public InputStream getErrorStream() {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public int waitFor() {
            alive = false;
            return 0;
        }

        @Override
        public int exitValue() {
            return 0;
        }

        @Override
        public void destroy() {
            alive = false;
        }

        @Override
        public boolean isAlive() {
            return alive;
        }
    }
}
