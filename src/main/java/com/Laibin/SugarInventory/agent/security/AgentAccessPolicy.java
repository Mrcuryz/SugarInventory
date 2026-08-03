package com.Laibin.SugarInventory.agent.security;

import com.Laibin.SugarInventory.SpringSecurity.LoginUser;
import com.Laibin.SugarInventory.common.BusinessException;

import java.util.Locale;
import java.util.Set;

public final class AgentAccessPolicy {
    private static final Set<String> ADMIN_ROLE_CODES = Set.of("ADMIN", "SUPER_ADMIN");

    private AgentAccessPolicy() {
    }

    public static boolean isAdmin(LoginUser loginUser) {
        if (loginUser == null || loginUser.getUser() == null) {
            return false;
        }
        String roleCode = loginUser.getUser().getRoleCode();
        return roleCode != null
                && ADMIN_ROLE_CODES.contains(roleCode.trim().toUpperCase(Locale.ROOT));
    }

    public static void requireAdmin(LoginUser loginUser) {
        if (loginUser == null || loginUser.getUser() == null || loginUser.getUser().getId() == null) {
            throw new BusinessException(401, "Authentication is required.");
        }
        if (!isAdmin(loginUser)) {
            throw new BusinessException(403, "仅管理员可使用 AI 助手。");
        }
    }
}
