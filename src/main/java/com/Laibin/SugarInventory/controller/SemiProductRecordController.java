package com.Laibin.SugarInventory.controller;

import com.Laibin.SugarInventory.SpringSecurity.LoginUser;
import com.Laibin.SugarInventory.annotation.CheckWarehouseStatus;
import com.Laibin.SugarInventory.annotation.LogOperation;
import com.Laibin.SugarInventory.common.PageResult;
import com.Laibin.SugarInventory.common.Result;
import com.Laibin.SugarInventory.domain.dto.*;
import com.Laibin.SugarInventory.domain.enumObject.OperationType;
import com.Laibin.SugarInventory.domain.po.SemiProductRecord;
import com.Laibin.SugarInventory.domain.po.User;
import com.Laibin.SugarInventory.domain.vo.*;
import com.Laibin.SugarInventory.service.SemiProductRecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author Mrcury
 * @since 2025-02-19
 */
@RestController
@RequestMapping("/api/semi-products")
@RequiredArgsConstructor
@Tag(name = "半成品记录管理", description = "包括新增、查询、修改半成品记录的相关接口")
@PreAuthorize("hasAnyAuthority('inventory:view', 'record:query')")
public class SemiProductRecordController {
    private final SemiProductRecordService semiProductRecordService;

    /**
     * 新增半成品记录
     *
     * @param dto       半成品查询条件
     *                  产品名称、数量
     * @param loginUser 登录用户信息
     * @return 半成品名称列表
     */
    @Operation(summary = "半成品入库", description = "新增一条半成品记录，记录产品名称、数量等信息。记录由当前登录用户录入。")
    @PostMapping("/add")
    @CheckWarehouseStatus
    @PreAuthorize("hasAuthority('inventory:inbound')")
    @LogOperation(value = "semi_product_record", type = OperationType.INSERT)
    public Result<InVO> addSemiProductRecord(
            @RequestBody AddSemiProductRecordDTO dto,
            @AuthenticationPrincipal LoginUser loginUser) {
        return Result.success(semiProductRecordService.addSemiProductRecord(dto, loginUser.getUser().getName()));
    }

    @Operation(summary = "半成品栈式入库", description = "在特殊库位新增一条半成品记录，记录产品名称、数量等信息。记录由当前登录用户录入。")
    @PostMapping("/stack-in")
    @CheckWarehouseStatus
    @PreAuthorize("hasAuthority('inventory:inbound')")
    @LogOperation(value = "semi_product_record", type = OperationType.INSERT)
    public Result<InVO> stackModeInStock(
            @RequestBody AddSemiProductRecordDTO dto,
            @AuthenticationPrincipal LoginUser loginUser) {
        return Result.success(semiProductRecordService.stackModeInStock(dto, loginUser.getUser().getName()));
    }

    @Operation(summary = "批量查询半成品记录详情", description = "根据多个记录ID批量查询半成品记录的详细信息")
    @PostMapping("/batch-get")
    public Result<List<RecordDetailVO>> getSemiProductRecords(
            @RequestBody BatchGetSemiProductRecordDTO dto) {
        return Result.success(semiProductRecordService.getSemiProductRecordsByIds(dto.getIds()));
    }

    /**
     * 查询本人的半成品记录
     *
     * @param dto       操作日期（可选）
     * @param loginUser 登录用户信息
     * @return 本人关联半成品记录列表
     */
    @Operation(summary = "查询当前用户的半成品记录", description = "根据操作日期（可选）查询当前登录用户关联的半成品记录列表")
    @GetMapping("/my-records")
    public Result<List<RecordDetailVO>> getMyRecords(
            @Validated RecordQueryDTO dto,
            @AuthenticationPrincipal LoginUser loginUser
    ) {
        return Result.success(
                semiProductRecordService.getRecordsByOperator(
                        loginUser.getUser().getOpenid(),
                        dto.getOperationDate()
                )
        );
    }

    /**
     * 查询半成品记录
     *
     * @param dto 查询条件
     *            产品名称（可选）、操作日期（可选）、操作员姓名（可选）
     * @return 半成品记录列表
     */
    @Operation(summary = "查询半成品记录列表", description = "根据查询条件（产品名称、操作日期、操作员姓名）查询半成品记录列表")
    @PostMapping("/records")
    public Result<PageResult<RecordDetailVO>> getSemiProductRecords(
            @RequestBody SemiProductRecordDTO dto,
            @AuthenticationPrincipal LoginUser loginUser
    ) {
        User user = loginUser.getUser();
        PageResult<RecordDetailVO> pageResult = semiProductRecordService.getSemiProductRecords(dto, user);
        return Result.success(pageResult);
    }
}
