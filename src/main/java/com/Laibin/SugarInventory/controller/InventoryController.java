package com.Laibin.SugarInventory.controller;

import com.Laibin.SugarInventory.common.PageResult;
import com.Laibin.SugarInventory.common.Result;
import com.Laibin.SugarInventory.domain.dto.InventoryQueryDTO;
import com.Laibin.SugarInventory.domain.dto.OutProductQueryDTO;
import com.Laibin.SugarInventory.domain.vo.OutProductVO;
import com.Laibin.SugarInventory.domain.vo.OutWarehouseVO;
import com.Laibin.SugarInventory.domain.vo.VInventorySummary;
import com.Laibin.SugarInventory.domain.vo.VWarehouseCapacity;
import com.Laibin.SugarInventory.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
@Tag(name = "库存详情查询", description = "库位中库存详情查询")
public class InventoryController {

    private final InventoryService inventoryService;

    @Operation(summary = "库存详情查询", description = "根据库位ID和产品名称查询库存详情")
    @PostMapping("/summary")
    public Result<PageResult<VInventorySummary>> getSummary(
            @RequestBody InventoryQueryDTO query
    ) {
        return Result.success(inventoryService.getInventorySummary(query));
    }

    @Operation(summary = "库存容量百分比查询", description = "查询库存容量百分比")
    @GetMapping("/warehouses")
    public Result<List<VWarehouseCapacity>> getWarehouses() {
        try {
            return Result.success(inventoryService.getWarehouses());
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @Operation(summary = "查询所有存有符合标准的产品的库位", description = "根据产品名称、标准名称、筛网ID、入库日期查询存有符合条件产品的库位")
    @PostMapping("/qualified-warehouses")
    public Result<List<OutWarehouseVO>> getQualifiedWarehouses(@RequestBody OutProductQueryDTO queryDTO) {
        return Result.success(inventoryService.getQualifiedWarehouses(queryDTO));
    }

    @Operation(summary = "库位中库存详情查询", description = "根据库位ID和条件查询库位中库存详情")
    @PostMapping("/qualified-inventory/{warehouseId}")
    public Result<List<OutProductVO>> getInventoryDetails(@PathVariable Integer warehouseId,
                                                          @RequestBody OutProductQueryDTO queryDTO) {
        return Result.success(inventoryService.getInventoryDetails(warehouseId, queryDTO));
    }
}