package com.Laibin.SugarInventory.domain.vo;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AssayReportMetricVO {
    String metricCode;
    String metricName;
    String actualValueText;
    String standardRangeText;
    String resultLabel;
    String reason;
}
