package com.Laibin.SugarInventory.analytics.domain.vo;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;

@Value
@Builder
public class RegisteredReportProductionFlowDailyPointVO {
    LocalDate businessDate;
    int materialInputRecordCount;
    int materialInputOrderCount;
    int materialInputPalletCount;
    int materialInputTotalPieces;
    BigDecimal materialInputWeightKg;
    int stableOutputRecordCount;
    int stableOutputOrderCount;
    int stableOutputTotalPieces;
    BigDecimal stableOutputWeightKg;
}
