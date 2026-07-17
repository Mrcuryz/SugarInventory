package com.Laibin.SugarInventory.production.domain.vo;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Value
@Builder
public class ProductionInProcessMaterialsVO {
    String dataScope;
    long total;
    int page;
    int size;
    List<Row> records;
    List<String> limitations;

    @Value
    @Builder
    public static class Row {
        String orderNo;
        String orderStatus;
        String palletCode;
        String productName;
        String productStatus;
        LocalDate productionDate;
        String warehouseName;
        String positionText;
        Integer quantity;
        String unit;
        Integer pieces;
        Integer totalPieces;
        BigDecimal totalWeight;
        String materialStatus;
        String pickedByName;
        LocalDateTime pickedAt;
    }
}
