package com.Laibin.SugarInventory.production.domain.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ProductionDailyProductLineDTO {
    private Long id;

    @Size(max = 60)
    private String categoryCode;

    private Integer productId;

    @Size(max = 100)
    private String unit;

    private BigDecimal dailyActual;
    private BigDecimal convertedTons;
    private BigDecimal monthQuantity;
    private BigDecimal monthTons;
    private BigDecimal yearTons;

    @Size(max = 500)
    private String remark;

    private Integer displayOrder;
}
