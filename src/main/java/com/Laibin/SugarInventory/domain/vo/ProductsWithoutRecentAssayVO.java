package com.Laibin.SugarInventory.domain.vo;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class ProductsWithoutRecentAssayVO {
    String scopeLabel;
    String warehouseScopeLabel;
    String dateRangeLabel;
    String population;
    String groupBy;
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
    List<ProductWithoutRecentAssayGroupVO> groups;
    List<String> riskLabels;
    List<String> notes;
}
