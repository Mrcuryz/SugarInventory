package com.Laibin.SugarInventory.equipment.domain.vo;

import lombok.Data;

import java.util.List;

@Data
public class EquipmentAssetDetailVO {
    private EquipmentAssetVO asset;
    private List<EquipmentAssetVO> auxiliaryEquipment;
    private List<EquipmentRepairVO> repairRecords;
}
