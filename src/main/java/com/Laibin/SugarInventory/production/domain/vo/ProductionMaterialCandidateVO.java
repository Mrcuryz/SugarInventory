package com.Laibin.SugarInventory.production.domain.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class ProductionMaterialCandidateVO {
    private Integer inventoryId;
    private Integer palletCodeId;
    private String palletCode;
    private Integer productId;
    private String productName;
    private String productStatus;
    private LocalDate productionDate;
    private String quantityText;
    private Integer quantity;
    private String unit;
    private Integer pieces;
    private Integer warehouseId;
    private String warehouseName;
    private String side;
    private Integer rowNumber;
    private Integer layer;
    private BigDecimal weight;
}
