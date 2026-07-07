package com.Laibin.SugarInventory.mapper.model;

import lombok.Data;

import java.time.LocalDate;
import java.math.BigDecimal;

@Data
public class InventoryDistributionGroupRow {
    private String warehouseName;
    private String productName;
    private String packagingMethod;
    private BigDecimal weightPerPiece;
    private Integer piecesPerPallet;
    private Long rawFullPallets;
    private Long rawLoosePieces;
    private Long totalEquivalentPieces;
    private BigDecimal totalWeight;
    private Long palletCount;
    private Long warehouseCount;
    private Long productCount;
    private Long missingAssayCount;
    private Long failedAssayCount;
    private Long noStandardAssayCount;
    private Long abnormalPalletCount;
    private Integer minPiecesPerPallet;
    private Integer maxPiecesPerPallet;
    private LocalDate latestInboundTime;
}
