package com.Laibin.SugarInventory.analytics.domain.vo;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;

@Value
@Builder
public class RegisteredReportMetricPeriodPointVO {
    String periodLabel;
    LocalDate periodStart;
    LocalDate periodEnd;
    int sampleCount;
    BigDecimal averageValue;
    BigDecimal medianValue;
    BigDecimal minimumValue;
    BigDecimal maximumValue;
}
