package com.Laibin.SugarInventory.equipment.controller;

import com.Laibin.SugarInventory.SpringSecurity.LoginUser;
import com.Laibin.SugarInventory.annotation.LogOperation;
import com.Laibin.SugarInventory.common.PageResult;
import com.Laibin.SugarInventory.common.Result;
import com.Laibin.SugarInventory.domain.enumObject.OperationType;
import com.Laibin.SugarInventory.equipment.domain.dto.EquipmentAssetCreateDTO;
import com.Laibin.SugarInventory.equipment.domain.dto.EquipmentAssetQueryDTO;
import com.Laibin.SugarInventory.equipment.domain.dto.EquipmentAssetUpdateDTO;
import com.Laibin.SugarInventory.equipment.domain.vo.EquipmentAssetDetailVO;
import com.Laibin.SugarInventory.equipment.domain.vo.EquipmentAssetOptionVO;
import com.Laibin.SugarInventory.equipment.domain.vo.EquipmentAssetVO;
import com.Laibin.SugarInventory.equipment.service.EquipmentAssetService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/equipment/assets")
@RequiredArgsConstructor
@Tag(name = "设备台账")
public class EquipmentAssetController {
    private final EquipmentAssetService assetService;

    @PostMapping("/query")
    @PreAuthorize("hasAuthority('equipment:asset:view')")
    public Result<PageResult<EquipmentAssetVO>> query(@RequestBody(required = false) EquipmentAssetQueryDTO query) {
        return Result.success(assetService.page(query));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('equipment:asset:view')")
    public Result<EquipmentAssetDetailVO> detail(@PathVariable Integer id) {
        return Result.success(assetService.detail(id));
    }

    @GetMapping("/options")
    @PreAuthorize("hasAnyAuthority('equipment:asset:view','equipment:asset:create','equipment:asset:update','equipment:repair:view','equipment:repair:create','equipment:repair:update')")
    public Result<List<EquipmentAssetOptionVO>> options(@RequestParam(required = false) String keyword) {
        return Result.success(assetService.options(keyword));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('equipment:asset:create')")
    @LogOperation(value = "设备台账", type = OperationType.INSERT)
    public Result<EquipmentAssetVO> create(@RequestBody @Valid EquipmentAssetCreateDTO dto,
                                           @AuthenticationPrincipal LoginUser loginUser) {
        return Result.success(assetService.create(dto, loginUser.getUser().getId()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('equipment:asset:update')")
    @LogOperation(value = "设备台账", type = OperationType.UPDATE)
    public Result<EquipmentAssetVO> update(@PathVariable Integer id,
                                           @RequestBody @Valid EquipmentAssetUpdateDTO dto,
                                           @AuthenticationPrincipal LoginUser loginUser) {
        return Result.success(assetService.update(id, dto, loginUser.getUser().getId()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('equipment:asset:delete')")
    @LogOperation(value = "设备台账", type = OperationType.DELETE)
    public Result<Void> delete(@PathVariable Integer id) {
        assetService.delete(id);
        return Result.success(null);
    }

    @PostMapping("/export")
    @PreAuthorize("hasAuthority('equipment:asset:export')")
    public void export(@RequestBody(required = false) EquipmentAssetQueryDTO query,
                       HttpServletResponse response) throws IOException {
        writeXlsx(response, "设备台账-" + LocalDate.now() + ".xlsx", assetService.export(query));
    }

    private void writeXlsx(HttpServletResponse response, String fileName, byte[] content) throws IOException {
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encoded);
        response.setContentLength(content.length);
        response.getOutputStream().write(content);
    }
}
