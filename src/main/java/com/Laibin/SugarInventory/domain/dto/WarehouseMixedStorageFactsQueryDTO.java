package com.Laibin.SugarInventory.domain.dto;

import lombok.Data;

@Data
public class WarehouseMixedStorageFactsQueryDTO {
    private Integer warehouseId;
    private String factType = "ANY";
    private Integer limit = 20;
}
