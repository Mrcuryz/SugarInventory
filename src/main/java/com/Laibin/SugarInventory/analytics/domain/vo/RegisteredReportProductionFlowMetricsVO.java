package com.Laibin.SugarInventory.analytics.domain.vo;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

@Value
@Builder
public class RegisteredReportProductionFlowMetricsVO {
    int materialInputRecordCount;
    int materialInputOrderCount;
    int materialInputPalletCount;
    int materialInputBoardCount;
    int materialInputLoosePieceCount;
    int materialInputTotalPieces;
    BigDecimal materialInputWeightKg;
    int stableOutputRecordCount;
    int stableOutputOrderCount;
    int stableOutputBoardCount;
    int stableOutputLoosePieceCount;
    int stableOutputTotalPieces;
    BigDecimal stableOutputWeightKg;
    int cohortOrderCount;
    int completedOrderCount;
    int completedOrdersWithInputCount;
    int completedOrdersMissingInputCount;
    int completedOrdersWithStableOutputCount;
    int completedOrdersMissingOutputCount;
    int ordersWithInputCount;
    int ordersMissingInputCount;
    int ordersWithStableOutputCount;
    int ordersMissingOutputCount;
    BigDecimal cohortMaterialInputWeightKg;
    BigDecimal cohortBoilingInputWeightKg;
    BigDecimal cohortStableOutputWeightKg;
}
