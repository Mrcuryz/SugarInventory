package com.Laibin.SugarInventory.controller;

import com.Laibin.SugarInventory.annotation.LogOperation;
import com.Laibin.SugarInventory.common.PageResult;
import com.Laibin.SugarInventory.common.Result;
import com.Laibin.SugarInventory.domain.dto.QualityStandardDTO;
import com.Laibin.SugarInventory.domain.enumObject.OperationType;
import com.Laibin.SugarInventory.domain.po.QualityStandard;
import com.Laibin.SugarInventory.domain.vo.QualityStandardVO;
import com.Laibin.SugarInventory.service.QualityStandardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/quality-standards")
@RequiredArgsConstructor
@Tag(name = "化验标准管理", description = "提供化验标准的增删改查接口")
public class QualityStandardController {

    private final QualityStandardService qualityStandardService;

    @Operation(summary = "查询所有化验标准", description = "可按产品类型、标准名称和状态筛选")
    @GetMapping("/list")
    public Result<List<QualityStandardVO>> listQualityStandards(
            @RequestParam(value = "productType", required = false) String productType,
            @RequestParam(value = "standardName", required = false) String standardName,
            @RequestParam(value = "status", required = false) String status) {
        return Result.success(qualityStandardService.listQualityStandards(productType, standardName, status));
    }

    @Operation(summary = "分页查询化验标准", description = "用于化验标准管理页的分页列表")
    @GetMapping("/list/page")
    public Result<PageResult<QualityStandardVO>> pageQualityStandards(
            @RequestParam(value = "productType", required = false) String productType,
            @RequestParam(value = "standardName", required = false) String standardName,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", required = false, defaultValue = "1") Integer page,
            @RequestParam(value = "size", required = false, defaultValue = "10") Integer size) {
        return Result.success(qualityStandardService.pageQualityStandards(productType, standardName, status, page, size));
    }

    @Operation(summary = "查询化验标准详情", description = "根据 ID 获取化验标准")
    @GetMapping("/{id}")
    public Result<QualityStandardVO> getQualityStandardById(@PathVariable Integer id) {
        return Result.success(qualityStandardService.getQualityStandardById(id));
    }

    @Operation(summary = "新增化验标准", description = "创建新的化验标准")
    @LogOperation(value = "化验标准", type = OperationType.INSERT)
    @PostMapping("/add")
    public Result<QualityStandard> addQualityStandard(@RequestBody @Valid QualityStandardDTO dto) {
        return Result.success(qualityStandardService.addQualityStandard(dto));
    }

    @Operation(summary = "更新化验标准", description = "根据 ID 修改化验标准")
    @LogOperation(value = "化验标准", type = OperationType.UPDATE)
    @PutMapping("/update/{id}")
    public Result<QualityStandard> updateQualityStandard(@PathVariable Integer id, @RequestBody @Valid QualityStandardDTO dto) {
        return Result.success(qualityStandardService.updateQualityStandard(id, dto));
    }

    @Operation(summary = "删除化验标准", description = "若标准已被产品使用，会返回业务提示")
    @LogOperation(value = "化验标准", type = OperationType.DELETE)
    @DeleteMapping("/delete/{id}")
    public Result<String> deleteQualityStandard(@PathVariable Integer id) {
        qualityStandardService.deleteQualityStandard(id);
        return Result.success("删除成功");
    }

    @Operation(summary = "强制删除化验标准", description = "管理员可强制删除标准，并清理关联产品关系")
    @LogOperation(value = "化验标准", type = OperationType.DELETE)
    @DeleteMapping("/delete/{id}/force")
    @PreAuthorize("hasAuthority('product:delete')")
    public Result<String> forceDeleteQualityStandard(@PathVariable Integer id) {
        qualityStandardService.forceDeleteQualityStandard(id);
        return Result.success("强制删除成功");
    }
}
