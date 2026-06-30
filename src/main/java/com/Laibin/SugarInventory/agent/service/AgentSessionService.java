package com.Laibin.SugarInventory.agent.service;

import com.Laibin.SugarInventory.SpringSecurity.LoginUser;
import com.Laibin.SugarInventory.agent.dto.AgentSessionCreateDTO;
import com.Laibin.SugarInventory.agent.dto.AgentToolAuditDTO;
import com.Laibin.SugarInventory.agent.vo.AgentSessionVO;
import com.Laibin.SugarInventory.domain.po.AgentSession;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;

public interface AgentSessionService {
    String SCOPE_WAREHOUSE_READ = "mcp:warehouse:read";

    AgentSessionVO createSession(LoginUser loginUser, AgentSessionCreateDTO dto, HttpServletRequest request);

    List<AgentSessionVO> listCurrentSessions(LoginUser loginUser);

    AgentSession requireOwnedActiveSession(LoginUser loginUser, String agentSessionId);

    InternalAgentSessionAccess requireActiveInternalToolSession(String agentSessionId);

    AgentSessionVO toSessionVO(AgentSession session, LoginUser loginUser);

    void revokeSession(LoginUser loginUser, String agentSessionId, String revokedReason);

    String issueDelegationTokenForInternalUse(LoginUser loginUser, String agentSessionId);

    AgentSession validateDelegation(Claims claims, HttpServletRequest request, UserDetails userDetails);

    void recordApiAudit(String agentSessionId, Integer userId, String toolName, String method, String path,
                        int responseStatus, String resultCode, String errorCode, long durationMs);

    String recordToolAudit(String agentSessionId, Integer userId, AgentToolAuditDTO dto);
}

