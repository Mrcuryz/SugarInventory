package com.Laibin.SugarInventory.controller;

import com.Laibin.SugarInventory.annotation.LogOperation;
import com.Laibin.SugarInventory.common.PageResult;
import com.Laibin.SugarInventory.common.Result;
import com.Laibin.SugarInventory.domain.dto.RolePermissionUpdateDTO;
import com.Laibin.SugarInventory.domain.dto.RoleQueryDTO;
import com.Laibin.SugarInventory.domain.dto.RoleSaveDTO;
import com.Laibin.SugarInventory.domain.dto.RoleStatusUpdateDTO;
import com.Laibin.SugarInventory.domain.enumObject.OperationType;
import com.Laibin.SugarInventory.domain.vo.RoleOptionVO;
import com.Laibin.SugarInventory.domain.vo.RoleVO;
import com.Laibin.SugarInventory.service.RoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rbac/roles")
@Tag(name = "角色管理", description = "角色与权限分配接口")
public class RoleController {
    @Autowired
    private RoleService roleService;

    @PostMapping("/query")
    @PreAuthorize("hasAuthority('rbac:role:view')")
    @Operation(summary = "分页查询角色")
    public Result<PageResult<RoleVO>> queryRoles(@RequestBody RoleQueryDTO queryDTO) {
        return Result.success(roleService.queryRoles(queryDTO));
    }

    @GetMapping("/options")
    @PreAuthorize("hasAnyAuthority('rbac:user:view','rbac:role:view')")
    @Operation(summary = "查询启用中的角色选项")
    public Result<List<RoleOptionVO>> listRoleOptions() {
        return Result.success(roleService.listEnabledRoleOptions());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('rbac:role:view')")
    @Operation(summary = "查询角色详情")
    public Result<RoleVO> getRoleDetail(@PathVariable Integer id) {
        return Result.success(roleService.getRoleDetail(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('rbac:role:create')")
    @LogOperation(value = "role", type = OperationType.INSERT)
    @Operation(summary = "新增角色")
    public Result<RoleVO> createRole(@Valid @RequestBody RoleSaveDTO dto) {
        return Result.success(roleService.createRole(dto));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('rbac:role:update')")
    @LogOperation(value = "role", type = OperationType.UPDATE)
    @Operation(summary = "更新角色")
    public Result<RoleVO> updateRole(@PathVariable Integer id, @Valid @RequestBody RoleSaveDTO dto) {
        return Result.success(roleService.updateRole(id, dto));
    }

    @PutMapping("/{id}/permissions")
    @PreAuthorize("hasAuthority('rbac:role:assign_permission')")
    @LogOperation(value = "role", type = OperationType.UPDATE)
    @Operation(summary = "分配角色权限")
    public Result<Boolean> updateRolePermissions(@PathVariable Integer id, @RequestBody RolePermissionUpdateDTO dto) {
        roleService.updateRolePermissions(id, dto);
        return Result.success(true);
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('rbac:role:update')")
    @LogOperation(value = "role", type = OperationType.UPDATE)
    @Operation(summary = "更新角色状态")
    public Result<Boolean> updateRoleStatus(@PathVariable Integer id, @RequestBody RoleStatusUpdateDTO dto) {
        roleService.updateRoleStatus(id, dto.getStatus());
        return Result.success(true);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('rbac:role:delete')")
    @LogOperation(value = "role", type = OperationType.DELETE)
    @Operation(summary = "删除角色")
    public Result<Boolean> deleteRole(@PathVariable Integer id) {
        roleService.deleteRole(id);
        return Result.success(true);
    }
}
