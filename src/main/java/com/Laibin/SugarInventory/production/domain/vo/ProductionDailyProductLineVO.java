package com.Laibin.SugarInventory.production.domain.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ProductionDailyProductLineVO {
    private Long id;
    private String categoryCode;
    private Integer productId;
    private String productName;
    private String importedProductName;
    private String importError;
    private String productStatus;
    private String productType;
    private String unit;
    private BigDecimal dailyActual;
    private BigDecimal convertedTons;
    private BigDecimal monthQuantity;
    private BigDecimal monthTons;
    private BigDecimal yearTons;
    private String remark;
    private Integer displayOrder;
}
