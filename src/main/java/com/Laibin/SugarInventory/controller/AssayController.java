package com.Laibin.SugarInventory.controller;

import com.Laibin.SugarInventory.SpringSecurity.LoginUser;
import com.Laibin.SugarInventory.annotation.LogOperation;
import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.common.PageResult;
import com.Laibin.SugarInventory.common.Result;
import com.Laibin.SugarInventory.domain.dto.AssayCheckDTO;
import com.Laibin.SugarInventory.domain.dto.AssayQueryDTO;
import com.Laibin.SugarInventory.domain.dto.AssaySubmitDTO;
import com.Laibin.SugarInventory.domain.enumObject.OperationType;
import com.Laibin.SugarInventory.domain.vo.AssayVO;
import com.Laibin.SugarInventory.service.AssayService;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/assay")
@Tag(name = "化验记录管理", description = "包括导入、查询、详情、更新、删除等接口")
public class AssayController {

    @Autowired
    private AssayService assayService;

    @PostMapping("/import")
    @LogOperation(value = "化验数据", type = OperationType.INSERT)
    @Operation(summary = "导入化验记录", description = "批量导入化验记录")
    @PreAuthorize("hasAuthority('quality:test')")
    public Result<Boolean> importAssays(@RequestBody List<AssaySubmitDTO> dtos,
                                        @AuthenticationPrincipal LoginUser loginUser) {
        try {
            assayService.importAssays(dtos, loginUser.getUser().getId());
            return Result.success(true);
        } catch (BusinessException e) {
            return Result.error(500, "化验记录导入失败：" + e.getMessage());
        }
    }

    @Operation(summary = "检查化验记录是否存在", description = "根据产品ID和日期检查化验记录是否存在")
    @PostMapping("/exists")
    public Result<Boolean> exists(@RequestBody AssayCheckDTO dto) {
        try {
            return Result.success(assayService.existedAssay(dto));
        } catch (BusinessException e) {
            return Result.error(500, "化验记录检查失败：" + e.getMessage());
        }
    }

    @PreAuthorize("hasAuthority('quality:test')")
    @Operation(summary = "查询化验记录", description = "根据查询条件分页查询化验记录")
    @PostMapping("/query")
    public Result<PageResult<AssayVO>> queryAssays(@RequestBody AssayQueryDTO query) {
        try {
            return Result.success(assayService.queryAssays(query));
        } catch (BusinessException e) {
            return Result.error(500, "化验记录查询失败：" + e.getMessage());
        }
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询化验详情", description = "根据化验记录ID查询判定详情")
    public Result<AssayVO> getAssayById(@PathVariable Integer id) {
        try {
            return Result.success(assayService.getAssayById(id));
        } catch (BusinessException e) {
            return Result.error(500, "化验详情查询失败：" + e.getMessage());
        }
    }

    @PostMapping("/{id}")
    @LogOperation(value = "化验数据", type = OperationType.UPDATE)
    @Operation(summary = "更新化验记录", description = "根据化验记录ID更新化验数据")
    @PreAuthorize("hasAuthority('quality:test')")
    public Result<AssayVO> updateAssay(@PathVariable("id") Integer id,
                                       @RequestBody AssaySubmitDTO dto,
                                       @AuthenticationPrincipal LoginUser loginUser) {
        try {
            return Result.success(assayService.updateAssay(id, dto, loginUser.getUser()));
        } catch (BusinessException | JsonProcessingException e) {
            return Result.error(500, "化验记录更新失败：" + e.getMessage());
        }
    }

    @Operation(summary = "删除化验记录", description = "根据化验记录ID删除化验记录")
    @LogOperation(value = "化验数据", type = OperationType.DELETE)
    @PreAuthorize("hasAuthority('quality:test')")
    @DeleteMapping("/{id}")
    public Result<Boolean> deleteAssay(@PathVariable("id") Integer id) {
        try {
            assayService.deleteAssay(id);
            return Result.success(true);
        } catch (BusinessException e) {
            return Result.error(500, "化验记录删除失败：" + e.getMessage());
        }
    }
}