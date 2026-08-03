package com.Laibin.SugarInventory.analytics.domain.vo;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

@Value
@Builder
public class RegisteredReportMetricStandardBreakdownVO {
    String standardLabel;
    String rangeLabel;
    String unit;
    int sampleCount;
    int withinStandardCount;
    int outOfStandardCount;
    BigDecimal withinStandardRatePercent;
}
