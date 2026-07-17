package com.Laibin.SugarInventory.agent;

import com.Laibin.SugarInventory.SpringSecurity.JwtUtils;
import com.Laibin.SugarInventory.SpringSecurity.LoginUser;
import com.Laibin.SugarInventory.agent.dto.AgentSessionCreateDTO;
import com.Laibin.SugarInventory.agent.dto.AgentToolAuditDTO;
import com.Laibin.SugarInventory.agent.security.AgentSessionAuthenticationException;
import com.Laibin.SugarInventory.agent.service.AgentInterruptStateService;
import com.Laibin.SugarInventory.agent.service.AgentSessionService;
import com.Laibin.SugarInventory.agent.service.impl.AgentSessionServiceImpl;
import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.domain.po.AgentSession;
import com.Laibin.SugarInventory.domain.po.AgentToolAuditLog;
import com.Laibin.SugarInventory.domain.po.User;
import com.Laibin.SugarInventory.mapper.AgentApiAuditLogMapper;
import com.Laibin.SugarInventory.mapper.AgentSessionMapper;
import com.Laibin.SugarInventory.mapper.AgentToolAuditLogMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentSessionServiceImplTest {
    private AgentSessionMapper sessionMapper;
    private JwtUtils jwtUtils;
    private AgentToolAuditLogMapper toolAuditLogMapper;
    private AgentInterruptStateService interruptStateService;
    private UserDetailsService userDetailsService;
    private AgentSessionServiceImpl service;
    private LoginUser loginUser;

    @BeforeEach
    void setUp() {
        sessionMapper = mock(AgentSessionMapper.class);
        jwtUtils = mock(JwtUtils.class);
        toolAuditLogMapper = mock(AgentToolAuditLogMapper.class);
        interruptStateService = mock(AgentInterruptStateService.class);
        userDetailsService = mock(UserDetailsService.class);
        service = new AgentSessionServiceImpl(
                sessionMapper,
                mock(AgentApiAuditLogMapper.class),
                toolAuditLogMapper,
                interruptStateService,
                jwtUtils,
                userDetailsService,
                "llm",
                "deepseek-v4-flash",
                15);
        User user = new User();
        user.setId(7);
        user.setName("测试用户");
        user.setRoleCode("ADMIN");
        loginUser = new LoginUser(user, List.of(new SimpleGrantedAuthority("record:query")));
    }

    @Test
    void createsSessionResponseWithoutDelegationToken() throws Exception {
        AgentSessionCreateDTO dto = new AgentSessionCreateDTO();
        dto.setClientType("WEB");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("User-Agent", "JUnit");

        var response = service.createSession(loginUser, dto, request);

        assertThat(response.getAgentSessionId()).isNotBlank();
        assertThat(response.getScopes()).containsExactly(AgentSessionService.SCOPE_WAREHOUSE_READ);
        assertThat(response.getModelDisplayName()).isEqualTo("deepseek v4 flash");
        assertThat(response.getMcpServerName()).isEqualTo("smart_warehouse");
        String json = new ObjectMapper().findAndRegisterModules().writeValueAsString(response);
        assertThat(json).doesNotContain("delegationToken", "token", "Authorization");
        verify(sessionMapper).insert(any(AgentSession.class));
    }

    @Test
    void createSessionCancelsPendingInterruptsForReplacedActiveSessions() {
        AgentSession previous = activeSession();
        previous.setId("old-session");
        when(sessionMapper.selectList(any())).thenReturn(List.of(previous));

        service.createSession(loginUser, new AgentSessionCreateDTO(), new MockHttpServletRequest());

        verify(interruptStateService).cancelSessionInterrupts("old-session", "SESSION_CLOSED_OR_REPLACED");
        verify(sessionMapper).insert(any(AgentSession.class));
    }

    @Test
    void issuesDelegationTokenOnlyThroughInternalServiceMethod() {
        AgentSession session = activeSession();
        when(sessionMapper.selectById("session-1")).thenReturn(session);
        when(jwtUtils.generateAgentDelegationToken(any(), any(), any(), any())).thenReturn("delegated.jwt");

        String token = service.issueDelegationTokenForInternalUse(loginUser, "session-1");

        assertThat(token).isEqualTo("delegated.jwt");
    }

    @Test
    void validateDelegationChecksAgentSessionTableEachRequest() {
        Claims claims = mock(Claims.class);
        AgentSession session = activeSession();
        when(jwtUtils.hasWarehouseMcpAudience(claims)).thenReturn(true);
        when(jwtUtils.getAgentSessionId(claims)).thenReturn("session-1");
        when(jwtUtils.getUserIdFromClaims(claims)).thenReturn(7);
        when(jwtUtils.getScopes(claims)).thenReturn(List.of(AgentSessionService.SCOPE_WAREHOUSE_READ));
        when(sessionMapper.selectById("session-1")).thenReturn(session);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/inventory/stock/page");
        AgentSession result = service.validateDelegation(claims, request, loginUser);

        assertThat(result.getId()).isEqualTo("session-1");
        verify(sessionMapper).selectById("session-1");
    }

    @Test
    void validateDelegationAllowsInventoryDistributionReadOnlyPost() {
        Claims claims = validClaims();
        AgentSession session = activeSession();
        when(sessionMapper.selectById("session-1")).thenReturn(session);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/inventory/distribution");
        AgentSession result = service.validateDelegation(claims, request, loginUser);

        assertThat(result).isSameAs(session);
        verify(sessionMapper).updateById(session);
    }

    @Test
    void validateDelegationAllowsParameterizedQualifiedInventoryReadPostButRejectsNonNumericPath() {
        Claims claims = validClaims();
        AgentSession session = activeSession();
        when(sessionMapper.selectById("session-1")).thenReturn(session);

        assertThat(service.validateDelegation(claims,
                new MockHttpServletRequest("POST", "/api/inventory/qualified-inventory/8/page"), loginUser))
                .isSameAs(session);
        assertThatThrownBy(() -> service.validateDelegation(claims,
                new MockHttpServletRequest("POST", "/api/inventory/qualified-inventory/anything/page"), loginUser))
                .isInstanceOf(AgentSessionAuthenticationException.class)
                .extracting("status")
                .isEqualTo(403);
    }

    @Test
    void validateDelegationAllowsAssayRecordsReadOnlyPost() {
        Claims claims = validClaims();
        AgentSession session = activeSession();
        when(sessionMapper.selectById("session-1")).thenReturn(session);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/assay/records/query");
        AgentSession result = service.validateDelegation(claims, request, loginUser);

        assertThat(result).isSameAs(session);
        verify(sessionMapper).updateById(session);
    }

    @Test
    void validateDelegationAllowsAssayReportDetailReadOnlyPost() {
        Claims claims = validClaims();
        AgentSession session = activeSession();
        when(sessionMapper.selectById("session-1")).thenReturn(session);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/assay/report-detail/query");
        AgentSession result = service.validateDelegation(claims, request, loginUser);

        assertThat(result).isSameAs(session);
        verify(sessionMapper).updateById(session);
    }

    @Test
    void validateDelegationAllowsAssayAbnormalitiesReadOnlyPost() {
        Claims claims = validClaims();
        AgentSession session = activeSession();
        when(sessionMapper.selectById("session-1")).thenReturn(session);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/assay/abnormalities/query");
        AgentSession result = service.validateDelegation(claims, request, loginUser);

        assertThat(result).isSameAs(session);
        verify(sessionMapper).updateById(session);
    }

    @Test
    void validateDelegationAllowsProductsWithoutRecentAssayReadOnlyPost() {
        Claims claims = validClaims();
        AgentSession session = activeSession();
        when(sessionMapper.selectById("session-1")).thenReturn(session);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/assay/products-without-recent-assay/query");
        AgentSession result = service.validateDelegation(claims, request, loginUser);

        assertThat(result).isSameAs(session);
        verify(sessionMapper).updateById(session);
    }

    @Test
    void validateDelegationAllowsAssayStandardCoverageReadOnlyPost() {
        Claims claims = validClaims();
        AgentSession session = activeSession();
        when(sessionMapper.selectById("session-1")).thenReturn(session);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/assay/standard-coverage/query");
        AgentSession result = service.validateDelegation(claims, request, loginUser);

        assertThat(result).isSameAs(session);
        verify(sessionMapper).updateById(session);
    }

    @Test
    void validateDelegationAllowsEveryRegisteredAgentReadPostPath() {
        Claims claims = validClaims();
        AgentSession session = activeSession();
        when(sessionMapper.selectById("session-1")).thenReturn(session);
        List<String> paths = List.of(
                "/api/production/agent-read/entities/resolve",
                "/api/production/agent-read/orders/progress/query",
                "/api/production/agent-read/boiling-batches/trace/query",
                "/api/production/agent-read/orders/material-pick-trace/query",
                "/api/production/agent-read/orders/label-completion/query",
                "/api/production/agent-read/materials/in-process/query",
                "/api/production/agent-read/orders/material-candidates/query",
                "/api/logistics/agent-read/pallet-tasks/query",
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
                "/api/administration/agent-read/employees/query",
                "/api/administration/agent-read/roles/query",
                "/api/administration/agent-read/roles/permission-summary/query",
                "/api/audit/agent-read/operation-logs/query",
                "/api/audit/agent-read/agent-tool-audit/query",
                "/api/audit/agent-read/agent-answer-reviews/query",
                "/api/inventory/agent-read/ledger/query",
                "/api/inventory/agent-read/prepare-pool-balance/query",
                "/api/pallet-codes/agent-read/fixed-product-pool/query");

        for (String path : paths) {
            assertThat(service.validateDelegation(
                    claims, new MockHttpServletRequest("POST", path), loginUser)).isSameAs(session);
        }
    }

    @Test
    void validateDelegationRejectsRevokedSession() {
        Claims claims = validClaims();
        AgentSession session = activeSession();
        session.setStatus("REVOKED");
        when(sessionMapper.selectById("session-1")).thenReturn(session);

        assertThatThrownBy(() -> service.validateDelegation(claims, new MockHttpServletRequest("GET", "/api/products/product"), loginUser))
                .isInstanceOf(AgentSessionAuthenticationException.class)
                .hasMessageContaining("not active");
    }

    @Test
    void validateDelegationRejectsExpiredSession() {
        Claims claims = validClaims();
        AgentSession session = activeSession();
        session.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(sessionMapper.selectById("session-1")).thenReturn(session);

        assertThatThrownBy(() -> service.validateDelegation(claims, new MockHttpServletRequest("GET", "/api/products/product"), loginUser))
                .isInstanceOf(AgentSessionAuthenticationException.class);
    }

    @Test
    void validateDelegationRejectsBusinessWriteMethodForReadScope() {
        Claims claims = validClaims();
        when(sessionMapper.selectById("session-1")).thenReturn(activeSession());

        assertThatThrownBy(() -> service.validateDelegation(claims, new MockHttpServletRequest("POST", "/api/in-stock/add"), loginUser))
                .isInstanceOf(AgentSessionAuthenticationException.class)
                .extracting("status")
                .isEqualTo(403);
    }


    @Test
    void requireActiveInternalToolSessionRestoresCurrentUserAndReadScope() {
        AgentSession session = activeSession();
        when(sessionMapper.selectById("session-1")).thenReturn(session);
        when(userDetailsService.loadUserByUsername("7")).thenReturn(loginUser);

        var access = service.requireActiveInternalToolSession("session-1");

        assertThat(access.session()).isSameAs(session);
        assertThat(access.loginUser()).isSameAs(loginUser);
        verify(sessionMapper).updateById(session);
    }

    @Test
    void requireActiveInternalToolSessionRejectsMissingRevokedAndScopeDeniedSessions() {
        when(sessionMapper.selectById("missing")).thenReturn(null);
        assertThatThrownBy(() -> service.requireActiveInternalToolSession("missing"))
                .isInstanceOf(AgentSessionAuthenticationException.class)
                .extracting("errorCode")
                .isEqualTo("AGENT_SESSION_NOT_FOUND");

        AgentSession revoked = activeSession();
        revoked.setStatus("REVOKED");
        when(sessionMapper.selectById("revoked")).thenReturn(revoked);
        assertThatThrownBy(() -> service.requireActiveInternalToolSession("revoked"))
                .isInstanceOf(AgentSessionAuthenticationException.class)
                .extracting("errorCode")
                .isEqualTo("AGENT_SESSION_INACTIVE");

        AgentSession scopeDenied = activeSession();
        scopeDenied.setScopes("mcp:warehouse:other");
        when(sessionMapper.selectById("scope-denied")).thenReturn(scopeDenied);
        assertThatThrownBy(() -> service.requireActiveInternalToolSession("scope-denied"))
                .isInstanceOf(AgentSessionAuthenticationException.class)
                .extracting("errorCode")
                .isEqualTo("AGENT_SCOPE_DENIED");
    }

    @Test
    void requireActiveInternalToolSessionRejectsDeletedUser() {
        when(sessionMapper.selectById("session-1")).thenReturn(activeSession());
        when(userDetailsService.loadUserByUsername("7")).thenThrow(new BusinessException(404, "user detail"));

        assertThatThrownBy(() -> service.requireActiveInternalToolSession("session-1"))
                .isInstanceOf(AgentSessionAuthenticationException.class)
                .extracting("errorCode")
                .isEqualTo("AGENT_USER_INVALID");
    }
    @Test
    void requireOwnedActiveSessionRejectsOtherUserSession() {
        AgentSession session = activeSession();
        session.setUserId(99);
        when(sessionMapper.selectById("session-1")).thenReturn(session);

        assertThatThrownBy(() -> service.requireOwnedActiveSession(loginUser, "session-1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void requireOwnedActiveSessionRejectsRevokedSessionForMessages() {
        AgentSession session = activeSession();
        session.setStatus("REVOKED");
        when(sessionMapper.selectById("session-1")).thenReturn(session);

        assertThatThrownBy(() -> service.requireOwnedActiveSession(loginUser, "session-1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not active");
    }

    @Test
    void recordToolAuditStoresUpstreamPathInRequestSummaryForUnmigratedSchema() {
        AgentToolAuditDTO dto = new AgentToolAuditDTO();
        dto.setToolName("get_inventory_overview");
        dto.setUpstreamPath("/api/inventory/stock/page");
        dto.setRequestSummary("{\"productId\":84}");
        dto.setResponseSummary("{\"resolutionStatus\":\"UNIQUE\"}");
        dto.setResultCode("SUCCESS");
        dto.setDurationMs(12L);

        service.recordToolAudit("session-1", 7, dto);

        ArgumentCaptor<AgentToolAuditLog> captor = ArgumentCaptor.forClass(AgentToolAuditLog.class);
        verify(toolAuditLogMapper).insert(captor.capture());
        AgentToolAuditLog log = captor.getValue();
        assertThat(log.getRequestSummary()).contains("upstreamPath=/api/inventory/stock/page").contains("productId");
        assertThat(log.getUpstreamPath()).isEqualTo("/api/inventory/stock/page");
    }    private Claims validClaims() {
        Claims claims = mock(Claims.class);
        when(jwtUtils.hasWarehouseMcpAudience(claims)).thenReturn(true);
        when(jwtUtils.getAgentSessionId(claims)).thenReturn("session-1");
        when(jwtUtils.getUserIdFromClaims(claims)).thenReturn(7);
        when(jwtUtils.getScopes(claims)).thenReturn(List.of(AgentSessionService.SCOPE_WAREHOUSE_READ));
        return claims;
    }

    private AgentSession activeSession() {
        AgentSession session = new AgentSession();
        session.setId("session-1");
        session.setUserId(7);
        session.setStatus("ACTIVE");
        session.setScopes(AgentSessionService.SCOPE_WAREHOUSE_READ);
        session.setIssuedAt(LocalDateTime.now().minusMinutes(1));
        session.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        return session;
    }
}




