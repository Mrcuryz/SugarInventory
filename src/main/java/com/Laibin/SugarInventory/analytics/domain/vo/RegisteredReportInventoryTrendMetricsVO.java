package com.Laibin.SugarInventory.analytics.domain.vo;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

@Value
@Builder
public class RegisteredReportInventoryTrendMetricsVO {
    int observationDayCount;
    long openingPieces;
    long closingPieces;
    long netChangePieces;
    BigDecimal openingWeightKg;
    BigDecimal closingWeightKg;
    BigDecimal netChangeWeightKg;
    int increaseDayCount;
    int decreaseDayCount;
    int unchangedDayCount;
}
