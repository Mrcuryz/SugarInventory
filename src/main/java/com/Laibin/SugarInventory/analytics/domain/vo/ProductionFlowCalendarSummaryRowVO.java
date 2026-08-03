package com.Laibin.SugarInventory.analytics.domain.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ProductionFlowCalendarSummaryRowVO {
    private Integer materialInputRecordCount;
    private Integer materialInputOrderCount;
    private Integer materialInputPalletCount;
    private Integer materialInputBoardCount;
    private Integer materialInputLoosePieceCount;
    private Integer materialInputTotalPieces;
    private BigDecimal materialInputWeightKg;
    private Integer stableOutputRecordCount;
    private Integer stableOutputOrderCount;
    private Integer stableOutputBoardCount;
    private Integer stableOutputLoosePieceCount;
    private Integer stableOutputTotalPieces;
    private BigDecimal stableOutputWeightKg;
    private Integer rowsMissingWeight;
    private Integer rowsMissingPieceConversion;
    private LocalDateTime latestRecordedAt;
}
