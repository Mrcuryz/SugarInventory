package com.Laibin.SugarInventory.inventoryhistory.domain;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class InventoryBalanceAggregate {
    private Integer productId;
    private String productName;
    private Integer warehouseId;
    private String warehouseName;
    private Integer totalPieces;
    private BigDecimal totalWeightKg;
}
