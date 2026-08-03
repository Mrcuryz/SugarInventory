package com.Laibin.SugarInventory.analytics.domain.vo;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

@Value
@Builder
public class RegisteredReportQualityMetricsVO {
    int assayRecordCount;
    int judgedRecordCount;
    int passCount;
    int failCount;
    int noStandardCount;
    int multipleCandidatesCount;
    BigDecimal passRatePercent;
    int distinctProductCount;
    int distinctStandardVersionCount;
}
