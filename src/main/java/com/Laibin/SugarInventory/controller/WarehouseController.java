package com.Laibin.SugarInventory.controller;

import com.Laibin.SugarInventory.annotation.LogOperation;
import com.Laibin.SugarInventory.common.Result;
import com.Laibin.SugarInventory.domain.assembler.ManagementViewAssembler;
import com.Laibin.SugarInventory.domain.dto.WarehouseDTO;
import com.Laibin.SugarInventory.domain.dto.WarehouseLocationCodeBatchDTO;
import com.Laibin.SugarInventory.domain.dto.WarehouseUpdateDTO;
import com.Laibin.SugarInventory.domain.enumObject.OperationType;
import com.Laibin.SugarInventory.domain.po.Warehouse;
import com.Laibin.SugarInventory.domain.vo.WarehouseVO;
import com.Laibin.SugarInventory.domain.vo.WarehouseLocationCodeVO;
import com.Laibin.SugarInventory.service.WarehouseLocationCodeService;
import com.Laibin.SugarInventory.service.WarehouseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.io.IOException;

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

    @Autowired
    private WarehouseLocationCodeService warehouseLocationCodeService;

    @Operation(summary = "获取库位码", description = "生成与库位ID绑定的受控扫码内容，不包含左右侧或库位内位置")
    @GetMapping("/{id}/location-code")
    public Result<WarehouseLocationCodeVO> getLocationCode(@PathVariable("id") Integer id) {
        return Result.success(warehouseLocationCodeService.getLocationCode(id));
    }

    @Operation(summary = "解析库位码", description = "解析系统生成的库位码并返回实时库位信息")
    @GetMapping("/location-code/resolve")
    public Result<WarehouseLocationCodeVO> resolveLocationCode(@RequestParam("code") String code) {
        return Result.success(warehouseLocationCodeService.resolve(code));
    }

    @Operation(summary = "批量获取库位码", description = "供库位码打印页面按所选库位生成标签数据")
    @PostMapping("/location-codes/query")
    @PreAuthorize("hasAuthority('qrcode:print')")
    public Result<List<WarehouseLocationCodeVO>> getLocationCodes(
            @Valid @RequestBody WarehouseLocationCodeBatchDTO dto) {
        return Result.success(warehouseLocationCodeService.getLocationCodes(dto.getWarehouseIds()));
    }

    @Operation(summary = "下载库位二维码 PNG")
    @GetMapping("/{id}/location-qrcode.png")
    @PreAuthorize("hasAuthority('qrcode:print')")
    public void downloadLocationQrCode(@PathVariable("id") Integer id,
                                       HttpServletResponse response) throws IOException {
        byte[] png = warehouseLocationCodeService.generateQrCodePng(id);
        response.setContentType("image/png");
        response.setHeader("Content-Disposition", "attachment; filename=warehouse-location-" + id + ".png");
        response.getOutputStream().write(png);
    }

    @Operation(summary = "下载库位二维码标签 PDF")
    @GetMapping("/{id}/location-qrcode-label.pdf")
    @PreAuthorize("hasAuthority('qrcode:print')")
    public void downloadLocationQrLabel(@PathVariable("id") Integer id,
                                        HttpServletResponse response) throws IOException {
        byte[] pdf = warehouseLocationCodeService.generateQrLabelPdf(id);
        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", "attachment; filename=warehouse-location-" + id + ".pdf");
        response.getOutputStream().write(pdf);
    }

    @Operation(summary = "批量下载库位二维码标签 PDF")
    @PostMapping("/location-qrcode-labels/pdf")
    @PreAuthorize("hasAuthority('qrcode:print')")
    public void downloadLocationQrLabels(
            @Valid @RequestBody WarehouseLocationCodeBatchDTO dto,
            HttpServletResponse response) throws IOException {
        byte[] pdf = warehouseLocationCodeService.generateQrLabelPdf(dto.getWarehouseIds());
        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", "attachment; filename=warehouse-location-labels.pdf");
        response.getOutputStream().write(pdf);
    }

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
