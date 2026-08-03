package com.Laibin.SugarInventory.analytics.domain.vo;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;

@Value
@Builder
public class RegisteredReportProductionFlowOrderBreakdownVO {
    String orderNo;
    String orderTypeLabel;
    String orderStatusLabel;
    LocalDate productionDate;
    String inputSourceLabel;
    int inputRecordCount;
    int inputPalletCount;
    int inputTotalPieces;
    BigDecimal inputWeightKg;
    int stableOutputRecordCount;
    int stableOutputTotalPieces;
    BigDecimal stableOutputWeightKg;
    String outputProductNames;
    String completenessLabel;
}
