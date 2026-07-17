package com.Laibin.SugarInventory.service;

import com.Laibin.SugarInventory.common.PageResult;
import com.Laibin.SugarInventory.domain.dto.EmployeeRosterAgentQueryDTO;
import com.Laibin.SugarInventory.domain.dto.RolePermissionSummaryAgentQueryDTO;
import com.Laibin.SugarInventory.domain.po.EmployeeRoster;
import com.Laibin.SugarInventory.domain.po.Permission;
import com.Laibin.SugarInventory.domain.vo.RoleVO;
import com.Laibin.SugarInventory.mapper.PermissionMapper;
import com.Laibin.SugarInventory.service.impl.AdministrationAgentReadServiceImpl;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdministrationAgentReadServiceImplTest {
    @Test
    void employeeRosterMasksMobileAndOmitsCredentialData() {
        EmployeeService employees = mock(EmployeeService.class);
        EmployeeRoster row = new EmployeeRoster(); row.setId(99); row.setEmployeeId("E001"); row.setName("张三");
        row.setMobile("13812345678"); row.setDepartment("仓储部"); row.setStatus("在职"); row.setRoleCode("WAREHOUSE");
        when(employees.queryEmployee(any())).thenReturn(new PageResult<>(1L, List.of(row)));
        var service = new AdministrationAgentReadServiceImpl(employees, mock(RoleService.class), mock(PermissionMapper.class));

        var result = service.queryEmployeeRoster(new EmployeeRosterAgentQueryDTO());

        assertThat(result.getRecords()).singleElement().satisfies(item -> {
            assertThat(item.getEmployeeId()).isEqualTo("E001");
            assertThat(item.getMaskedMobile()).isEqualTo("138****5678");
        });
        assertThat(result.getLimitations()).anyMatch(value -> value.contains("登录凭据"));
    }

    @Test
    void permissionSummaryRequiresExactRoleAndReturnsNoInternalIds() {
        RoleService roles = mock(RoleService.class); PermissionMapper permissions = mock(PermissionMapper.class);
        RoleVO role = new RoleVO(); role.setId(17); role.setRoleCode("WAREHOUSE"); role.setRoleName("仓库员");
        role.setStatus("ENABLED"); role.setUserCount(2);
        when(roles.queryRoles(any())).thenReturn(new PageResult<>(1L, List.of(role)));
        Permission permission = new Permission(); permission.setId(42); permission.setPermCode("inventory:view");
        permission.setPermName("查看库存");
        when(permissions.selectPermissionsByRoleCode("WAREHOUSE")).thenReturn(List.of(permission));
        var service = new AdministrationAgentReadServiceImpl(mock(EmployeeService.class), roles, permissions);
        var query = new RolePermissionSummaryAgentQueryDTO(); query.setRoleCodeOrName("WAREHOUSE");

        var result = service.getRolePermissionSummary(query);

        assertThat(result.getPermissions()).singleElement().satisfies(item -> {
            assertThat(item.getPermissionCode()).isEqualTo("inventory:view");
            assertThat(item.getPermissionGroup()).isEqualTo("INVENTORY");
        });
        assertThat(result.getLimitations()).anyMatch(value -> value.contains("内部权限 ID"));
    }
}
