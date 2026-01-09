package com.Laibin.SugarInventory.controller;

import com.Laibin.SugarInventory.SpringSecurity.LoginUser;
import com.Laibin.SugarInventory.annotation.CheckWarehouseStatus;
import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.common.PageResult;
import com.Laibin.SugarInventory.common.Result;
import com.Laibin.SugarInventory.domain.dto.InStockQueryDTO;
import com.Laibin.SugarInventory.domain.dto.InStockRequestDTO;
import com.Laibin.SugarInventory.domain.po.User;
import com.Laibin.SugarInventory.domain.vo.InStockVO;
import com.Laibin.SugarInventory.domain.vo.InVO;
import com.Laibin.SugarInventory.service.InStockService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 * 前端控制器
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


    @Operation(summary = "新增成品入库记录", description = "新增入库记录，包含产品、仓库、半成品信息、筛网规格及多个库存位置")
    @CheckWarehouseStatus
    @PostMapping("/add")
    public Result<InVO> stockIn(
            @RequestBody @Valid InStockRequestDTO request,
            @AuthenticationPrincipal LoginUser loginUser
    ) {
        try {
            InVO inVO = inStockService.stockIn(request, loginUser.getUser().getId());
            System.out.println(inVO);
            return Result.success(inVO);
        } catch (BusinessException e) {
            e.printStackTrace();
            return Result.error(500, "成品入库出错：" + e.getMessage());
        }
    }

    @Operation(summary = "批量查询入库记录", description = "根据查询条件分页查询入库记录")
    @PostMapping("/query")
    public Result<PageResult<InStockVO>> queryRecords(@RequestBody InStockQueryDTO queryDTO,
                                                      @AuthenticationPrincipal LoginUser loginUser) {
        User user = loginUser.getUser();
        try {
            return Result.success(inStockService.queryInStockRecords(queryDTO, user));
        } catch (BusinessException e) {
            return Result.error(500, "查询入库记录出错：" + e.getMessage());
        }
    }
}
