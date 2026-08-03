package com.Laibin.SugarInventory.analytics.domain.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class ProductionFlowOrderRowVO {
    private String orderNo;
    private String orderType;
    private String orderStatus;
    private LocalDate productionDate;
    private LocalDateTime completedAt;
    private Integer materialInputRecordCount;
    private Integer materialInputPalletCount;
    private Integer materialInputTotalPieces;
    private BigDecimal materialInputWeightKg;
    private Integer boilingInputUsageCount;
    private BigDecimal boilingInputWeightKg;
    private Integer stableOutputRecordCount;
    private Integer stableOutputTotalPieces;
    private BigDecimal stableOutputWeightKg;
    private String outputProductNames;
    private LocalDateTime latestRecordedAt;
}
