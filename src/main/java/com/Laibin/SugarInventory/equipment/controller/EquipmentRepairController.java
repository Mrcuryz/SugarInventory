package com.Laibin.SugarInventory.equipment.controller;

import com.Laibin.SugarInventory.SpringSecurity.LoginUser;
import com.Laibin.SugarInventory.annotation.LogOperation;
import com.Laibin.SugarInventory.common.PageResult;
import com.Laibin.SugarInventory.common.Result;
import com.Laibin.SugarInventory.domain.enumObject.OperationType;
import com.Laibin.SugarInventory.equipment.domain.dto.EquipmentRepairCreateDTO;
import com.Laibin.SugarInventory.equipment.domain.dto.EquipmentRepairQueryDTO;
import com.Laibin.SugarInventory.equipment.domain.dto.EquipmentRepairUpdateDTO;
import com.Laibin.SugarInventory.equipment.domain.vo.EquipmentRepairVO;
import com.Laibin.SugarInventory.equipment.service.EquipmentRepairService;
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
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/equipment/repairs")
@RequiredArgsConstructor
@Tag(name = "设备修理记录")
public class EquipmentRepairController {
    private final EquipmentRepairService repairService;

    @PostMapping("/query")
    @PreAuthorize("hasAuthority('equipment:repair:view')")
    public Result<PageResult<EquipmentRepairVO>> query(@RequestBody(required = false) EquipmentRepairQueryDTO query) {
        return Result.success(repairService.page(query));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('equipment:repair:view')")
    public Result<EquipmentRepairVO> detail(@PathVariable Integer id) {
        return Result.success(repairService.detail(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('equipment:repair:create')")
    @LogOperation(value = "设备修理记录", type = OperationType.INSERT)
    public Result<EquipmentRepairVO> create(@RequestBody @Valid EquipmentRepairCreateDTO dto,
                                            @AuthenticationPrincipal LoginUser loginUser) {
        return Result.success(repairService.create(dto, loginUser.getUser().getId()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('equipment:repair:update')")
    @LogOperation(value = "设备修理记录", type = OperationType.UPDATE)
    public Result<EquipmentRepairVO> update(@PathVariable Integer id,
                                            @RequestBody @Valid EquipmentRepairUpdateDTO dto,
                                            @AuthenticationPrincipal LoginUser loginUser) {
        return Result.success(repairService.update(id, dto, loginUser.getUser().getId()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('equipment:repair:delete')")
    @LogOperation(value = "设备修理记录", type = OperationType.DELETE)
    public Result<Void> delete(@PathVariable Integer id) {
        repairService.delete(id);
        return Result.success(null);
    }

    @PostMapping("/export")
    @PreAuthorize("hasAuthority('equipment:repair:export')")
    public void export(@RequestBody(required = false) EquipmentRepairQueryDTO query,
                       HttpServletResponse response) throws IOException {
        byte[] content = repairService.export(query);
        String name = URLEncoder.encode("设备修理记录-" + LocalDate.now() + ".xlsx", StandardCharsets.UTF_8).replace("+", "%20");
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + name);
        response.setContentLength(content.length);
        response.getOutputStream().write(content);
    }
}
