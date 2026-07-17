package com.Laibin.SugarInventory.domain.vo;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Value
@Builder
public class StockDocumentsAgentVO {
    String dataScope;
    String documentType;
    long total;
    int page;
    int size;
    List<Row> records;
    List<String> limitations;

    @Value
    @Builder
    public static class Row {
        String documentType;
        String productName;
        String warehouseName;
        Integer quantity;
        Integer pieces;
        String unit;
        BigDecimal totalWeight;
        LocalDate businessDate;
        LocalDate sourceEntryDate;
        String operatorName;
        String screenMeshName;
        LocalDateTime createdAt;
    }
}
