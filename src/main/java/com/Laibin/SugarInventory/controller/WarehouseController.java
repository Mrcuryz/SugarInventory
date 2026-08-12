package com.Laibin.SugarInventory.controller;

import com.Laibin.SugarInventory.annotation.LogOperation;
import com.Laibin.SugarInventory.common.Result;
import com.Laibin.SugarInventory.domain.assembler.ManagementViewAssembler;
import com.Laibin.SugarInventory.domain.dto.WarehouseDTO;
import com.Laibin.SugarInventory.domain.dto.WarehouseUpdateDTO;
import com.Laibin.SugarInventory.domain.enumObject.OperationType;
import com.Laibin.SugarInventory.domain.po.Warehouse;
import com.Laibin.SugarInventory.domain.vo.WarehouseVO;
import com.Laibin.SugarInventory.service.WarehouseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
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
@RequestMapping("/api/warehouse")
@Tag(name = "库位信息管理", description = "仓库中库位信息的增删改查")
@PreAuthorize("hasAuthority('warehouse:view')")
public class WarehouseController {
    @Autowired
    private WarehouseService warehouseService;

    @Operation(summary = "设置指定库位为维修状态")
    @LogOperation(value = "库位", type = OperationType.UPDATE)
    @PutMapping("/maintain/{id}")
    @PreAuthorize("hasAuthority('warehouse:status')")
    public Result<WarehouseVO> updateWarehouseToMaintain(
            @Parameter(description = "库位ID")
            @PathVariable("id") Integer id) {
        return Result.success(ManagementViewAssembler.toWarehouseVO(
                warehouseService.setWarehouseToMaintain(id)));
    }

    // 新增仓库
    @Operation(summary = "新增库位")
    @LogOperation(value = "库位", type = OperationType.INSERT)
    @PostMapping("/create")
    @PreAuthorize("hasAuthority('warehouse:create')")
    public Result<WarehouseVO> createWarehouse(@RequestBody WarehouseDTO warehouse) {
        return Result.success(ManagementViewAssembler.toWarehouseVO(
                warehouseService.createWarehouse(warehouse)));
    }

    // 根据ID查询仓库信息
    @GetMapping("/{id}")
    public Result<WarehouseVO> getWarehouse(@PathVariable Integer id) {
        Warehouse warehouse = warehouseService.getWarehouseById(id);
        return Result.success(ManagementViewAssembler.toWarehouseVO(warehouse));
    }

    // 修改仓库信息
    @Operation(summary = "修改库位信息")
    @LogOperation(value = "库位", type = OperationType.UPDATE)
    @PutMapping("/update")
    @PreAuthorize("hasAuthority('warehouse:update')")
    public Result<WarehouseVO> updateWarehouse(@RequestBody WarehouseUpdateDTO warehouse) {
        return Result.success(ManagementViewAssembler.toWarehouseVO(
                warehouseService.updateWarehouse(warehouse)));
    }

    // 删除仓库
    @Operation(summary = "删除库位")
    @LogOperation(value = "库位", type = OperationType.DELETE)
    @DeleteMapping("/delete/{id}")
    @PreAuthorize("hasAuthority('warehouse:delete')")
    public Result<String> deleteWarehouse(@PathVariable Integer id) {
        warehouseService.deleteWarehouse(id);
        return Result.success("仓库删除成功");
    }

    // 根据ID查询库位，如果ID为空，则返回所有库位
    @GetMapping("/query")
    public Result<List<WarehouseVO>> queryWarehouse(@RequestParam(required = false) String name) {
        return Result.success(ManagementViewAssembler.toWarehouseVOs(
                warehouseService.listAllWarehouses(name)));
    }
}
