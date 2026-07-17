package com.Laibin.SugarInventory.domain.vo;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;
import java.util.List;

@Value
@Builder
public class ProductWithoutRecentAssayGroupVO {
    String groupLabel;
    String productLabel;
    String warehouseLabel;
    long rawFullPallets;
    long rawLoosePieces;
    Long normalizedPallets;
    Long normalizedLoosePieces;
    long totalEquivalentPieces;
    String stockText;
    String totalWeightText;
    long inventoryRecordCount;
    long palletCount;
    long warehouseCount;
    long productCount;
    LocalDate latestInboundTime;
    List<String> warehouseLabels;
    List<String> riskLabels;
    String nextActionLabel;
    String calculationNote;
}
