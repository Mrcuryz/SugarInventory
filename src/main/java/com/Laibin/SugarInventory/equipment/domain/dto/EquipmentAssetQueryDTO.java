package com.Laibin.SugarInventory.equipment.domain.dto;

import lombok.Data;

@Data
public class EquipmentAssetQueryDTO {
    private String keyword;
    private Integer unitId;
    private Integer categoryId;
    private Integer manufacturerId;
    private Integer renovationTypeId;
    private Boolean auxiliaryOnly;
    private Integer page = 1;
    private Integer size = 10;
}
