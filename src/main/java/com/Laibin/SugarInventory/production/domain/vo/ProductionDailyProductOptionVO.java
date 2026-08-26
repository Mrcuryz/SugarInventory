package com.Laibin.SugarInventory.production.domain.vo;

import lombok.Data;

@Data
public class ProductionDailyProductOptionVO {
    private Integer id;
    private String productName;
    private String productStatus;
    private String productType;
    private String packagingMethod;
}
