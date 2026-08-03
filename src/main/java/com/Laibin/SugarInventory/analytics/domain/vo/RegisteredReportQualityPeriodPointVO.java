package com.Laibin.SugarInventory.analytics.domain.vo;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;

@Value
@Builder
public class RegisteredReportQualityPeriodPointVO {
    String periodLabel;
    LocalDate periodStart;
    LocalDate periodEnd;
    int assayRecordCount;
    int judgedRecordCount;
    int passCount;
    int failCount;
    int noStandardCount;
    int multipleCandidatesCount;
    BigDecimal passRatePercent;
}
