package com.Laibin.SugarInventory.domain.vo;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class AssayStandardCoverageVO {
    String scopeLabel;
    String dateRangeLabel;
    String coverageType;
    long totalGroups;
    long rawFullPallets;
    long rawLoosePieces;
    Long normalizedPallets;
    Long normalizedLoosePieces;
    long totalEquivalentPieces;
    String totalStockText;
    String totalWeightText;
    long inventoryRecordCount;
    long palletCount;
    long warehouseCount;
    long productCount;
    String summaryText;
    List<AssayStandardCoverageGroupVO> groups;
    List<String> riskLabels;
    List<String> notes;
}
