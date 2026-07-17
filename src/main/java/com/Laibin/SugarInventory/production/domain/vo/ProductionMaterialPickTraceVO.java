package com.Laibin.SugarInventory.production.domain.vo;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Value
@Builder
public class ProductionMaterialPickTraceVO {
    String dataScope;
    String orderRef;
    String orderNo;
    String orderStatus;
    int materialRecordCount;
    List<MaterialRecord> records;
    List<String> limitations;

    @Value
    @Builder
    public static class MaterialRecord {
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
        String status;
        String pickedByName;
        LocalDateTime pickedAt;
        String remark;
    }
}
