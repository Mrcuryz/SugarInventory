package com.Laibin.SugarInventory.analytics.domain.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class ProductionFlowDailyInputRowVO {
    private LocalDate businessDate;
    private Integer inputRecordCount;
    private Integer inputOrderCount;
    private Integer inputPalletCount;
    private Integer inputBoardCount;
    private Integer inputLoosePieceCount;
    private Integer inputTotalPieces;
    private BigDecimal inputWeightKg;
    private Integer rowsMissingWeight;
    private Integer rowsMissingPieceConversion;
    private LocalDateTime latestRecordedAt;
}
