package com.Laibin.SugarInventory.equipment.domain.assembler;

import com.Laibin.SugarInventory.equipment.domain.po.EquipmentAssetPO;
import com.Laibin.SugarInventory.equipment.domain.po.EquipmentCategoryPO;
import com.Laibin.SugarInventory.equipment.domain.po.EquipmentCodeRulePO;
import com.Laibin.SugarInventory.equipment.domain.po.EquipmentManufacturerPO;
import com.Laibin.SugarInventory.equipment.domain.po.EquipmentRenovationTypePO;
import com.Laibin.SugarInventory.equipment.domain.po.EquipmentRepairRecordPO;
import com.Laibin.SugarInventory.equipment.domain.po.EquipmentRepairTypePO;
import com.Laibin.SugarInventory.equipment.domain.po.EquipmentUnitPO;
import com.Laibin.SugarInventory.equipment.domain.vo.EquipmentAssetVO;
import com.Laibin.SugarInventory.equipment.domain.vo.EquipmentBasicDataVOs.EquipmentCategoryVO;
import com.Laibin.SugarInventory.equipment.domain.vo.EquipmentBasicDataVOs.EquipmentCodeRuleVO;
import com.Laibin.SugarInventory.equipment.domain.vo.EquipmentBasicDataVOs.EquipmentManufacturerVO;
import com.Laibin.SugarInventory.equipment.domain.vo.EquipmentBasicDataVOs.EquipmentTypeVO;
import com.Laibin.SugarInventory.equipment.domain.vo.EquipmentBasicDataVOs.EquipmentUnitVO;
import com.Laibin.SugarInventory.equipment.domain.vo.EquipmentRepairVO;

import java.util.Map;

public final class EquipmentAssembler {
    private EquipmentAssembler() {
    }

    public static EquipmentAssetVO toAssetVO(EquipmentAssetPO po,
                                             Map<Integer, EquipmentUnitPO> units,
                                             Map<Integer, EquipmentCategoryPO> categories,
                                             Map<Integer, EquipmentManufacturerPO> manufacturers,
                                             Map<Integer, EquipmentRenovationTypePO> renovationTypes,
                                             Map<Integer, EquipmentAssetPO> parents) {
        EquipmentAssetVO vo = new EquipmentAssetVO();
        vo.setId(po.getId());
        vo.setEquipmentCode(po.getEquipmentCode());
        vo.setEquipmentSubNo(po.getEquipmentSubNo());
        vo.setUnitId(po.getUnitId());
        EquipmentUnitPO unit = units.get(po.getUnitId());
        if (unit != null) {
            vo.setUnitCode(unit.getUnitCode());
            vo.setUnitName(unit.getUnitName());
        }
        vo.setCategoryId(po.getCategoryId());
        EquipmentCategoryPO category = categories.get(po.getCategoryId());
        if (category != null) {
            vo.setCategoryName(category.getCategoryName());
            vo.setMajorCode(category.getMajorCode());
        }
        vo.setRenovationTypeId(po.getRenovationTypeId());
        EquipmentRenovationTypePO renovationType = renovationTypes.get(po.getRenovationTypeId());
        if (renovationType != null) vo.setRenovationTypeName(renovationType.getTypeName());
        vo.setParentEquipmentId(po.getParentEquipmentId());
        EquipmentAssetPO parent = parents.get(po.getParentEquipmentId());
        if (parent != null) {
            vo.setParentEquipmentCode(parent.getEquipmentCode());
            vo.setParentEquipmentName(parent.getEquipmentName());
        }
        vo.setManufacturerId(po.getManufacturerId());
        EquipmentManufacturerPO manufacturer = manufacturers.get(po.getManufacturerId());
        if (manufacturer != null) {
            vo.setManufacturerCode(manufacturer.getManufacturerCode());
            vo.setManufacturerName(manufacturer.getManufacturerName());
        }
        vo.setEquipmentName(po.getEquipmentName());
        vo.setModel(po.getModel());
        vo.setRatedPowerKw(po.getRatedPowerKw());
        vo.setRatedVoltageV(po.getRatedVoltageV());
        vo.setRatedCurrentA(po.getRatedCurrentA());
        vo.setRatedSpeedRpm(po.getRatedSpeedRpm());
        vo.setPrice(po.getPrice());
        vo.setInstallationCost(po.getInstallationCost());
        vo.setInstallationLocation(po.getInstallationLocation());
        vo.setFactorySerialNo(po.getFactorySerialNo());
        vo.setProductionDate(po.getProductionDate());
        vo.setArrivalDate(po.getArrivalDate());
        vo.setCommissioningDate(po.getCommissioningDate());
        vo.setRemark(po.getRemark());
        vo.setVersion(po.getVersion());
        vo.setCreatedAt(po.getCreatedAt());
        vo.setUpdatedAt(po.getUpdatedAt());
        return vo;
    }

    public static EquipmentRepairVO toRepairVO(EquipmentRepairRecordPO po,
                                               Map<Integer, EquipmentAssetPO> assets,
                                               Map<Integer, EquipmentUnitPO> units,
                                               Map<Integer, EquipmentCategoryPO> categories,
                                               Map<Integer, EquipmentRepairTypePO> repairTypes) {
        EquipmentRepairVO vo = new EquipmentRepairVO();
        vo.setId(po.getId());
        vo.setEquipmentId(po.getEquipmentId());
        EquipmentAssetPO asset = assets.get(po.getEquipmentId());
        if (asset != null) {
            vo.setEquipmentCode(asset.getEquipmentCode());
            vo.setEquipmentName(asset.getEquipmentName());
            vo.setUnitId(asset.getUnitId());
            vo.setCategoryId(asset.getCategoryId());
            EquipmentUnitPO unit = units.get(asset.getUnitId());
            EquipmentCategoryPO category = categories.get(asset.getCategoryId());
            if (unit != null) vo.setUnitName(unit.getUnitName());
            if (category != null) vo.setCategoryName(category.getCategoryName());
        }
        vo.setRepairDate(po.getRepairDate());
        vo.setRepairTypeId(po.getRepairTypeId());
        EquipmentRepairTypePO repairType = repairTypes.get(po.getRepairTypeId());
        if (repairType != null) vo.setRepairTypeName(repairType.getTypeName());
        vo.setRepairPerson(po.getRepairPerson());
        vo.setAcceptancePerson(po.getAcceptancePerson());
        vo.setRepairContent(po.getRepairContent());
        vo.setVersion(po.getVersion());
        vo.setCreatedAt(po.getCreatedAt());
        vo.setUpdatedAt(po.getUpdatedAt());
        return vo;
    }

    public static EquipmentUnitVO toUnitVO(EquipmentUnitPO po) {
        EquipmentUnitVO vo = new EquipmentUnitVO();
        vo.setId(po.getId()); vo.setUnitCode(po.getUnitCode()); vo.setUnitName(po.getUnitName());
        vo.setSortOrder(po.getSortOrder()); vo.setEnabled(po.getEnabled());
        vo.setCreatedAt(po.getCreatedAt()); vo.setUpdatedAt(po.getUpdatedAt());
        return vo;
    }

    public static EquipmentCategoryVO toCategoryVO(EquipmentCategoryPO po) {
        EquipmentCategoryVO vo = new EquipmentCategoryVO();
        vo.setId(po.getId()); vo.setCategoryName(po.getCategoryName()); vo.setMajorCode(po.getMajorCode());
        vo.setDefaultSequenceStart(po.getDefaultSequenceStart()); vo.setDefaultSequenceEnd(po.getDefaultSequenceEnd());
        vo.setSortOrder(po.getSortOrder()); vo.setEnabled(po.getEnabled());
        vo.setCreatedAt(po.getCreatedAt()); vo.setUpdatedAt(po.getUpdatedAt());
        return vo;
    }

    public static EquipmentManufacturerVO toManufacturerVO(EquipmentManufacturerPO po) {
        EquipmentManufacturerVO vo = new EquipmentManufacturerVO();
        vo.setId(po.getId()); vo.setManufacturerCode(po.getManufacturerCode());
        vo.setManufacturerName(po.getManufacturerName()); vo.setAddress(po.getAddress());
        vo.setContactPerson(po.getContactPerson()); vo.setPhone(po.getPhone()); vo.setFax(po.getFax());
        vo.setRemark(po.getRemark()); vo.setCreatedAt(po.getCreatedAt()); vo.setUpdatedAt(po.getUpdatedAt());
        return vo;
    }

    public static EquipmentTypeVO toRenovationTypeVO(EquipmentRenovationTypePO po) {
        EquipmentTypeVO vo = new EquipmentTypeVO();
        vo.setId(po.getId()); vo.setTypeName(po.getTypeName()); vo.setSortOrder(po.getSortOrder());
        vo.setEnabled(po.getEnabled()); vo.setCreatedAt(po.getCreatedAt()); vo.setUpdatedAt(po.getUpdatedAt());
        return vo;
    }

    public static EquipmentTypeVO toRepairTypeVO(EquipmentRepairTypePO po) {
        EquipmentTypeVO vo = new EquipmentTypeVO();
        vo.setId(po.getId()); vo.setTypeName(po.getTypeName()); vo.setSortOrder(po.getSortOrder());
        vo.setEnabled(po.getEnabled()); vo.setCreatedAt(po.getCreatedAt()); vo.setUpdatedAt(po.getUpdatedAt());
        return vo;
    }

    public static EquipmentCodeRuleVO toCodeRuleVO(EquipmentCodeRulePO po,
                                                   Map<Integer, EquipmentUnitPO> units,
                                                   Map<Integer, EquipmentCategoryPO> categories) {
        EquipmentCodeRuleVO vo = new EquipmentCodeRuleVO();
        vo.setId(po.getId()); vo.setCompanyCode(po.getCompanyCode()); vo.setUnitId(po.getUnitId());
        vo.setCategoryId(po.getCategoryId()); vo.setSectionCode(po.getSectionCode());
        vo.setSequenceStart(po.getSequenceStart()); vo.setSequenceEnd(po.getSequenceEnd());
        vo.setNextSequence(po.getNextSequence()); vo.setEnabled(po.getEnabled()); vo.setVersion(po.getVersion());
        vo.setCreatedAt(po.getCreatedAt()); vo.setUpdatedAt(po.getUpdatedAt());
        EquipmentUnitPO unit = units.get(po.getUnitId());
        if (unit != null) { vo.setUnitCode(unit.getUnitCode()); vo.setUnitName(unit.getUnitName()); }
        EquipmentCategoryPO category = categories.get(po.getCategoryId());
        if (category != null) { vo.setCategoryName(category.getCategoryName()); vo.setMajorCode(category.getMajorCode()); }
        return vo;
    }
}
