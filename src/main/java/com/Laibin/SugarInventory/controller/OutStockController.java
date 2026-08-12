package com.Laibin.SugarInventory.controller;

import com.Laibin.SugarInventory.SpringSecurity.LoginUser;
import com.Laibin.SugarInventory.annotation.CheckWarehouseStatus;
import com.Laibin.SugarInventory.annotation.LogOperation;
import com.Laibin.SugarInventory.common.PageResult;
import com.Laibin.SugarInventory.common.Result;
import com.Laibin.SugarInventory.domain.dto.OutRecordQueryDTO;
import com.Laibin.SugarInventory.domain.dto.OutStockRequestDTO;
import com.Laibin.SugarInventory.domain.dto.OutProductQueryDTO;
import com.Laibin.SugarInventory.domain.dto.TransferOutStockRequestDTO;
import com.Laibin.SugarInventory.domain.enumObject.OperationType;
import com.Laibin.SugarInventory.domain.po.User;
import com.Laibin.SugarInventory.domain.vo.*;
import com.Laibin.SugarInventory.service.OutStockService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/out-stock")
@RequiredArgsConstructor
@Tag(name = "出库管理", description = "产品出库和出库记录查询接口")
public class OutStockController {

    private final OutStockService outStockService;

    @Operation(summary = "产品出库操作", description = "创建出库记录")
    @CheckWarehouseStatus
    @PostMapping("/out")
    @PreAuthorize("hasAuthority('inventory:outbound')")
    @LogOperation(value = "out_stock", type = OperationType.INSERT)
    public Result<OutVO> createOutStock(
            @RequestBody OutStockRequestDTO request,
            @AuthenticationPrincipal LoginUser loginUser
    ) {
        return Result.success(outStockService.processOutStock(request, loginUser.getUser().getId()));
    }

    @Operation(summary = "产品调拨出库", description = "产品调拨出库")
    @CheckWarehouseStatus
    @PostMapping("/transferOut")
    @PreAuthorize("hasAuthority('inventory:transfer')")
    @LogOperation(value = "out_stock", type = OperationType.INSERT)
    public Result<InVO> transferOut(
            @RequestBody TransferOutStockRequestDTO request,
            @AuthenticationPrincipal LoginUser loginUser
    ) {
        return Result.success(outStockService.transferOut(request, loginUser.getUser().getId()));
    }


    @Operation(summary = "栈式出库操作", description = "新增特殊库位出库记录")
    @CheckWarehouseStatus
    @PostMapping("/stack-out")
    @PreAuthorize("hasAuthority('inventory:outbound')")
    @LogOperation(value = "out_stock", type = OperationType.INSERT)
    public Result<OutVO> createStackOutStock(
            @RequestBody OutStockRequestDTO request,
            @AuthenticationPrincipal LoginUser loginUser
    ) {
        return Result.success(outStockService.processStackOutStock(request, loginUser.getUser().getId()));
    }

    @Operation(summary = "出库记录查询", description = "根据条件批量查询出库记录信息")
    @PostMapping("/records")
    @PreAuthorize("hasAnyAuthority('document:view', 'record:query')")
    public Result<PageResult<OutStockRecordVO>> searchOutRecords(
            @RequestBody OutRecordQueryDTO query,
            @AuthenticationPrincipal LoginUser loginUser
    ) {
        User user = loginUser.getUser();
        return Result.success(outStockService.searchOutRecords(query, user));
    }
}
