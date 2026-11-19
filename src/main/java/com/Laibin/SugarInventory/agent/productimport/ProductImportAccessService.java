package com.Laibin.SugarInventory.agent.productimport;

import com.Laibin.SugarInventory.SpringSecurity.LoginUser;
import com.Laibin.SugarInventory.agent.security.AgentAccessPolicy;
import com.Laibin.SugarInventory.agent.service.AgentSessionService;
import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.domain.po.Permission;
import com.Laibin.SugarInventory.mapper.PermissionMapper;
import com.Laibin.SugarInventory.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;

/** Upload/read access only. Execution must separately check each selected operation's permission. */
@Service
@RequiredArgsConstructor
public class ProductImportAccessService {
    private final AgentSessionService sessions;
    private final UserMapper users;
    private final PermissionMapper permissions;

    public Integer requireCurrent(LoginUser principal, String sessionId) {
        AgentAccessPolicy.requireAgentAccess(principal);
        if (sessionId == null || sessionId.isBlank() || sessionId.length() > 64)
            throw new BusinessException(400, "会话信息无效，请重新打开助手");
        Integer owner = principal.getUser().getId();
        var user = users.selectById(owner);
        if (user == null || user.getRoleCode() == null) throw denied();
        var codes = permissions.selectPermissionsByRoleCode(user.getRoleCode()).stream()
                .map(Permission::getPermCode).toList();
        var fresh = new LoginUser(user, codes.stream().map(SimpleGrantedAuthority::new).toList());
        if (!AgentAccessPolicy.canUseAgent(fresh) || !codes.contains("product:view")
                || !(codes.contains("product:create") || codes.contains("product:update"))) throw denied();
        sessions.requireOwnedActiveSession(fresh, sessionId);
        return owner;
    }

    private BusinessException denied() {
        return new BusinessException(403, "当前账号需要产品查看权限，以及新增或修改产品的权限");
    }
}
