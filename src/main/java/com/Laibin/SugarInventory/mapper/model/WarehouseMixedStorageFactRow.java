package com.Laibin.SugarInventory.mapper.model;

import lombok.Data;

@Data
public class WarehouseMixedStorageFactRow {
    private String warehouseName;
    private Integer productCount;
    private Integer productTypeCount;
    private Integer specificationCount;
    private Integer inventoryRecordCount;
    private String productLabels;
    private String productTypeLabels;
    private String productStatusLabels;
    private String screenMeshLabels;
}
