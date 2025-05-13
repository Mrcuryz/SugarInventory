package com.Laibin.SugarInventory.controller;

import com.Laibin.SugarInventory.SpringSecurity.LoginUser;
import com.Laibin.SugarInventory.annotation.LogOperation;
import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.common.PageResult;
import com.Laibin.SugarInventory.common.Result;
import com.Laibin.SugarInventory.domain.dto.AssayQueryDTO;
import com.Laibin.SugarInventory.domain.dto.AssaySubmitDTO;
import com.Laibin.SugarInventory.domain.enumObject.OperationType;
import com.Laibin.SugarInventory.domain.vo.AssayVO;
import com.Laibin.SugarInventory.service.AssayService;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/assay")

@Tag(name = "化验记录管理", description = "包括导入化验记录、查询化验记录、更新化验记录等接口")
public class AssayController {
    @Autowired
    private AssayService assayService;

    @PostMapping("/import")
    @LogOperation(value = "化验数据", type = OperationType.INSERT)
    @Operation(summary = "导入化验记录", description = "批量导入化验记录")
    @PreAuthorize("hasAuthority('quality:test')")
    public Result<Boolean> importAssays(
            @RequestBody List<AssaySubmitDTO> dtos,
            @AuthenticationPrincipal LoginUser loginUser
    ) {
        try {
            assayService.importAssays(dtos, loginUser.getUser().getId());
            return Result.success(true);
        } catch (BusinessException e) {
            return Result.error(500, "化验记录导入失败：" + e.getMessage());
        }
    }

    @PreAuthorize("hasAuthority('quality:test')")
    @Operation(summary = "查询化验记录", description = "根据查询条件分页查询化验记录")
    @PostMapping("/query")
    public Result<PageResult<AssayVO>> queryAssays(
            @RequestBody AssayQueryDTO query
    ) {
        try {
            return Result.success(assayService.queryAssays(query));
        } catch (BusinessException e) {
            e.printStackTrace();
            return Result.error(500, "化验记录查询失败：" + e.getMessage());
        }
    }

    @Operation(summary = "更新化验记录", description = "根据化验记录ID更新化验数据（更新时保留旧记录，以便历史对比）")
    @LogOperation(value = "化验数据", type = OperationType.INSERT)
    @PreAuthorize("hasAuthority('quality:test')")
    @PostMapping("/{id}")
    public Result<AssayVO> updateAssay(
            @PathVariable("id") Integer id,
            @RequestBody AssaySubmitDTO dto,
            @AuthenticationPrincipal LoginUser loginUser
    ) {
        try {
            return Result.success(assayService.updateAssay(id, dto, loginUser.getUser()));
        } catch (BusinessException | JsonProcessingException e) {
            return Result.error(500, "化验记录更新失败：" + e.getMessage());
        }
    }
}
