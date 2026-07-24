package com.Laibin.SugarInventory.mapper.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class InventoryQualityRow {
    private Integer assayId;
    private Integer productId;
    private String productName;
    private String packagingMethod;
    private BigDecimal weightPerPiece;
    private Integer piecesPerPallet;
    private LocalDate productionDate;
    private String warehouseName;
    private Long rawFullPallets;
    private Long rawLoosePieces;
    private Long totalEquivalentPieces;
    private BigDecimal totalWeight;
    private Long palletCount;
    private String judgeResult;
    private String appliedStandardName;
    private Integer appliedStandardVersion;
    private Integer failedMetricCount;
    private String failedMetricsJson;
    private BigDecimal metricValue;
}
