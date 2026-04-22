package com.Laibin.SugarInventory.domain.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class AssayFailedMetricVO {
    private String metricCode;
    private String metricName;
    private BigDecimal actualValue;
    private String unit;
    private String compareType;
    private BigDecimal minValue;
    private BigDecimal maxValue;
    private String reason;
}
