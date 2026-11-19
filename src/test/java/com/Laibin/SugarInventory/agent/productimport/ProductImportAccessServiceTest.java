package com.Laibin.SugarInventory.agent.productimport;

import com.Laibin.SugarInventory.SpringSecurity.LoginUser;
import com.Laibin.SugarInventory.agent.service.AgentSessionService;
import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.domain.po.Permission;
import com.Laibin.SugarInventory.domain.po.User;
import com.Laibin.SugarInventory.mapper.PermissionMapper;
import com.Laibin.SugarInventory.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.util.Arrays;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProductImportAccessServiceTest {
    private final AgentSessionService sessions = mock(AgentSessionService.class);
    private final UserMapper users = mock(UserMapper.class);
    private final PermissionMapper permissions = mock(PermissionMapper.class);
    private final ProductImportAccessService access = new ProductImportAccessService(sessions, users, permissions);
    private LoginUser principal() {
        User user = new User(); user.setId(7); user.setRoleCode("STAFF");
        when(users.selectById(7)).thenReturn(user);
        return new LoginUser(user,List.of(new SimpleGrantedAuthority("agent:use")));
    }
    private void codes(String... codes) {
        when(permissions.selectPermissionsByRoleCode("STAFF")).thenReturn(Arrays.stream(codes).map(code -> {
            Permission p = new Permission(); p.setPermCode(code); return p;
        }).toList());
    }
    @Test void permitsCreateOnlyOrUpdateOnlyWithProductReadAndAgentAccess() {
        var user = principal();
        for (String code : List.of("product:create","product:update")) {
            codes("agent:use","product:view",code);
            assertEquals(7,access.requireCurrent(user,"session"));
        }
        verify(sessions,times(2)).requireOwnedActiveSession(any(LoginUser.class),eq("session"));
    }
    @Test void revokedWriteReadOrAgentPermissionImmediatelyDenies() {
        var user = principal();
        for (String[] codes : new String[][]{{"agent:use","product:view"},{"agent:use","product:create"},
                {"product:view","product:update"}}) {
            codes(codes);
            assertEquals(403,assertThrows(BusinessException.class,()->access.requireCurrent(user,"session")).getCode());
        }
        verifyNoInteractions(sessions);
    }
    @Test void rejectsRevokedOrForeignSession() {
        var user = principal(); codes("agent:use","product:view","product:create");
        when(sessions.requireOwnedActiveSession(any(),eq("foreign"))).thenThrow(new BusinessException(404,"missing"));
        assertThrows(BusinessException.class,()->access.requireCurrent(user,"foreign"));
    }
    @Test void missingLoginOrDeletedUserCannotReadAttachments() {
        assertThrows(BusinessException.class,()->access.requireCurrent(null,"session"));
        var user = principal(); when(users.selectById(7)).thenReturn(null);
        assertThrows(BusinessException.class,()->access.requireCurrent(user,"session"));
        verifyNoInteractions(sessions);
    }
}
