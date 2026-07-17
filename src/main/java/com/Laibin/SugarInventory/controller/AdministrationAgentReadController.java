package com.Laibin.SugarInventory.controller;

import com.Laibin.SugarInventory.common.Result;
import com.Laibin.SugarInventory.domain.dto.EmployeeRosterAgentQueryDTO;
import com.Laibin.SugarInventory.domain.dto.RoleCatalogAgentQueryDTO;
import com.Laibin.SugarInventory.domain.dto.RolePermissionSummaryAgentQueryDTO;
import com.Laibin.SugarInventory.domain.vo.AdministrationAgentVO;
import com.Laibin.SugarInventory.service.AdministrationAgentReadService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/administration/agent-read")
@RequiredArgsConstructor
public class AdministrationAgentReadController {
    private final AdministrationAgentReadService service;

    @PostMapping("/employees/query")
    @PreAuthorize("hasAuthority('rbac:user:view')")
    public Result<AdministrationAgentVO.EmployeeRosterResult> queryEmployeeRoster(@RequestBody(required = false) EmployeeRosterAgentQueryDTO query) {
        return Result.success(service.queryEmployeeRoster(query));
    }

    @PostMapping("/roles/query")
    @PreAuthorize("hasAuthority('rbac:role:view')")
    public Result<AdministrationAgentVO.RoleCatalogResult> queryRoles(@RequestBody(required = false) RoleCatalogAgentQueryDTO query) {
        return Result.success(service.queryRoles(query));
    }

    @PostMapping("/roles/permission-summary/query")
    @PreAuthorize("hasAuthority('rbac:role:view')")
    public Result<AdministrationAgentVO.RolePermissionSummaryResult> getRolePermissionSummary(@RequestBody RolePermissionSummaryAgentQueryDTO query) {
        return Result.success(service.getRolePermissionSummary(query));
    }
}
