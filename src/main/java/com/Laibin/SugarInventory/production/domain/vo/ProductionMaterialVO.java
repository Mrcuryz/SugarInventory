package com.Laibin.SugarInventory.production.domain.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class ProductionMaterialVO {
    private Long id;
    private Long productionOrderId;
    private String orderNo;
    private String orderStatus;
    private Integer palletCodeId;
    private String palletCode;
    private Integer productId;
    private String productName;
    private String productStatus;
    private LocalDate productionDate;
    private Integer warehouseId;
    private String warehouseName;
    private String side;
    private Integer rowNumber;
    private Integer layer;
    private Integer quantity;
    private String unit;
    private Integer pieces;
    private Integer totalPieces;
    private BigDecimal totalWeight;
    private String status;
    private String pickedByName;
    private LocalDateTime pickedAt;
    private String remark;
}
