package com.Laibin.SugarInventory.agent.service.impl;

import com.Laibin.SugarInventory.SpringSecurity.JwtUtils;
import com.Laibin.SugarInventory.SpringSecurity.LoginUser;
import com.Laibin.SugarInventory.agent.dto.AgentSessionCreateDTO;
import com.Laibin.SugarInventory.agent.dto.AgentToolAuditDTO;
import com.Laibin.SugarInventory.agent.security.AgentAccessPolicy;
import com.Laibin.SugarInventory.agent.security.AgentSessionAuthenticationException;
import com.Laibin.SugarInventory.agent.service.AgentInterruptStateService;
import com.Laibin.SugarInventory.agent.service.AgentSessionService;
import com.Laibin.SugarInventory.agent.service.InternalAgentSessionAccess;
import com.Laibin.SugarInventory.agent.vo.AgentSessionVO;
import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.domain.po.AgentApiAuditLog;
import com.Laibin.SugarInventory.domain.po.AgentSession;
import com.Laibin.SugarInventory.domain.po.AgentToolAuditLog;
import com.Laibin.SugarInventory.mapper.AgentApiAuditLogMapper;
import com.Laibin.SugarInventory.mapper.AgentSessionMapper;
import com.Laibin.SugarInventory.mapper.AgentToolAuditLogMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AgentSessionServiceImpl implements AgentSessionService {
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_REVOKED = "REVOKED";
    private static final String MCP_SERVER_NAME = "smart_warehouse";
    private static final String DEFAULT_MCP_TRANSPORT = "STDIO";
    private static final Set<String> SUPPORTED_SCOPES = Set.of(SCOPE_WAREHOUSE_READ);
    private static final Set<String> READ_ONLY_POST_PATHS = Set.of(
            "/api/inventory/distribution",
            "/api/assay/records/query",
            "/api/assay/report-detail/query",
            "/api/assay/abnormalities/query",
            "/api/assay/products-without-recent-assay/query",
            "/api/assay/standard-coverage/query",
            "/api/pallet-codes/lifecycle/query",
            "/api/pallet-codes/printed-not-inbound/query",
            "/api/pallet-codes/anomalies/query",
            "/api/pallet-codes/flow-records/query",
            "/api/pallet-codes/batch-inbound-completion/query",
            "/api/production/agent-read/entities/resolve",
            "/api/production/agent-read/orders/progress/query",
            "/api/production/agent-read/boiling-batches/query",
            "/api/production/agent-read/boiling-batches/trace/query",
            "/api/production/agent-read/orders/material-pick-trace/query",
            "/api/production/agent-read/orders/label-completion/query",
            "/api/production/agent-read/materials/in-process/query",
            "/api/production/agent-read/orders/material-candidates/query",
            "/api/analytics/agent-read/reports/run",
            "/api/logistics/agent-read/pallet-tasks/query",
            "/api/logistics/agent-read/pallet-tasks/transition/preview",
            "/api/logistics/agent-read/pallet-tasks/finish-inbound/execution/preview",
            "/api/logistics/agent-read/stock-documents/query",
            "/api/logistics/agent-read/auto-inbound/batches/query",
            "/api/logistics/agent-read/auto-inbound/batches/detail/query",
            "/api/warehouse/agent-read/capacity-distribution/query",
            "/api/warehouse/agent-read/recent-operations/query",
            "/api/warehouse/agent-read/mixed-storage-facts/query",
            "/api/master-data/agent-read/products/query",
            "/api/master-data/agent-read/products/detail/query",
            "/api/master-data/agent-read/screen-meshes/query",
            "/api/quality/agent-read/assay-groups/query",
            "/api/quality/agent-read/standards/query",
            "/api/quality/agent-read/standards/detail/query",
            "/api/quality/agent-read/product-standard-relations/query",
            "/api/quality/agent-read/product-quality-configuration/query",
            "/api/administration/agent-read/employees/query",
            "/api/administration/agent-read/roles/query",
            "/api/administration/agent-read/roles/permission-summary/query",
            "/api/audit/agent-read/operation-logs/query",
            "/api/audit/agent-read/agent-tool-audit/query",
            "/api/audit/agent-read/agent-answer-reviews/query",
            "/api/inventory/agent-read/ledger/query",
            "/api/inventory/agent-read/quality/query",
            "/api/pallet-codes/agent-read/fixed-product-pool/query"
    );

    private final AgentSessionMapper agentSessionMapper;
    private final AgentApiAuditLogMapper apiAuditLogMapper;
    private final AgentToolAuditLogMapper toolAuditLogMapper;
    private final AgentInterruptStateService interruptStateService;
    private final JwtUtils jwtUtils;
    private final UserDetailsService userDetailsService;
    private final long delegationTokenTtlMinutes;
    private final String modelDisplayName;

    public AgentSessionServiceImpl(AgentSessionMapper agentSessionMapper,
                                   AgentApiAuditLogMapper apiAuditLogMapper,
                                   AgentToolAuditLogMapper toolAuditLogMapper,
                                   AgentInterruptStateService interruptStateService,
                                   JwtUtils jwtUtils,
                                   UserDetailsService userDetailsService,
                                   @Value("${agent.model.mode:llm}") String agentModelMode,
                                   @Value("${agent.model.name:${openai.model:deepseek-v4-flash}}") String agentModelName,
                                   @Value("${agent.delegation-token-ttl-minutes:15}") long delegationTokenTtlMinutes) {
        this.agentSessionMapper = agentSessionMapper;
        this.apiAuditLogMapper = apiAuditLogMapper;
        this.toolAuditLogMapper = toolAuditLogMapper;
        this.interruptStateService = interruptStateService;
        this.jwtUtils = jwtUtils;
        this.userDetailsService = userDetailsService;
        this.delegationTokenTtlMinutes = delegationTokenTtlMinutes;
        this.modelDisplayName = resolveModelDisplayName(agentModelMode, agentModelName);
    }

    @Override
    @Transactional
    public AgentSessionVO createSession(LoginUser loginUser, AgentSessionCreateDTO dto, HttpServletRequest request) {
        AgentAccessPolicy.requireAgentAccess(loginUser);
        List<String> scopes = normalizeRequestedScopes(dto == null ? null : dto.getRequestedScopes());
        LocalDateTime now = LocalDateTime.now();
        cancelPendingInterruptsForReplacedSessions(loginUser.getUser().getId());
        AgentSession session = new AgentSession();
        session.setId(UUID.randomUUID().toString());
        session.setUserId(loginUser.getUser().getId());
        session.setClientType(limit(normalize(dto == null ? null : dto.getClientType(), "UNKNOWN"), 40));
        session.setScopes(String.join(",", scopes));
        session.setStatus(STATUS_ACTIVE);
        session.setCreatedIp(limit(resolveClientIp(request), 80));
        session.setUserAgent(limit(request == null ? null : request.getHeader("User-Agent"), 500));
        session.setMcpServerName(MCP_SERVER_NAME);
        session.setMcpTransport(limit(normalize(dto == null ? null : dto.getMcpTransport(), DEFAULT_MCP_TRANSPORT), 40));
        session.setIssuedAt(now);
        session.setExpiresAt(now.plusMinutes(delegationTokenTtlMinutes));
        session.setLastUsedAt(now);
        agentSessionMapper.insert(session);
        return toSessionVO(session, loginUser);
    }

    private void cancelPendingInterruptsForReplacedSessions(Integer userId) {
        List<AgentSession> activeSessions = agentSessionMapper.selectList(new LambdaQueryWrapper<AgentSession>()
                .eq(AgentSession::getUserId, userId)
                .eq(AgentSession::getStatus, STATUS_ACTIVE));
        if (activeSessions == null) {
            return;
        }
        for (AgentSession activeSession : activeSessions) {
            if (activeSession.getId() != null) {
                interruptStateService.cancelSessionInterrupts(activeSession.getId(), "SESSION_CLOSED_OR_REPLACED");
            }
        }
    }

    @Override
    public List<AgentSessionVO> listCurrentSessions(LoginUser loginUser) {
        AgentAccessPolicy.requireAgentAccess(loginUser);
        List<AgentSession> sessions = agentSessionMapper.selectList(new LambdaQueryWrapper<AgentSession>()
                .eq(AgentSession::getUserId, loginUser.getUser().getId())
                .orderByDesc(AgentSession::getIssuedAt));
        return sessions.stream().map(session -> toSessionVO(session, loginUser)).collect(Collectors.toList());
    }

    @Override
    public AgentSession requireOwnedActiveSession(LoginUser loginUser, String agentSessionId) {
        AgentAccessPolicy.requireAgentAccess(loginUser);
        AgentSession session = agentSessionMapper.selectById(agentSessionId);
        if (session == null || !Objects.equals(session.getUserId(), loginUser.getUser().getId())) {
            throw new BusinessException(404, "Agent session was not found.");
        }
        if (!STATUS_ACTIVE.equals(session.getStatus()) || isExpired(session)) {
            throw new BusinessException(401, "Agent session is not active.");
        }
        return session;
    }

    @Override
    @Transactional
    public InternalAgentSessionAccess requireActiveInternalToolSession(String agentSessionId) {
        if (agentSessionId == null || agentSessionId.isBlank()) {
            throw auth(HttpServletResponse.SC_UNAUTHORIZED, "AGENT_SESSION_MISSING", "Agent session is not active.");
        }
        AgentSession session = agentSessionMapper.selectById(agentSessionId);
        if (session == null) {
            throw auth(HttpServletResponse.SC_UNAUTHORIZED, "AGENT_SESSION_NOT_FOUND", "Agent session is not active.");
        }
        if (!STATUS_ACTIVE.equals(session.getStatus()) || isExpired(session)) {
            markLastError(session, "AGENT_SESSION_INACTIVE");
            throw auth(HttpServletResponse.SC_UNAUTHORIZED, "AGENT_SESSION_INACTIVE", "Agent session is not active.");
        }
        if (!parseScopes(session.getScopes()).contains(SCOPE_WAREHOUSE_READ)) {
            markLastError(session, "AGENT_SCOPE_DENIED");
            throw auth(HttpServletResponse.SC_FORBIDDEN, "AGENT_SCOPE_DENIED", "Agent scope does not allow this request.");
        }

        UserDetails userDetails;
        try {
            userDetails = userDetailsService.loadUserByUsername(String.valueOf(session.getUserId()));
        } catch (RuntimeException e) {
            markLastError(session, "AGENT_USER_INVALID");
            throw auth(HttpServletResponse.SC_UNAUTHORIZED, "AGENT_USER_INVALID", "Agent user is not active.");
        }
        if (!(userDetails instanceof LoginUser loginUser) || !userStillValid(loginUser)) {
            markLastError(session, "AGENT_USER_INVALID");
            throw auth(HttpServletResponse.SC_UNAUTHORIZED, "AGENT_USER_INVALID", "Agent user is not active.");
        }
        if (!AgentAccessPolicy.canUseAgent(loginUser)) {
            markLastError(session, "AGENT_ACCESS_DENIED");
            throw auth(HttpServletResponse.SC_FORBIDDEN, "AGENT_ACCESS_DENIED", "Agent access permission is required.");
        }

        session.setLastUsedAt(LocalDateTime.now());
        session.setLastErrorCode(null);
        agentSessionMapper.updateById(session);
        return new InternalAgentSessionAccess(session, loginUser);
    }

    @Override
    public AgentSessionVO toSessionVO(AgentSession session, LoginUser loginUser) {
        AgentSessionVO vo = new AgentSessionVO();
        vo.setAgentSessionId(session.getId());
        vo.setUserId(session.getUserId());
        vo.setName(loginUser.getUser().getName());
        vo.setRoleCode(loginUser.getUser().getRoleCode());
        vo.setPermissionCodes(loginUser.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList()));
        vo.setScopes(parseScopes(session.getScopes()));
        vo.setStatus(session.getStatus());
        vo.setExpiresAt(session.getExpiresAt());
        vo.setModelDisplayName(modelDisplayName);
        vo.setMcpServerName(session.getMcpServerName());
        vo.setMcpTransport(session.getMcpTransport());
        return vo;
    }

    @Override
    @Transactional
    public void revokeSession(LoginUser loginUser, String agentSessionId, String revokedReason) {
        AgentAccessPolicy.requireAgentAccess(loginUser);
        AgentSession session = agentSessionMapper.selectById(agentSessionId);
        if (session == null || !Objects.equals(session.getUserId(), loginUser.getUser().getId())) {
            throw new BusinessException(404, "Agent session was not found.");
        }
        session.setStatus(STATUS_REVOKED);
        session.setRevokedAt(LocalDateTime.now());
        session.setRevokedBy(loginUser.getUser().getId());
        session.setRevokedReason(limit(revokedReason, 200));
        agentSessionMapper.updateById(session);
        interruptStateService.cancelSessionInterrupts(
                agentSessionId,
                revokedReason == null || revokedReason.isBlank() ? "SESSION_CLOSED_OR_REPLACED" : revokedReason);
    }

    @Override
    public String issueDelegationTokenForInternalUse(LoginUser loginUser, String agentSessionId) {
        AgentSession session = requireOwnedActiveSession(loginUser, agentSessionId);
        return jwtUtils.generateAgentDelegationToken(loginUser, session.getId(), parseScopes(session.getScopes()), Duration.ofMinutes(delegationTokenTtlMinutes));
    }

    @Override
    @Transactional
    public AgentSession validateDelegation(Claims claims, HttpServletRequest request, UserDetails userDetails) {
        if (!jwtUtils.hasWarehouseMcpAudience(claims)) {
            throw auth(HttpServletResponse.SC_UNAUTHORIZED, "AGENT_TOKEN_BAD_AUDIENCE", "Agent delegation token is invalid.");
        }
        String sessionId = jwtUtils.getAgentSessionId(claims);
        if (sessionId == null || sessionId.isBlank()) {
            throw auth(HttpServletResponse.SC_UNAUTHORIZED, "AGENT_SESSION_MISSING", "Agent session is missing.");
        }
        AgentSession session = agentSessionMapper.selectById(sessionId);
        if (session == null) {
            throw auth(HttpServletResponse.SC_UNAUTHORIZED, "AGENT_SESSION_NOT_FOUND", "Agent session is not active.");
        }
        Integer userId = jwtUtils.getUserIdFromClaims(claims);
        if (!Objects.equals(session.getUserId(), userId)) {
            markLastError(session, "AGENT_SESSION_USER_MISMATCH");
            throw auth(HttpServletResponse.SC_FORBIDDEN, "AGENT_SESSION_USER_MISMATCH", "Agent session is not authorized for this user.");
        }
        if (!STATUS_ACTIVE.equals(session.getStatus()) || isExpired(session)) {
            markLastError(session, "AGENT_SESSION_INACTIVE");
            throw auth(HttpServletResponse.SC_UNAUTHORIZED, "AGENT_SESSION_INACTIVE", "Agent session is not active.");
        }
        if (!userStillValid(userDetails)) {
            markLastError(session, "AGENT_USER_INVALID");
            throw auth(HttpServletResponse.SC_UNAUTHORIZED, "AGENT_USER_INVALID", "Agent user is not active.");
        }
        if (!(userDetails instanceof LoginUser loginUser) || !AgentAccessPolicy.canUseAgent(loginUser)) {
            markLastError(session, "AGENT_ACCESS_DENIED");
            throw auth(HttpServletResponse.SC_FORBIDDEN, "AGENT_ACCESS_DENIED", "Agent access permission is required.");
        }
        if (!jwtUtils.getScopes(claims).contains(SCOPE_WAREHOUSE_READ)) {
            markLastError(session, "AGENT_SCOPE_DENIED");
            throw auth(HttpServletResponse.SC_FORBIDDEN, "AGENT_SCOPE_DENIED", "Agent scope does not allow this request.");
        }
        if (!isAllowedDelegatedRequest(request)) {
            markLastError(session, "AGENT_SCOPE_DENIED");
            throw auth(HttpServletResponse.SC_FORBIDDEN, "AGENT_SCOPE_DENIED", "Agent scope does not allow this request.");
        }
        session.setLastUsedAt(LocalDateTime.now());
        session.setLastErrorCode(null);
        agentSessionMapper.updateById(session);
        return session;
    }

    @Override
    public void recordApiAudit(String agentSessionId, Integer userId, String toolName, String method, String path,
                               int responseStatus, String resultCode, String errorCode, long durationMs) {
        AgentApiAuditLog log = new AgentApiAuditLog();
        log.setAgentSessionId(limit(agentSessionId, 80));
        log.setUserId(userId);
        log.setToolName(limit(toolName, 100));
        log.setHttpMethod(limit(method, 12));
        log.setRequestPath(limit(path, 500));
        log.setResponseStatus(responseStatus);
        log.setResultCode(limit(resultCode, 40));
        log.setErrorCode(limit(errorCode, 80));
        log.setDurationMs(durationMs);
        log.setCreatedAt(LocalDateTime.now());
        apiAuditLogMapper.insert(log);
        if (responseStatus >= 400 && agentSessionId != null) {
            updateLastError(agentSessionId, errorCode == null ? "HTTP_" + responseStatus : errorCode);
        }
    }

    @Override
    public String recordToolAudit(String agentSessionId, Integer userId, AgentToolAuditDTO dto) {
        AgentToolAuditLog log = new AgentToolAuditLog();
        log.setAgentSessionId(limit(agentSessionId, 80));
        log.setUserId(userId);
        log.setToolName(limit(dto.getToolName(), 100));
        log.setToolCallId(limit(dto.getToolCallId(), 100));
        log.setMessageId(limit(dto.getMessageId(), 100));
        log.setUpstreamPath(limit(dto.getUpstreamPath(), 500));
        log.setArgumentsSummary(limit(sanitizeSummary(dto.getArgumentsSummary()), 1000));
        log.setRequestSummary(limit(sanitizeSummary(withUpstreamPath(dto.getUpstreamPath(), dto.getRequestSummary())), 1000));
        log.setResponseSummary(limit(sanitizeSummary(dto.getResponseSummary()), 1000));
        log.setResultCode(limit(dto.getResultCode(), 40));
        log.setErrorCode(limit(dto.getErrorCode(), 80));
        log.setDurationMs(dto.getDurationMs());
        log.setCreatedAt(LocalDateTime.now());
        toolAuditLogMapper.insert(log);
        if (dto.getErrorCode() != null && agentSessionId != null) {
            updateLastError(agentSessionId, dto.getErrorCode());
        }
        return log.getId() == null ? null : "audit_" + log.getId();
    }

    private List<String> normalizeRequestedScopes(List<String> requestedScopes) {
        if (requestedScopes == null || requestedScopes.isEmpty()) {
            return List.of(SCOPE_WAREHOUSE_READ);
        }
        Set<String> result = requestedScopes.stream()
                .filter(scope -> scope != null && !scope.isBlank())
                .map(String::trim)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (result.isEmpty()) {
            return List.of(SCOPE_WAREHOUSE_READ);
        }
        if (!SUPPORTED_SCOPES.containsAll(result)) {
            throw new BusinessException(400, "Unsupported agent scope.");
        }
        return new ArrayList<>(result);
    }

    private List<String> parseScopes(String scopes) {
        if (scopes == null || scopes.isBlank()) {
            return List.of();
        }
        return List.of(scopes.split(",")).stream()
                .map(String::trim)
                .filter(scope -> !scope.isBlank())
                .collect(Collectors.toList());
    }

    private boolean isExpired(AgentSession session) {
        return session.getExpiresAt() != null && session.getExpiresAt().isBefore(LocalDateTime.now());
    }

    private boolean userStillValid(UserDetails userDetails) {
        if (userDetails == null || !userDetails.isEnabled()) {
            return false;
        }
        if (!(userDetails instanceof LoginUser loginUser)) {
            return false;
        }
        return loginUser.getUser() != null
                && loginUser.getUser().getId() != null
                && loginUser.getUser().getRoleCode() != null
                && !loginUser.getUser().getRoleCode().isBlank();
    }

    private boolean isAllowedDelegatedRequest(HttpServletRequest request) {
        if (request == null) {
            return false;
        }
        String method = request.getMethod();
        String uri = request.getRequestURI();
        if ("GET".equalsIgnoreCase(method)) {
            return true;
        }
        return "POST".equalsIgnoreCase(method)
                && ("/api/agent/audit/tool-calls".equals(uri)
                || READ_ONLY_POST_PATHS.contains(uri)
                || uri.matches("/api/inventory/qualified-inventory/[0-9]+/page"));
    }

    private void markLastError(AgentSession session, String errorCode) {
        session.setLastErrorCode(errorCode);
        agentSessionMapper.updateById(session);
    }

    private void updateLastError(String agentSessionId, String errorCode) {
        AgentSession update = new AgentSession();
        update.setId(agentSessionId);
        update.setLastErrorCode(limit(errorCode, 80));
        agentSessionMapper.updateById(update);
    }

    private AgentSessionAuthenticationException auth(int status, String errorCode, String message) {
        return new AgentSessionAuthenticationException(status, errorCode, message);
    }

    private String resolveClientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String resolveModelDisplayName(String mode, String modelName) {
        if ("rule".equalsIgnoreCase(normalize(mode, "llm"))) {
            return "规则解析器";
        }
        return limit(normalize(modelName, "模型运行中").replace('-', ' ').replace('_', ' '), 80);
    }


    private String withUpstreamPath(String upstreamPath, String requestSummary) {
        if (upstreamPath == null || upstreamPath.isBlank()) {
            return requestSummary;
        }
        String prefix = "upstreamPath=" + upstreamPath.trim();
        if (requestSummary == null || requestSummary.isBlank()) {
            return prefix;
        }
        return prefix + "; " + requestSummary;
    }
    private String sanitizeSummary(String value) {
        if (value == null) {
            return null;
        }
        return value
                .replaceAll("(?i)authorization\\s*[:=]\\s*bearer\\s+[^\\s,;]+", "Authorization: <redacted>")
                .replaceAll("(?i)bearer\\s+[^\\s,;]+", "Bearer <redacted>")
                .replaceAll("(?i)(token|api[_-]?key|password|secret)\\s*[:=]\\s*[^\\s,;]+", "$1=<redacted>")
                .replaceAll("(?i)jdbc:[^\\s,;]+", "jdbc:<redacted>")
                .replaceAll("(?m)^\\s*at\\s+.+$", "<stack redacted>");
    }

    private String limit(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }
}




