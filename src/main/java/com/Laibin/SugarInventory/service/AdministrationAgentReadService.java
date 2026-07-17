package com.Laibin.SugarInventory.service;

import com.Laibin.SugarInventory.domain.dto.EmployeeRosterAgentQueryDTO;
import com.Laibin.SugarInventory.domain.dto.RoleCatalogAgentQueryDTO;
import com.Laibin.SugarInventory.domain.dto.RolePermissionSummaryAgentQueryDTO;
import com.Laibin.SugarInventory.domain.vo.AdministrationAgentVO;

public interface AdministrationAgentReadService {
    AdministrationAgentVO.EmployeeRosterResult queryEmployeeRoster(EmployeeRosterAgentQueryDTO query);
    AdministrationAgentVO.RoleCatalogResult queryRoles(RoleCatalogAgentQueryDTO query);
    AdministrationAgentVO.RolePermissionSummaryResult getRolePermissionSummary(RolePermissionSummaryAgentQueryDTO query);
}
