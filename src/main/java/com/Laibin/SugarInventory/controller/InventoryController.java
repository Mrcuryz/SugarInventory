package com.Laibin.SugarInventory.controller;

import com.Laibin.SugarInventory.common.PageResult;
import com.Laibin.SugarInventory.common.Result;
import com.Laibin.SugarInventory.domain.dto.InventoryQueryDTO;
import com.Laibin.SugarInventory.domain.dto.OutProductQueryDTO;
import com.Laibin.SugarInventory.domain.dto.OutStockBatchQueryDTO;
import com.Laibin.SugarInventory.domain.vo.OutProductVO;
import com.Laibin.SugarInventory.domain.vo.OutWarehouseVO;
import com.Laibin.SugarInventory.domain.vo.VInventorySummary;
import com.Laibin.SugarInventory.domain.vo.VWarehouseCapacity;
import com.Laibin.SugarInventory.domain.vo.WarehouseRecentOperationVO;
import com.Laibin.SugarInventory.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
@Tag(name = "库存详情查询", description = "库位中库存详情查询")
public class InventoryController {
    private final InventoryService inventoryService;

    @PreAuthorize("hasAuthority('record:query')")
    @Operation(summary = "库存详情查询", description = "根据库位ID和产品名称查询库存详情")
    @PostMapping("/summary")
    public Result<PageResult<VInventorySummary>> getSummary(
            @RequestBody InventoryQueryDTO query
    ) {
        return Result.success(inventoryService.getInventorySummary(query));
    }

    @Operation(summary = "产品所有库存", description = "产品所有库存")
    @GetMapping("/stock")
    public Result<List<VInventorySummary>> getProductStock(@RequestParam String productStatus,
                                                           @RequestParam(required = false) String productName) {
        if (productName != null) {
            productName = productName.trim();
        }
        return Result.success(inventoryService.getProductStock(productStatus, productName));
    }

    @PreAuthorize("hasAuthority('record:query')")
    @Operation(summary = "库存容量百分比查询", description = "查询库存容量百分比")
    @GetMapping("/warehouses")
    public Result<List<VWarehouseCapacity>> getWarehouses() {
        try {
            return Result.success(inventoryService.getWarehouses());
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @PreAuthorize("hasAuthority('record:query')")
    @Operation(summary = "库存容量百分比查询", description = "查询库存容量百分比")
    @GetMapping("/query")
    public Result<PageResult<VWarehouseCapacity>> queryWarehouses(
            @RequestParam(value = "warehouseName", required = false) String warehouseName,
            @RequestBody(required = false) List<Integer> ids,
            @RequestParam("page") Integer page,
            @RequestParam("size") Integer size,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "sortField", required = false) String sortField,
            @RequestParam(value = "sortOrder", required = false) String sortOrder,
            @RequestParam(value = "createdStart", required = false) String createdStart,
            @RequestParam(value = "createdEnd", required = false) String createdEnd,
            @RequestParam(value = "updatedStart", required = false) String updatedStart,
            @RequestParam(value = "updatedEnd", required = false) String updatedEnd) {
        try {
            return Result.success(inventoryService.queryWarehouses(warehouseName, ids, page, size, status,
                    sortField, sortOrder, createdStart, createdEnd, updatedStart, updatedEnd));
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @PreAuthorize("hasAuthority('record:query')")
    @Operation(summary = "库位最近操作记录", description = "查询库位最近流转操作记录")
    @GetMapping("/warehouses/{warehouseId}/recent-operations")
    public Result<List<WarehouseRecentOperationVO>> listWarehouseRecentOperations(@PathVariable Integer warehouseId,
                                                                                  @RequestParam(value = "limit", required = false) Integer limit) {
        return Result.success(inventoryService.listWarehouseRecentOperations(warehouseId, limit));
    }

    @PreAuthorize("hasAuthority('record:query')")
    @Operation(summary = "库存容量百分比查询", description = "查询库存容量百分比")
    @PostMapping("/query")
    public Result<PageResult<VWarehouseCapacity>> queryWarehouses(@RequestBody OutStockBatchQueryDTO query) {
        try {
            System.out.println(query);
            return Result.success(inventoryService.batchQueryWarehouses(query));
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @PreAuthorize("hasAuthority('record:query')")
    @Operation(summary = "查询所有存有符合标准的产品的库位", description = "根据产品名称、标准名称、筛网ID、入库日期查询存有符合条件产品的库位")
    @PostMapping("/qualified-warehouses")
    public Result<List<OutWarehouseVO>> getQualifiedWarehouses(@RequestBody OutProductQueryDTO queryDTO) {
        try {
            System.out.println(queryDTO);
            return Result.success(inventoryService.getQualifiedWarehouses(queryDTO));
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error(e.getMessage());
        }
    }

    @Operation(summary = "库位中库存详情查询", description = "根据库位ID和条件查询库位中库存详情")
    @PostMapping("/qualified-inventory/{warehouseId}")
    public Result<List<OutProductVO>> getInventoryDetails(@PathVariable Integer warehouseId,
                                                          @RequestBody OutProductQueryDTO queryDTO) {
        return Result.success(inventoryService.getInventoryDetails(warehouseId, queryDTO));
    }

    @Operation(summary = "库位库存明细分页查询", description = "根据库位ID和条件分页查询库位中的库存明细")
    @PostMapping("/qualified-inventory/{warehouseId}/page")
    public Result<PageResult<OutProductVO>> pageInventoryDetails(@PathVariable Integer warehouseId,
                                                                 @RequestBody OutProductQueryDTO queryDTO) {
        return Result.success(inventoryService.pageInventoryDetails(warehouseId, queryDTO));
    }
}
