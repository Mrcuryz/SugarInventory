package com.Laibin.SugarInventory.controller;

import com.Laibin.SugarInventory.SpringSecurity.LoginUser;
import com.Laibin.SugarInventory.annotation.LogOperation;
import com.Laibin.SugarInventory.common.Result;
import com.Laibin.SugarInventory.domain.dto.OutStockRequestDTO;
import com.Laibin.SugarInventory.domain.dto.OutProductQueryDTO;
import com.Laibin.SugarInventory.domain.enumObject.OperationType;
import com.Laibin.SugarInventory.domain.vo.OutProductVO;
import com.Laibin.SugarInventory.domain.vo.OutVO;
import com.Laibin.SugarInventory.domain.vo.ProductVO;
import com.Laibin.SugarInventory.service.OutStockService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/out-stock")
@RequiredArgsConstructor
@Tag(name = "出库管理", description = "产品出库和出库记录查询接口")
public class OutStockController {

    private final OutStockService outStockService;

    @Operation(summary = "产品出库操作", description = "创建出库记录，需指定产品ID、入库日期、筛网规格以及每个位置对应的出库数量")
    @PostMapping("/out")
    public Result<OutVO> createOutStock(
            @RequestBody OutStockRequestDTO request,
            @AuthenticationPrincipal LoginUser loginUser
    ) {
        try {
            return Result.success(outStockService.processOutStock(request, loginUser.getUser().getId()));
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @Operation(summary = "筛选出库产品", description = "根据条件查询符合条件的出库产品，返回符合条件的产品及位置信息")
    @PostMapping("/search")
    public Result<List<OutProductVO>> searchProducts(@RequestBody OutProductQueryDTO query) {
        List<OutProductVO> products = outStockService.searchProducts(query);
        return Result.success(products);
    }
}
