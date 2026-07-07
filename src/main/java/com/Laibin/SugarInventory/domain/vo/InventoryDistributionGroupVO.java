package com.Laibin.SugarInventory.domain.vo;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;
import java.util.List;

@Value
@Builder
public class InventoryDistributionGroupVO {
    String groupLabel;
    String warehouseLabel;
    String productLabel;
    long rawFullPallets;
    long rawLoosePieces;
    Long normalizedPallets;
    Long normalizedLoosePieces;
    long totalEquivalentPieces;
    String stockText;
    String totalWeightText;
    long palletCount;
    long warehouseCount;
    long productCount;
    String percentageText;
    LocalDate latestInboundTime;
    List<String> riskLabels;
    String calculationNote;
}
