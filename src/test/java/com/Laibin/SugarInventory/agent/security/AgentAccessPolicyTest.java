package com.Laibin.SugarInventory.agent.security;

import com.Laibin.SugarInventory.SpringSecurity.LoginUser;
import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.domain.po.User;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentAccessPolicyTest {
    @Test
    void allowsAdminAndSuperAdminRoles() {
        assertThat(AgentAccessPolicy.isAdmin(loginUser("ADMIN"))).isTrue();
        assertThat(AgentAccessPolicy.isAdmin(loginUser("SUPER_ADMIN"))).isTrue();
        assertThat(AgentAccessPolicy.isAdmin(loginUser(" super_admin "))).isTrue();
        assertThatCode(() -> AgentAccessPolicy.requireAdmin(loginUser("ADMIN"))).doesNotThrowAnyException();
    }

    @Test
    void rejectsNonAdminRoles() {
        for (String roleCode : List.of("STAFF", "QC", "WAREHOUSE", "")) {
            assertThat(AgentAccessPolicy.isAdmin(loginUser(roleCode))).isFalse();
            assertThatThrownBy(() -> AgentAccessPolicy.requireAdmin(loginUser(roleCode)))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(403);
        }
    }

    @Test
    void allowsNonAdminOnlyWithExplicitAgentUsePermission() {
        LoginUser qualityUser = loginUser("QC", AgentAccessPolicy.AGENT_USE_PERMISSION);
        assertThat(AgentAccessPolicy.canUseAgent(qualityUser)).isTrue();
        assertThatCode(() -> AgentAccessPolicy.requireAgentAccess(qualityUser)).doesNotThrowAnyException();

        LoginUser staffUser = loginUser("STAFF");
        assertThat(AgentAccessPolicy.canUseAgent(staffUser)).isFalse();
        assertThatThrownBy(() -> AgentAccessPolicy.requireAgentAccess(staffUser))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(403);
    }

    @Test
    void rejectsMissingAuthentication() {
        assertThatThrownBy(() -> AgentAccessPolicy.requireAdmin(null))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(401);

        LoginUser missingUser = new LoginUser(null, List.of());
        assertThatThrownBy(() -> AgentAccessPolicy.requireAdmin(missingUser))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(401);
    }

    private LoginUser loginUser(String roleCode) {
        return loginUser(roleCode, new String[0]);
    }

    private LoginUser loginUser(String roleCode, String... permissionCodes) {
        User user = new User();
        user.setId(7);
        user.setRoleCode(roleCode);
        return new LoginUser(user, List.of(permissionCodes).stream()
                .map(SimpleGrantedAuthority::new)
                .toList());
    }
}
