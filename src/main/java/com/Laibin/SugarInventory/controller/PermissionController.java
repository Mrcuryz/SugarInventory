package com.Laibin.SugarInventory.controller;

import com.Laibin.SugarInventory.common.Result;
import com.Laibin.SugarInventory.domain.vo.PermissionVO;
import com.Laibin.SugarInventory.service.PermissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rbac/permissions")
@Tag(name = "权限管理", description = "系统权限点查询接口")
public class PermissionController {
    @Autowired
    private PermissionService permissionService;

    @GetMapping
    @PreAuthorize("hasAuthority('rbac:role:view')")
    @Operation(summary = "查询权限点列表")
    public Result<List<PermissionVO>> listPermissions() {
        return Result.success(permissionService.listPermissionVOs());
    }
}
