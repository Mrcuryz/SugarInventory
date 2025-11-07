package com.Laibin.SugarInventory.controller;

import com.Laibin.SugarInventory.SpringSecurity.LoginUser;
import com.Laibin.SugarInventory.annotation.LogOperation;
import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.common.PageResult;
import com.Laibin.SugarInventory.common.Result;
import com.Laibin.SugarInventory.domain.dto.AssayGroupQueryDTO;
import com.Laibin.SugarInventory.domain.dto.AssayGroupSubmitDTO;
import com.Laibin.SugarInventory.domain.dto.AssayQueryDTO;
import com.Laibin.SugarInventory.domain.enumObject.OperationType;
import com.Laibin.SugarInventory.domain.po.AssayGroup;
import com.Laibin.SugarInventory.domain.vo.AssayGroupVO;
import com.Laibin.SugarInventory.domain.vo.AssayVO;
import com.Laibin.SugarInventory.service.AssayGroupService;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/assayGroup")
@Tag(name = "化验验收标准管理", description = "化验验收标准管理")
public class AssayGroupController {
    @Autowired
    private AssayGroupService assayGroupService;

    @PostMapping("/add")
    @LogOperation(value = "化验验收标准", type = OperationType.INSERT)
    @Operation(summary = "保存化验验收标准", description = "保存化验验收标准")
    public Result<Boolean> addAssays(
            @RequestBody AssayGroupSubmitDTO dto,
            @AuthenticationPrincipal LoginUser loginUser
    ) {
        try {
            assayGroupService.addAssays(dto, loginUser.getUser().getId());
            return Result.success(true);
        } catch (BusinessException e) {
            e.printStackTrace();
            return Result.error(500, "化验记录添加失败：" + e.getMessage());
        }
    }

    @Operation(summary = "查询化验验收标准数据", description = "根据查询条件分页查询化验验收标准数据")
    @PostMapping("/query")
    public Result<PageResult<AssayGroupVO>> queryAssaysGroup(
            @RequestBody AssayGroupQueryDTO query
    ) {
        try {
            return Result.success(assayGroupService.queryAssays(query));
        } catch (BusinessException e) {
            e.printStackTrace();
            return Result.error(500, "化验验收标准数据查询失败：" + e.getMessage());
        }
    }

    @Operation(summary = "更新化验验收标准数据", description = "根据化验验收标准ID更新化验验收标准数据")
    @LogOperation(value = "化验验收标准数据", type = OperationType.INSERT)
    @PostMapping("/{id}")
    public Result<AssayGroup> updateAssayGroup(
            @PathVariable("id") Integer id,
            @RequestBody AssayGroupSubmitDTO dto,
            @AuthenticationPrincipal LoginUser loginUser
    ) {
        try {
            return Result.success(assayGroupService.updateAssay(id, dto, loginUser.getUser()));
        } catch (BusinessException e) {
            return Result.error(500, "化验记录更新失败：" + e.getMessage());
        }
    }

    @Operation(summary = "删除化验验收标准数据", description = "根据ID删除化验验收标准数据")
    @LogOperation(value = "化验验收标准数据", type = OperationType.DELETE)
    @DeleteMapping("/{id}")
    public Result<Boolean> deleteAssay(@PathVariable("id") Integer id) {
        try {
            assayGroupService.deleteAssay(id);
            return Result.success(true);
        } catch (BusinessException e) {
            return Result.error(500, "化验验收标准数据删除失败：" + e.getMessage());
        }
    }
}
