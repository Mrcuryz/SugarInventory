package com.Laibin.SugarInventory.agent.security;

import com.Laibin.SugarInventory.agent.service.AgentSessionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class AgentApiAuditFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(AgentApiAuditFilter.class);

    private final AgentSessionService agentSessionService;

    public AgentApiAuditFilter(AgentSessionService agentSessionService) {
        this.agentSessionService = agentSessionService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        long startNanos = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            Object sessionId = request.getAttribute(AgentSecurityContext.ATTR_AGENT_SESSION_ID);
            if (sessionId != null) {
                try {
                    Object userId = request.getAttribute(AgentSecurityContext.ATTR_AGENT_USER_ID);
                    Object toolName = request.getAttribute(AgentSecurityContext.ATTR_AGENT_TOOL_NAME);
                    long durationMs = (System.nanoTime() - startNanos) / 1_000_000L;
                    int status = response.getStatus();
                    String resultCode = status < 400 ? "SUCCESS" : "ERROR";
                    String errorCode = status < 400 ? null : "HTTP_" + status;
                    agentSessionService.recordApiAudit(
                            String.valueOf(sessionId),
                            userId instanceof Integer value ? value : null,
                            toolName == null ? null : String.valueOf(toolName),
                            request.getMethod(),
                            request.getRequestURI(),
                            status,
                            resultCode,
                            errorCode,
                            durationMs);
                } catch (RuntimeException e) {
                    log.warn("Failed to record agent API audit: {}", e.getMessage());
                }
            }
        }
    }
}
