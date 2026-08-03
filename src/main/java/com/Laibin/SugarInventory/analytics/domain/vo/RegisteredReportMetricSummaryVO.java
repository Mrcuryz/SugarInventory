package com.Laibin.SugarInventory.analytics.domain.vo;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

@Value
@Builder
public class RegisteredReportMetricSummaryVO {
    String metricKey;
    String metricName;
    String unit;
    int assayRecordCount;
    int sampleCount;
    int missingValueCount;
    int comparableStandardCount;
    int withinStandardCount;
    int outOfStandardCount;
    int withoutComparableStandardCount;
    BigDecimal withinStandardRatePercent;
    BigDecimal averageValue;
    BigDecimal medianValue;
    BigDecimal minimumValue;
    BigDecimal maximumValue;
    BigDecimal p10Value;
    BigDecimal p90Value;
}
