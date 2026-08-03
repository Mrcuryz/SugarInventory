package com.Laibin.SugarInventory.analytics.domain.vo;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;

@Value
@Builder
public class RegisteredReportInventoryTrendDailyPointVO {
    LocalDate businessDate;
    long totalPieces;
    BigDecimal totalWeightKg;
    long pieceChange;
    BigDecimal weightChangeKg;
    int movementRecordCount;
}
