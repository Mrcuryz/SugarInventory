package com.Laibin.SugarInventory.production.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ProductionDailyMetricValueDTO {
    @NotBlank
    @Size(max = 80)
    private String metricCode;
    private BigDecimal dailyActual;
    private BigDecimal convertedTons;
    private BigDecimal monthQuantity;
    private BigDecimal monthTons;
    private BigDecimal yearTons;

    @Size(max = 500)
    private String remark;
}
