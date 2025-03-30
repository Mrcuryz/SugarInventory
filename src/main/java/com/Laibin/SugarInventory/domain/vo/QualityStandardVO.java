package com.Laibin.SugarInventory.domain.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@EqualsAndHashCode(callSuper = true)
@Data
public class QualityStandardVO extends BaseVO {
    private Integer id;
    private String standardName;
    private String productType;
    private BigDecimal colorMin;
    private BigDecimal colorMax;
    private BigDecimal reducingSugarMin;
    private BigDecimal reducingSugarMax;
    private BigDecimal dryWeightMin;
    private BigDecimal dryWeightMax;
    private BigDecimal conductivityAshMin;
    private BigDecimal conductivityAshMax;
    private BigDecimal sucroseMin;
    private BigDecimal sucroseMax;
    private BigDecimal insolubleImpurityMax;
    private BigDecimal insolubleImpurityMin;
    private BigDecimal phMin;
    private BigDecimal phMax;
}

