package com.Laibin.SugarInventory.equipment.controller;

import com.Laibin.SugarInventory.SpringSecurity.LoginUser;
import com.Laibin.SugarInventory.annotation.LogOperation;
import com.Laibin.SugarInventory.common.PageResult;
import com.Laibin.SugarInventory.common.Result;
import com.Laibin.SugarInventory.domain.enumObject.OperationType;
import com.Laibin.SugarInventory.equipment.domain.dto.EquipmentBasicDataDTOs.EquipmentBasicQueryDTO;
import com.Laibin.SugarInventory.equipment.domain.dto.EquipmentBasicDataDTOs.EquipmentCategorySaveDTO;
import com.Laibin.SugarInventory.equipment.domain.dto.EquipmentBasicDataDTOs.EquipmentCodeRuleSaveDTO;
import com.Laibin.SugarInventory.equipment.domain.dto.EquipmentBasicDataDTOs.EquipmentEnabledUpdateDTO;
import com.Laibin.SugarInventory.equipment.domain.dto.EquipmentBasicDataDTOs.EquipmentManufacturerSaveDTO;
import com.Laibin.SugarInventory.equipment.domain.dto.EquipmentBasicDataDTOs.EquipmentTypeSaveDTO;
import com.Laibin.SugarInventory.equipment.domain.dto.EquipmentBasicDataDTOs.EquipmentUnitSaveDTO;
import com.Laibin.SugarInventory.equipment.domain.vo.EquipmentBasicDataVOs.EquipmentCategoryVO;
import com.Laibin.SugarInventory.equipment.domain.vo.EquipmentBasicDataVOs.EquipmentCodeRuleVO;
import com.Laibin.SugarInventory.equipment.domain.vo.EquipmentBasicDataVOs.EquipmentManufacturerVO;
import com.Laibin.SugarInventory.equipment.domain.vo.EquipmentBasicDataVOs.EquipmentOptionVO;
import com.Laibin.SugarInventory.equipment.domain.vo.EquipmentBasicDataVOs.EquipmentTypeVO;
import com.Laibin.SugarInventory.equipment.domain.vo.EquipmentBasicDataVOs.EquipmentUnitVO;
import com.Laibin.SugarInventory.equipment.service.EquipmentBasicDataService;
import io.swagger.v3.oas.annotations.tags.Tag;
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

import java.util.List;

@RestController
@RequestMapping("/api/equipment")
@RequiredArgsConstructor
@Tag(name = "设备基础资料")
public class EquipmentBasicDataController {
    private final EquipmentBasicDataService service;

    @PostMapping("/units/query")
    @PreAuthorize("hasAuthority('equipment:config:view')")
    public Result<PageResult<EquipmentUnitVO>> queryUnits(@RequestBody(required = false) EquipmentBasicQueryDTO query) {
        return Result.success(service.pageUnits(query));
    }

    @GetMapping("/units/options")
    @PreAuthorize("hasAnyAuthority('equipment:config:view','equipment:asset:view','equipment:asset:create','equipment:asset:update','equipment:repair:view')")
    public Result<List<EquipmentOptionVO>> unitOptions() { return Result.success(service.unitOptions()); }

    @PostMapping("/units")
    @PreAuthorize("hasAuthority('equipment:config:manage')")
    @LogOperation(value = "设备单位", type = OperationType.INSERT)
    public Result<EquipmentUnitVO> createUnit(@RequestBody @Valid EquipmentUnitSaveDTO dto) { return Result.success(service.saveUnit(null, dto)); }

    @PutMapping("/units/{id}")
    @PreAuthorize("hasAuthority('equipment:config:manage')")
    @LogOperation(value = "设备单位", type = OperationType.UPDATE)
    public Result<EquipmentUnitVO> updateUnit(@PathVariable Integer id, @RequestBody @Valid EquipmentUnitSaveDTO dto) { return Result.success(service.saveUnit(id, dto)); }

    @PutMapping("/units/{id}/enabled")
    @PreAuthorize("hasAuthority('equipment:config:manage')")
    @LogOperation(value = "设备单位", type = OperationType.UPDATE)
    public Result<Void> setUnitEnabled(@PathVariable Integer id, @RequestBody @Valid EquipmentEnabledUpdateDTO dto) { service.setUnitEnabled(id, dto.getEnabled()); return Result.success(null); }

    @DeleteMapping("/units/{id}")
    @PreAuthorize("hasAuthority('equipment:config:manage')")
    @LogOperation(value = "设备单位", type = OperationType.DELETE)
    public Result<Void> deleteUnit(@PathVariable Integer id) { service.deleteUnit(id); return Result.success(null); }

    @PostMapping("/categories/query")
    @PreAuthorize("hasAuthority('equipment:config:view')")
    public Result<PageResult<EquipmentCategoryVO>> queryCategories(@RequestBody(required = false) EquipmentBasicQueryDTO query) { return Result.success(service.pageCategories(query)); }

    @GetMapping("/categories/options")
    @PreAuthorize("hasAnyAuthority('equipment:config:view','equipment:asset:view','equipment:asset:create','equipment:asset:update','equipment:repair:view')")
    public Result<List<EquipmentOptionVO>> categoryOptions() { return Result.success(service.categoryOptions()); }

    @PostMapping("/categories")
    @PreAuthorize("hasAuthority('equipment:config:manage')")
    @LogOperation(value = "设备类别", type = OperationType.INSERT)
    public Result<EquipmentCategoryVO> createCategory(@RequestBody @Valid EquipmentCategorySaveDTO dto) { return Result.success(service.saveCategory(null, dto)); }

    @PutMapping("/categories/{id}")
    @PreAuthorize("hasAuthority('equipment:config:manage')")
    @LogOperation(value = "设备类别", type = OperationType.UPDATE)
    public Result<EquipmentCategoryVO> updateCategory(@PathVariable Integer id, @RequestBody @Valid EquipmentCategorySaveDTO dto) { return Result.success(service.saveCategory(id, dto)); }

    @PutMapping("/categories/{id}/enabled")
    @PreAuthorize("hasAuthority('equipment:config:manage')")
    @LogOperation(value = "设备类别", type = OperationType.UPDATE)
    public Result<Void> setCategoryEnabled(@PathVariable Integer id, @RequestBody @Valid EquipmentEnabledUpdateDTO dto) { service.setCategoryEnabled(id, dto.getEnabled()); return Result.success(null); }

    @DeleteMapping("/categories/{id}")
    @PreAuthorize("hasAuthority('equipment:config:manage')")
    @LogOperation(value = "设备类别", type = OperationType.DELETE)
    public Result<Void> deleteCategory(@PathVariable Integer id) { service.deleteCategory(id); return Result.success(null); }

    @PostMapping("/manufacturers/query")
    @PreAuthorize("hasAuthority('equipment:config:view')")
    public Result<PageResult<EquipmentManufacturerVO>> queryManufacturers(@RequestBody(required = false) EquipmentBasicQueryDTO query) { return Result.success(service.pageManufacturers(query)); }

    @GetMapping("/manufacturers/options")
    @PreAuthorize("hasAnyAuthority('equipment:config:view','equipment:asset:view','equipment:asset:create','equipment:asset:update')")
    public Result<List<EquipmentOptionVO>> manufacturerOptions() { return Result.success(service.manufacturerOptions()); }

    @PostMapping("/manufacturers")
    @PreAuthorize("hasAuthority('equipment:config:manage')")
    @LogOperation(value = "设备生产厂家", type = OperationType.INSERT)
    public Result<EquipmentManufacturerVO> createManufacturer(@RequestBody @Valid EquipmentManufacturerSaveDTO dto, @AuthenticationPrincipal LoginUser user) { return Result.success(service.saveManufacturer(null, dto, user.getUser().getId())); }

    @PutMapping("/manufacturers/{id}")
    @PreAuthorize("hasAuthority('equipment:config:manage')")
    @LogOperation(value = "设备生产厂家", type = OperationType.UPDATE)
    public Result<EquipmentManufacturerVO> updateManufacturer(@PathVariable Integer id, @RequestBody @Valid EquipmentManufacturerSaveDTO dto, @AuthenticationPrincipal LoginUser user) { return Result.success(service.saveManufacturer(id, dto, user.getUser().getId())); }

    @DeleteMapping("/manufacturers/{id}")
    @PreAuthorize("hasAuthority('equipment:config:manage')")
    @LogOperation(value = "设备生产厂家", type = OperationType.DELETE)
    public Result<Void> deleteManufacturer(@PathVariable Integer id) { service.deleteManufacturer(id); return Result.success(null); }

    @PostMapping("/renovation-types/query")
    @PreAuthorize("hasAuthority('equipment:config:view')")
    public Result<PageResult<EquipmentTypeVO>> queryRenovationTypes(@RequestBody(required = false) EquipmentBasicQueryDTO query) { return Result.success(service.pageRenovationTypes(query)); }

    @GetMapping("/renovation-types/options")
    @PreAuthorize("hasAnyAuthority('equipment:config:view','equipment:asset:view','equipment:asset:create','equipment:asset:update')")
    public Result<List<EquipmentOptionVO>> renovationTypeOptions() { return Result.success(service.renovationTypeOptions()); }

    @PostMapping("/renovation-types")
    @PreAuthorize("hasAuthority('equipment:config:manage')")
    @LogOperation(value = "设备技改类别", type = OperationType.INSERT)
    public Result<EquipmentTypeVO> createRenovationType(@RequestBody @Valid EquipmentTypeSaveDTO dto) { return Result.success(service.saveRenovationType(null, dto)); }

    @PutMapping("/renovation-types/{id}")
    @PreAuthorize("hasAuthority('equipment:config:manage')")
    @LogOperation(value = "设备技改类别", type = OperationType.UPDATE)
    public Result<EquipmentTypeVO> updateRenovationType(@PathVariable Integer id, @RequestBody @Valid EquipmentTypeSaveDTO dto) { return Result.success(service.saveRenovationType(id, dto)); }

    @PutMapping("/renovation-types/{id}/enabled")
    @PreAuthorize("hasAuthority('equipment:config:manage')")
    @LogOperation(value = "设备技改类别", type = OperationType.UPDATE)
    public Result<Void> setRenovationTypeEnabled(@PathVariable Integer id, @RequestBody @Valid EquipmentEnabledUpdateDTO dto) { service.setRenovationTypeEnabled(id, dto.getEnabled()); return Result.success(null); }

    @DeleteMapping("/renovation-types/{id}")
    @PreAuthorize("hasAuthority('equipment:config:manage')")
    @LogOperation(value = "设备技改类别", type = OperationType.DELETE)
    public Result<Void> deleteRenovationType(@PathVariable Integer id) { service.deleteRenovationType(id); return Result.success(null); }

    @PostMapping("/repair-types/query")
    @PreAuthorize("hasAuthority('equipment:config:view')")
    public Result<PageResult<EquipmentTypeVO>> queryRepairTypes(@RequestBody(required = false) EquipmentBasicQueryDTO query) { return Result.success(service.pageRepairTypes(query)); }

    @GetMapping("/repair-types/options")
    @PreAuthorize("hasAnyAuthority('equipment:config:view','equipment:repair:view','equipment:repair:create','equipment:repair:update')")
    public Result<List<EquipmentOptionVO>> repairTypeOptions() { return Result.success(service.repairTypeOptions()); }

    @PostMapping("/repair-types")
    @PreAuthorize("hasAuthority('equipment:config:manage')")
    @LogOperation(value = "设备修理类型", type = OperationType.INSERT)
    public Result<EquipmentTypeVO> createRepairType(@RequestBody @Valid EquipmentTypeSaveDTO dto) { return Result.success(service.saveRepairType(null, dto)); }

    @PutMapping("/repair-types/{id}")
    @PreAuthorize("hasAuthority('equipment:config:manage')")
    @LogOperation(value = "设备修理类型", type = OperationType.UPDATE)
    public Result<EquipmentTypeVO> updateRepairType(@PathVariable Integer id, @RequestBody @Valid EquipmentTypeSaveDTO dto) { return Result.success(service.saveRepairType(id, dto)); }

    @PutMapping("/repair-types/{id}/enabled")
    @PreAuthorize("hasAuthority('equipment:config:manage')")
    @LogOperation(value = "设备修理类型", type = OperationType.UPDATE)
    public Result<Void> setRepairTypeEnabled(@PathVariable Integer id, @RequestBody @Valid EquipmentEnabledUpdateDTO dto) { service.setRepairTypeEnabled(id, dto.getEnabled()); return Result.success(null); }

    @DeleteMapping("/repair-types/{id}")
    @PreAuthorize("hasAuthority('equipment:config:manage')")
    @LogOperation(value = "设备修理类型", type = OperationType.DELETE)
    public Result<Void> deleteRepairType(@PathVariable Integer id) { service.deleteRepairType(id); return Result.success(null); }

    @PostMapping("/code-rules/query")
    @PreAuthorize("hasAuthority('equipment:config:view')")
    public Result<PageResult<EquipmentCodeRuleVO>> queryCodeRules(@RequestBody(required = false) EquipmentBasicQueryDTO query) { return Result.success(service.pageCodeRules(query)); }

    @PostMapping("/code-rules")
    @PreAuthorize("hasAuthority('equipment:code-rule:manage')")
    @LogOperation(value = "设备编号规则", type = OperationType.INSERT)
    public Result<EquipmentCodeRuleVO> createCodeRule(@RequestBody @Valid EquipmentCodeRuleSaveDTO dto) { return Result.success(service.saveCodeRule(null, dto)); }

    @PutMapping("/code-rules/{id}")
    @PreAuthorize("hasAuthority('equipment:code-rule:manage')")
    @LogOperation(value = "设备编号规则", type = OperationType.UPDATE)
    public Result<EquipmentCodeRuleVO> updateCodeRule(@PathVariable Integer id, @RequestBody @Valid EquipmentCodeRuleSaveDTO dto) { return Result.success(service.saveCodeRule(id, dto)); }

    @PutMapping("/code-rules/{id}/enabled")
    @PreAuthorize("hasAuthority('equipment:code-rule:manage')")
    @LogOperation(value = "设备编号规则", type = OperationType.UPDATE)
    public Result<Void> setCodeRuleEnabled(@PathVariable Integer id, @RequestBody @Valid EquipmentEnabledUpdateDTO dto) { service.setCodeRuleEnabled(id, dto.getEnabled()); return Result.success(null); }

    @DeleteMapping("/code-rules/{id}")
    @PreAuthorize("hasAuthority('equipment:code-rule:manage')")
    @LogOperation(value = "设备编号规则", type = OperationType.DELETE)
    public Result<Void> deleteCodeRule(@PathVariable Integer id) { service.deleteCodeRule(id); return Result.success(null); }
}
