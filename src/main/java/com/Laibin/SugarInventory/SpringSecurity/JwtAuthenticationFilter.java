package com.Laibin.SugarInventory.SpringSecurity;

import com.Laibin.SugarInventory.agent.security.AgentSecurityContext;
import com.Laibin.SugarInventory.agent.security.AgentSessionAuthenticationException;
import com.Laibin.SugarInventory.agent.service.AgentSessionService;
import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.domain.po.AgentSession;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtUtils jwtUtils;
    private final UserDetailsService userDetailsService;
    private final AgentSessionService agentSessionService;

    public JwtAuthenticationFilter(JwtUtils jwtUtils,
                                   UserDetailsService userDetailsService,
                                   AgentSessionService agentSessionService) {
        this.jwtUtils = jwtUtils;
        this.userDetailsService = userDetailsService;
        this.agentSessionService = agentSessionService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws IOException, ServletException {
        response.setCharacterEncoding("UTF-8");
        String token = jwtUtils.parseToken(request);
        if (token != null) {
            try {
                Claims claims = jwtUtils.parseClaims(token);
                Integer userId = jwtUtils.getUserIdFromClaims(claims);
                UserDetails userDetails = loadUserDetails(userId, response);
                if (userDetails == null) {
                    return;
                }

                if (jwtUtils.isAgentDelegationToken(claims)) {
                    AgentSession session = agentSessionService.validateDelegation(claims, request, userDetails);
                    request.setAttribute(AgentSecurityContext.ATTR_AGENT_SESSION_ID, session.getId());
                    request.setAttribute(AgentSecurityContext.ATTR_AGENT_USER_ID, session.getUserId());
                    request.setAttribute(AgentSecurityContext.ATTR_AGENT_TOOL_NAME,
                            safeHeader(request.getHeader(AgentSecurityContext.HEADER_AGENT_TOOL_NAME), 100));
                }

                SecurityContext context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                userDetails,
                                null,
                                userDetails.getAuthorities()
                        )
                );
                SecurityContextHolder.setContext(context);
            } catch (ExpiredJwtException e) {
                writeAuthError(response, HttpServletResponse.SC_UNAUTHORIZED, "JWT Token已过期");
                return;
            } catch (AgentSessionAuthenticationException e) {
                writeAuthError(response, e.getStatus(), e.getMessage());
                return;
            } catch (Exception e) {
                writeAuthError(response, HttpServletResponse.SC_UNAUTHORIZED, "认证失败");
                return;
            }
        }

        chain.doFilter(request, response);
    }

    private UserDetails loadUserDetails(Integer userId, HttpServletResponse response) throws IOException {
        if (userId == null) {
            writeAuthError(response, HttpServletResponse.SC_UNAUTHORIZED, "用户不存在");
            return null;
        }
        try {
            return userDetailsService.loadUserByUsername(userId.toString());
        } catch (BusinessException e) {
            writeAuthError(response, HttpServletResponse.SC_UNAUTHORIZED, "用户不存在");
            return null;
        }
    }

    private void writeAuthError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.getWriter().write(message);
    }

    private String safeHeader(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String cleaned = value.replaceAll("[^A-Za-z0-9_.:-]", "_");
        return cleaned.length() <= maxLength ? cleaned : cleaned.substring(0, maxLength);
    }
}
