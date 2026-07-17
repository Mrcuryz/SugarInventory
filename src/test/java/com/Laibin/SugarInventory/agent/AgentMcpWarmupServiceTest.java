package com.Laibin.SugarInventory.agent;

import com.Laibin.SugarInventory.SpringSecurity.LoginUser;
import com.Laibin.SugarInventory.agent.mcp.McpSessionManager;
import com.Laibin.SugarInventory.agent.service.AgentMcpWarmupService;
import com.Laibin.SugarInventory.agent.service.AgentSessionService;
import com.Laibin.SugarInventory.domain.po.AgentSession;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentMcpWarmupServiceTest {
    @Test
    void schedulesWarmupWithoutBlockingSessionCreation() {
        AgentSessionService sessionService = mock(AgentSessionService.class);
        McpSessionManager mcpSessionManager = mock(McpSessionManager.class);
        LoginUser loginUser = mock(LoginUser.class);
        AgentSession session = new AgentSession();
        session.setId("session-1");
        when(sessionService.requireOwnedActiveSession(loginUser, "session-1")).thenReturn(session);
        AtomicReference<Runnable> scheduled = new AtomicReference<>();
        Executor executor = scheduled::set;
        AgentMcpWarmupService service = new AgentMcpWarmupService(
                sessionService, mcpSessionManager, executor, true);

        service.warmUp(loginUser, "session-1");

        verify(mcpSessionManager, never()).bindSession(loginUser, session);
        scheduled.get().run();
        verify(mcpSessionManager).bindSession(loginUser, session);
    }

    @Test
    void warmupFailureDoesNotFailSessionCreationPath() {
        AgentSessionService sessionService = mock(AgentSessionService.class);
        McpSessionManager mcpSessionManager = mock(McpSessionManager.class);
        LoginUser loginUser = mock(LoginUser.class);
        when(sessionService.requireOwnedActiveSession(loginUser, "session-1"))
                .thenThrow(new IllegalStateException("MCP unavailable"));
        AgentMcpWarmupService service = new AgentMcpWarmupService(
                sessionService, mcpSessionManager, Runnable::run, true);

        assertThatCode(() -> service.warmUp(loginUser, "session-1")).doesNotThrowAnyException();
    }

    @Test
    void fullWarmupQueueDoesNotFailSessionCreationPath() {
        AgentSessionService sessionService = mock(AgentSessionService.class);
        McpSessionManager mcpSessionManager = mock(McpSessionManager.class);
        LoginUser loginUser = mock(LoginUser.class);
        Executor rejectingExecutor = task -> {
            throw new RejectedExecutionException("queue full");
        };
        AgentMcpWarmupService service = new AgentMcpWarmupService(
                sessionService, mcpSessionManager, rejectingExecutor, true);

        assertThatCode(() -> service.warmUp(loginUser, "session-1")).doesNotThrowAnyException();
        verify(sessionService, never()).requireOwnedActiveSession(loginUser, "session-1");
    }
}
