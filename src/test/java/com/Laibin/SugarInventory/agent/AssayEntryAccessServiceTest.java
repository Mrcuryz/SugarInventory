package com.Laibin.SugarInventory.agent;

import com.Laibin.SugarInventory.agent.service.AssayEntryAccessService;
import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.domain.po.*;
import com.Laibin.SugarInventory.mapper.*;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AssayEntryAccessServiceTest {
    private final AgentSessionMapper sessions = mock(AgentSessionMapper.class);
    private final UserMapper users = mock(UserMapper.class);
    private final PermissionMapper permissions = mock(PermissionMapper.class);
    private final AssayEntryAccessService service = new AssayEntryAccessService(sessions, users, permissions);

    @Test
    void readsFreshDatabasePermissionsAndRefusesRevocation() {
        var session = new AgentSession(); session.setUserId(7); session.setStatus("ACTIVE"); session.setExpiresAt(LocalDateTime.now().plusDays(1));
        when(sessions.selectForAssayEntry("s")).thenReturn(session);
        var user = new User(); user.setId(7); user.setRoleCode("QC"); when(users.selectById(7)).thenReturn(user);
        when(permissions.selectPermissionsByRoleCode("QC")).thenReturn(codes("agent:use", "assay:view", "quality:test"));
        assertThat(service.requireCurrent(7, "s")).isSameAs(user);
        when(permissions.selectPermissionsByRoleCode("QC")).thenReturn(codes("agent:use", "assay:view"));
        assertThatThrownBy(() -> service.requireCurrent(7, "s")).isInstanceOf(BusinessException.class).extracting("code").isEqualTo(1056);
        verify(users, times(2)).selectById(7);
    }

    @Test
    void wrongOwnerExpiredOrRevokedSessionFailsBeforeUserQuery() {
        var session = new AgentSession(); session.setUserId(8); session.setStatus("ACTIVE"); session.setExpiresAt(LocalDateTime.now().plusDays(1));
        when(sessions.selectForAssayEntry("s")).thenReturn(session);
        assertThatThrownBy(() -> service.requireCurrent(7, "s")).isInstanceOf(BusinessException.class);
        session.setUserId(7); session.setExpiresAt(LocalDateTime.now().minusDays(1));
        assertThatThrownBy(() -> service.requireCurrent(7, "s")).isInstanceOf(BusinessException.class);
        session.setExpiresAt(LocalDateTime.now().plusDays(1)); session.setStatus("REVOKED");
        assertThatThrownBy(() -> service.requireCurrent(7, "s")).isInstanceOf(BusinessException.class);
        verifyNoInteractions(users, permissions);
    }

    private static List<Permission> codes(String... codes) {
        return java.util.Arrays.stream(codes).map(code -> { var permission = new Permission(); permission.setPermCode(code); return permission; }).toList();
    }

    @Test void recoveryAllowsOwnedExpiredSessionButNotLostPermissionsOrOtherOwner() {
        var session = new AgentSession(); session.setUserId(7); session.setStatus("REVOKED"); session.setExpiresAt(LocalDateTime.now().minusDays(1));
        when(sessions.selectForAssayEntry("s")).thenReturn(session);
        var user = new User(); user.setId(7); user.setRoleCode("QC"); when(users.selectById(7)).thenReturn(user);
        when(permissions.selectPermissionsByRoleCode("QC")).thenReturn(codes("agent:use", "assay:view", "quality:test"));
        assertThat(service.requireRecovery(7, "s")).isSameAs(user);
        assertThatThrownBy(() -> service.requireCurrent(7, "s")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.requireRecovery(8, "s")).isInstanceOf(BusinessException.class);
        when(permissions.selectPermissionsByRoleCode("QC")).thenReturn(codes("agent:use", "assay:view"));
        assertThatThrownBy(() -> service.requireRecovery(7, "s")).isInstanceOf(BusinessException.class).extracting("code").isEqualTo(1056);
    }
}
