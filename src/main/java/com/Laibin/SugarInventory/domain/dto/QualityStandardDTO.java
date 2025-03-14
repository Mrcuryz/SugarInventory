package com.Laibin.SugarInventory.domain.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@EqualsAndHashCode(callSuper = true)
@Data
public class QualityStandardDTO extends BaseDTO {
    private String productType; // '黄冰糖' 或 '白冰糖'
    private String standardName;
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

    @Override
    public Integer getId() {
        return null;
    }
}
