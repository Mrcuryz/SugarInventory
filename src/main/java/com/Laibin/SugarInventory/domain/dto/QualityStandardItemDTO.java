package com.Laibin.SugarInventory.domain.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class QualityStandardItemDTO {
    private Integer id;
    private String metricCode;
    private String metricName;
    private BigDecimal minValue;
    private BigDecimal maxValue;
    private String unit;
    private String compareType;
    private Integer sortOrder;
    private String remark;
}
