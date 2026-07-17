package com.Laibin.SugarInventory.mapper.model;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class PalletLifecycleSummaryRow {
    private Integer palletCodeId;
    private String code;
    private String status;
    private String productName;
    private String productStatus;
    private LocalDate productionDate;
    private Integer currentCycleNo;
    private String warehouseName;
    private Integer quantity;
    private Integer pieces;
    private LocalDate entryDate;
    private Integer assayId;
    private LocalDate assaySampleDate;
    private String assayQualified;
    private LocalDateTime assayCreatedAt;
}
