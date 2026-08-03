package com.Laibin.SugarInventory.analytics.domain.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class ProductionFlowDailyOutputRowVO {
    private LocalDate businessDate;
    private Integer outputRecordCount;
    private Integer outputOrderCount;
    private Integer outputBoardCount;
    private Integer outputLoosePieceCount;
    private Integer outputTotalPieces;
    private BigDecimal outputWeightKg;
    private Integer rowsMissingWeight;
    private Integer rowsMissingPieceConversion;
    private LocalDateTime latestRecordedAt;
}
