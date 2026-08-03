package com.Laibin.SugarInventory.analytics.domain.vo;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

@Value
@Builder
public class RegisteredReportInventoryTrendProductBreakdownVO {
    Integer productId;
    String productName;
    long openingPieces;
    long closingPieces;
    long netChangePieces;
    BigDecimal openingWeightKg;
    BigDecimal closingWeightKg;
    BigDecimal netChangeWeightKg;
}
