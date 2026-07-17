package com.Laibin.SugarInventory.production.domain.vo;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Value
@Builder
public class ProductionMaterialCandidatesVO {
    String dataScope;
    String orderRef;
    long total;
    int page;
    int size;
    List<Row> records;
    List<String> limitations;

    @Value
    @Builder
    public static class Row {
        String palletCode;
        String productName;
        String productStatus;
        LocalDate productionDate;
        String quantityText;
        Integer quantity;
        String unit;
        Integer pieces;
        String warehouseName;
        String positionText;
        BigDecimal weight;
    }
}
