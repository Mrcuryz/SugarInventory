package com.Laibin.SugarInventory.domain.vo;

import lombok.Builder;
import lombok.Value;
import java.util.List;

@Value @Builder
public class WarehouseMixedStorageFactsVO {
    String dataScope;
    int count;
    List<Row> records;
    List<String> limitations;

    @Value @Builder
    public static class Row {
        String warehouseName;
        int productCount;
        int productTypeCount;
        int specificationCount;
        int inventoryRecordCount;
        List<String> productLabels;
        List<String> productTypeLabels;
        List<String> productStatusLabels;
        List<String> screenMeshLabels;
        List<String> observedFacts;
    }
}
