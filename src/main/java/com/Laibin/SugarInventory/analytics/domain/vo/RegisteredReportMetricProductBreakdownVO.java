package com.Laibin.SugarInventory.analytics.domain.vo;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

@Value
@Builder
public class RegisteredReportMetricProductBreakdownVO {
    String productName;
    int sampleCount;
    BigDecimal averageValue;
    BigDecimal medianValue;
    BigDecimal minimumValue;
    BigDecimal maximumValue;
}
