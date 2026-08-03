package com.Laibin.SugarInventory.analytics.domain.vo;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class RegisteredReportCurrentInventoryMetricsVO {
    long productCount;
    long warehouseCount;
    long palletCount;
    long totalEquivalentPieces;
    String totalStockText;
    String totalWeightText;
}
