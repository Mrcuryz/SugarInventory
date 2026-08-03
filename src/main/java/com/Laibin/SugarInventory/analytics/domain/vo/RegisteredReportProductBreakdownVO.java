package com.Laibin.SugarInventory.analytics.domain.vo;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

@Value
@Builder
public class RegisteredReportProductBreakdownVO {
    String productName;
    String productStatus;
    int outputRecordCount;
    int productionOrderCount;
    BigDecimal totalWeightKg;
    int totalBoardCount;
    int loosePieceCount;
    int totalPieces;
}
