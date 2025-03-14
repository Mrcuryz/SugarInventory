package com.Laibin.SugarInventory.controller;

import com.Laibin.SugarInventory.annotation.LogOperation;
import com.Laibin.SugarInventory.common.Result;
import com.Laibin.SugarInventory.domain.dto.QualityStandardDTO;
import com.Laibin.SugarInventory.domain.enumObject.OperationType;
import com.Laibin.SugarInventory.domain.po.QualityStandard;
import com.Laibin.SugarInventory.domain.vo.QualityStandardVO;
import com.Laibin.SugarInventory.service.QualityStandardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/quality-standards")
@Tag(name = "质量标准管理", description = "提供增删改查质量标准的接口")
public class QualityStandardController {

    @Autowired
    private QualityStandardService qualityStandardService;

    @Operation(summary = "查询所有质量标准", description = "可按产品类型筛选")
    @GetMapping("/list")
    public Result<List<QualityStandardVO>> listQualityStandards(
            @RequestParam(value = "productType", required = false) String productType,
            @RequestParam(value = "standardName", required = false) String standardName) {
        return Result.success(qualityStandardService.listQualityStandards(productType, standardName));
    }

    @Operation(summary = "查询质量标准", description = "根据ID获取质量标准")
    @GetMapping("/{id}")
    public Result<QualityStandardVO> getQualityStandardById(@PathVariable Integer id) {
        return Result.success(qualityStandardService.getQualityStandardById(id));
    }

    @Operation(summary = "新增质量标准", description = "创建新的质量标准")
    @LogOperation(value = "检验标准", type = OperationType.INSERT)
    @PostMapping("/add")
    public Result<QualityStandard> addQualityStandard(@RequestBody @Valid QualityStandardDTO dto) {
        return Result.success(qualityStandardService.addQualityStandard(dto));
    }

    @Operation(summary = "更新质量标准", description = "根据ID修改质量标准")
    @LogOperation(value = "检验标准", type = OperationType.UPDATE)
    @PutMapping("/update/{id}")
    public Result<QualityStandard> updateQualityStandard(@PathVariable Integer id, @RequestBody @Valid QualityStandardDTO dto) {
        try {
            return Result.success(qualityStandardService.updateQualityStandard(id, dto));
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @Operation(summary = "删除质量标准", description = "根据ID删除质量标准")
    @LogOperation(value = "检验标准", type = OperationType.DELETE)
    @DeleteMapping("/delete/{id}")
    public Result<String> deleteQualityStandard(@PathVariable Integer id) {
        try {
            qualityStandardService.deleteQualityStandard(id);
            return Result.success("删除成功");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }
}
