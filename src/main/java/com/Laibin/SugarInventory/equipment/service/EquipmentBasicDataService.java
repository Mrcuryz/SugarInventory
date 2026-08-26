package com.Laibin.SugarInventory.equipment.service;

import com.Laibin.SugarInventory.common.PageResult;
import com.Laibin.SugarInventory.equipment.domain.dto.EquipmentBasicDataDTOs.EquipmentBasicQueryDTO;
import com.Laibin.SugarInventory.equipment.domain.dto.EquipmentBasicDataDTOs.EquipmentCategorySaveDTO;
import com.Laibin.SugarInventory.equipment.domain.dto.EquipmentBasicDataDTOs.EquipmentCodeRuleSaveDTO;
import com.Laibin.SugarInventory.equipment.domain.dto.EquipmentBasicDataDTOs.EquipmentManufacturerSaveDTO;
import com.Laibin.SugarInventory.equipment.domain.dto.EquipmentBasicDataDTOs.EquipmentTypeSaveDTO;
import com.Laibin.SugarInventory.equipment.domain.dto.EquipmentBasicDataDTOs.EquipmentUnitSaveDTO;
import com.Laibin.SugarInventory.equipment.domain.vo.EquipmentBasicDataVOs.EquipmentCategoryVO;
import com.Laibin.SugarInventory.equipment.domain.vo.EquipmentBasicDataVOs.EquipmentCodeRuleVO;
import com.Laibin.SugarInventory.equipment.domain.vo.EquipmentBasicDataVOs.EquipmentManufacturerVO;
import com.Laibin.SugarInventory.equipment.domain.vo.EquipmentBasicDataVOs.EquipmentOptionVO;
import com.Laibin.SugarInventory.equipment.domain.vo.EquipmentBasicDataVOs.EquipmentTypeVO;
import com.Laibin.SugarInventory.equipment.domain.vo.EquipmentBasicDataVOs.EquipmentUnitVO;

import java.util.List;

public interface EquipmentBasicDataService {
    PageResult<EquipmentUnitVO> pageUnits(EquipmentBasicQueryDTO query);
    List<EquipmentOptionVO> unitOptions();
    EquipmentUnitVO saveUnit(Integer id, EquipmentUnitSaveDTO dto);
    void setUnitEnabled(Integer id, boolean enabled);
    void deleteUnit(Integer id);

    PageResult<EquipmentCategoryVO> pageCategories(EquipmentBasicQueryDTO query);
    List<EquipmentOptionVO> categoryOptions();
    EquipmentCategoryVO saveCategory(Integer id, EquipmentCategorySaveDTO dto);
    void setCategoryEnabled(Integer id, boolean enabled);
    void deleteCategory(Integer id);

    PageResult<EquipmentManufacturerVO> pageManufacturers(EquipmentBasicQueryDTO query);
    List<EquipmentOptionVO> manufacturerOptions();
    EquipmentManufacturerVO saveManufacturer(Integer id, EquipmentManufacturerSaveDTO dto, Integer operatorId);
    void deleteManufacturer(Integer id);

    PageResult<EquipmentTypeVO> pageRenovationTypes(EquipmentBasicQueryDTO query);
    List<EquipmentOptionVO> renovationTypeOptions();
    EquipmentTypeVO saveRenovationType(Integer id, EquipmentTypeSaveDTO dto);
    void setRenovationTypeEnabled(Integer id, boolean enabled);
    void deleteRenovationType(Integer id);

    PageResult<EquipmentTypeVO> pageRepairTypes(EquipmentBasicQueryDTO query);
    List<EquipmentOptionVO> repairTypeOptions();
    EquipmentTypeVO saveRepairType(Integer id, EquipmentTypeSaveDTO dto);
    void setRepairTypeEnabled(Integer id, boolean enabled);
    void deleteRepairType(Integer id);

    PageResult<EquipmentCodeRuleVO> pageCodeRules(EquipmentBasicQueryDTO query);
    EquipmentCodeRuleVO saveCodeRule(Integer id, EquipmentCodeRuleSaveDTO dto);
    void setCodeRuleEnabled(Integer id, boolean enabled);
    void deleteCodeRule(Integer id);
}
