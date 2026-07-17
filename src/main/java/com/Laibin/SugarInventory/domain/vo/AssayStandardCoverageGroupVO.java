package com.Laibin.SugarInventory.domain.vo;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;
import java.util.List;

@Value
@Builder
public class AssayStandardCoverageGroupVO {
    String groupLabel;
    String productLabel;
    String coverageLabel;
    String affectedStockText;
    long rawFullPallets;
    long rawLoosePieces;
    Long normalizedPallets;
    Long normalizedLoosePieces;
    long totalEquivalentPieces;
    String totalWeightText;
    long inventoryRecordCount;
    long palletCount;
    long warehouseCount;
    LocalDate latestInboundTime;
    List<String> warehouseLabels;
    List<String> riskLabels;
}
