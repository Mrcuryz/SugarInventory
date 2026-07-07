package com.Laibin.SugarInventory.mapper.model;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class InventoryDistributionAggregateRow {
    private Long rawFullPallets;
    private Long rawLoosePieces;
    private Long warehouseCount;
    private Long productCount;
    private Long palletCount;
    private Long totalEquivalentPieces;
    private BigDecimal totalWeight;
    private Long missingAssayCount;
    private Long failedAssayCount;
    private Long noStandardAssayCount;
    private Long abnormalPalletCount;
    private Integer minPiecesPerPallet;
    private Integer maxPiecesPerPallet;
}
