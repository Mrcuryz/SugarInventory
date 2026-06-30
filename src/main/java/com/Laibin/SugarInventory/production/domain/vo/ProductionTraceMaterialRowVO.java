package com.Laibin.SugarInventory.production.domain.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ProductionTraceMaterialRowVO {
    private Long id;
    private Long productionOrderId;
    private String orderNo;
    private String orderType;
    private String orderStatus;
    private Integer palletCodeId;
    private Integer palletCycleNo;
    private String palletCode;
    private Integer productId;
    private String productName;
    private String productStatus;
    private Integer warehouseId;
    private String warehouseName;
    private Integer quantity;
    private String unit;
    private Integer pieces;
    private Integer totalPieces;
    private BigDecimal totalWeight;
    private String status;
    private LocalDateTime pickedAt;
}