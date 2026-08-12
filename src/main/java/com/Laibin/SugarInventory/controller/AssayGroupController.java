package com.Laibin.SugarInventory.controller;

import com.Laibin.SugarInventory.SpringSecurity.LoginUser;
import com.Laibin.SugarInventory.annotation.LogOperation;
import com.Laibin.SugarInventory.common.PageResult;
import com.Laibin.SugarInventory.common.Result;
import com.Laibin.SugarInventory.domain.dto.AssayGroupQueryDTO;
import com.Laibin.SugarInventory.domain.dto.AssayGroupSubmitDTO;
import com.Laibin.SugarInventory.domain.enumObject.OperationType;
import com.Laibin.SugarInventory.domain.vo.AssayGroupVO;
import com.Laibin.SugarInventory.service.AssayGroupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/assayGroup")
@Tag(name = "批量化验组管理", description = "批量化验组管理")
@PreAuthorize("hasAuthority('assay_group:view')")
public class AssayGroupController {
    @Autowired
    private AssayGroupService assayGroupService;

    @PostMapping("/add")
    @PreAuthorize("hasAuthority('assay_group:create')")
    @LogOperation(value = "批量化验组", type = OperationType.INSERT)
    @Operation(summary = "保存批量化验组", description = "保存批量化验组")
    public Result<Boolean> addAssays(
            @RequestBody AssayGroupSubmitDTO dto,
            @AuthenticationPrincipal LoginUser loginUser
    ) {
        assayGroupService.addAssays(dto, loginUser.getUser().getId());
        return Result.success(true);
    }

    @Operation(summary = "查询批量化验组数据", description = "根据查询条件分页查询批量化验组数据")
    @PostMapping("/query")
    public Result<PageResult<AssayGroupVO>> queryAssaysGroup(
            @RequestBody AssayGroupQueryDTO query
    ) {
        return Result.success(assayGroupService.queryAssays(query));
    }

    @Operation(summary = "更新批量化验组数据", description = "根据批量化验组 ID 更新批量化验组数据")
    @LogOperation(value = "批量化验组数据", type = OperationType.UPDATE)
    @PostMapping("/{id}")
    @PreAuthorize("hasAuthority('assay_group:update')")
    public Result<AssayGroupVO> updateAssayGroup(
            @PathVariable("id") Integer id,
            @RequestBody AssayGroupSubmitDTO dto,
            @AuthenticationPrincipal LoginUser loginUser
    ) {
        return Result.success(assayGroupService.updateAssay(id, dto, loginUser.getUser()));
    }

    @Operation(summary = "删除批量化验组数据", description = "根据 ID 删除批量化验组数据")
    @LogOperation(value = "批量化验组数据", type = OperationType.DELETE)
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('assay_group:delete')")
    public Result<Boolean> deleteAssay(@PathVariable("id") Integer id) {
        assayGroupService.deleteAssay(id);
        return Result.success(true);
    }
}
