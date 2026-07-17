package com.Laibin.SugarInventory.mapper.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class AssayStandardCoverageGroupRow {
    private Integer productId;
    private String productName;
    private String packagingMethod;
    private BigDecimal weightPerPiece;
    private Integer piecesPerPallet;
    private Long rawFullPallets;
    private Long rawLoosePieces;
    private Long totalEquivalentPieces;
    private BigDecimal totalWeight;
    private Long inventoryRecordCount;
    private Long palletCount;
    private Long warehouseCount;
    private Integer minPiecesPerPallet;
    private Integer maxPiecesPerPallet;
    private LocalDate latestInboundTime;
    private String warehouseNames;
    private Long relationCount;
    private Long enabledRelationCount;
    private Long activeStandardCount;
    private Long incompleteActiveStandardCount;
}
