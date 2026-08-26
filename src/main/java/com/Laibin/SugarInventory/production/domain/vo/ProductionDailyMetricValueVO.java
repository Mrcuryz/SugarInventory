package com.Laibin.SugarInventory.production.domain.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ProductionDailyMetricValueVO {
    private String metricCode;
    private String metricName;
    private String unit;
    private String position;
    private BigDecimal dailyActual;
    private BigDecimal convertedTons;
    private BigDecimal monthQuantity;
    private BigDecimal monthTons;
    private BigDecimal yearTons;
    private String remark;
    private Integer displayOrder;
}
