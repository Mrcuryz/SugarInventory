package com.Laibin.SugarInventory.mapper.model;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ProductsWithoutRecentAssayAggregateRow {
    private Long rawFullPallets;
    private Long rawLoosePieces;
    private Long totalEquivalentPieces;
    private BigDecimal totalWeight;
    private Long inventoryRecordCount;
    private Long palletCount;
    private Long warehouseCount;
    private Long productCount;
    private Integer minPiecesPerPallet;
    private Integer maxPiecesPerPallet;
}
