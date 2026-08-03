package com.Laibin.SugarInventory.analytics.domain.vo;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;

@Value
@Builder
public class RegisteredReportDailyPointVO {
    LocalDate businessDate;
    int outputRecordCount;
    int productionOrderCount;
    BigDecimal totalWeightKg;
    int totalBoardCount;
    int loosePieceCount;
    int totalPieces;
}
