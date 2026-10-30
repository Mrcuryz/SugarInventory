package com.Laibin.SugarInventory.agent.service;

import com.Laibin.SugarInventory.SpringSecurity.LoginUser;
import com.Laibin.SugarInventory.agent.security.AgentAccessPolicy;
import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.domain.enumObject.ErrorCode;
import com.Laibin.SugarInventory.domain.po.Permission;
import com.Laibin.SugarInventory.domain.po.User;
import com.Laibin.SugarInventory.mapper.AgentSessionMapper;
import com.Laibin.SugarInventory.mapper.PermissionMapper;
import com.Laibin.SugarInventory.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class AssayEntryAccessService {
    private final AgentSessionMapper sessions;
    private final UserMapper users;
    private final PermissionMapper permissions;

    /** Session lock also serializes replacement/cancellation/execution for one conversation. */
    public User requireCurrent(Integer owner, String sessionId) {
        return requireOwned(owner, sessionId, true);
    }

    /** Recovery may close an old operation, never confirm or execute it. Still locks the same owned session and reads fresh permissions. */
    public User requireRecovery(Integer owner, String sessionId) {
        return requireOwned(owner, sessionId, false);
    }

    private User requireOwned(Integer owner, String sessionId, boolean activeRequired) {
        if (owner == null || owner <= 0 || sessionId == null || sessionId.isBlank() || sessionId.length() > 64) {
            throw new BusinessException(ErrorCode.ASSAY_ENTRY_SESSION_INVALID);
        }
        var session = sessions.selectForAssayEntry(sessionId);
        if (session == null || !Objects.equals(session.getUserId(), owner)
                || (activeRequired && (!"ACTIVE".equals(session.getStatus())
                || session.getExpiresAt() == null || !session.getExpiresAt().isAfter(LocalDateTime.now(ZoneId.of("Asia/Shanghai")))))) {
            throw new BusinessException(ErrorCode.ASSAY_ENTRY_SESSION_INVALID);
        }
        // Query by immutable user id; do not reuse cached JWT authorities or treat a numeric id as an employee number.
        User user = users.selectById(owner);
        if (user == null || user.getRoleCode() == null) throw new BusinessException(ErrorCode.ASSAY_ENTRY_PERMISSION_DENIED);
        var codes = permissions.selectPermissionsByRoleCode(user.getRoleCode()).stream().map(Permission::getPermCode).toList();
        var fresh = new LoginUser(user, codes.stream().map(SimpleGrantedAuthority::new).toList());
        if (!AgentAccessPolicy.canUseAgent(fresh) || !codes.contains("assay:view")
                || !(codes.contains("assay:create") || codes.contains("quality:test"))) {
            throw new BusinessException(ErrorCode.ASSAY_ENTRY_PERMISSION_DENIED);
        }
        return user;
    }
}
