package com.Laibin.SugarInventory.controller;

import com.Laibin.SugarInventory.SpringSecurity.LoginUser;
import com.Laibin.SugarInventory.annotation.LogOperation;
import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.common.PageResult;
import com.Laibin.SugarInventory.common.Result;
import com.Laibin.SugarInventory.domain.dto.InStockQueryDTO;
import com.Laibin.SugarInventory.domain.dto.InStockUpdateDTO;
import com.Laibin.SugarInventory.domain.dto.InStockRequestDTO;
import com.Laibin.SugarInventory.domain.enumObject.OperationType;
import com.Laibin.SugarInventory.domain.po.User;
import com.Laibin.SugarInventory.domain.vo.InStockVO;
import com.Laibin.SugarInventory.service.InStockService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author Mrcury
 * @since 2025-02-19
 */
@RestController
@RequestMapping("/api/in-stock")
@Tag(name = "入库管理", description = "入库记录的新增、查询、批量修改接口")

public class InStockController {
    @Autowired
    private InStockService inStockService;

    @Operation(summary = "新增入库记录", description = "新增入库记录，包含产品、仓库、半成品信息、筛网规格及多个库存位置")
    @PostMapping("/add")
    public Result<Boolean> stockIn(
            @RequestBody @Valid InStockRequestDTO request,
            @AuthenticationPrincipal LoginUser loginUser
    ) {
        try {
            inStockService.handleStockIn(request, loginUser.getUser().getId());
            return Result.success(true);
        } catch (BusinessException e) {
            return Result.error(500, "商品入库失败：" + e.getMessage());
        }
    }

    @Operation(summary = "查询入库记录", description = "根据查询条件分页查询入库记录")
    @PostMapping("/query")
    public Result<PageResult<InStockVO>> queryRecords(@RequestBody InStockQueryDTO queryDTO,
                                                      @AuthenticationPrincipal LoginUser loginUser) {
        User user = loginUser.getUser();
        return Result.success(inStockService.queryInStockRecords(queryDTO, user));
    }
}
