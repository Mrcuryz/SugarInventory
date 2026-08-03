package com.Laibin.SugarInventory.analytics.domain.vo;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

@Value
@Builder
public class RegisteredReportMetricComparisonVO {
    String metricCode;
    String metricLabel;
    String unit;
    boolean additive;
    BigDecimal currentValue;
    BigDecimal comparisonValue;
    BigDecimal absoluteChange;
    BigDecimal percentChange;
    BigDecimal currentDailyAverage;
    BigDecimal comparisonDailyAverage;
    BigDecimal dailyAverageAbsoluteChange;
    BigDecimal dailyAveragePercentChange;
    String note;
}
