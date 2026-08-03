package com.Laibin.SugarInventory.analytics.domain.vo;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

@Value
@Builder
public class RegisteredReportMetricsVO {
    int outputRecordCount;
    int productionOrderCount;
    BigDecimal totalWeightKg;
    int totalBoardCount;
    int loosePieceCount;
    int totalPieces;
    int requiredQrCount;
    int boundQrCount;
    int inboundQrCount;
}
