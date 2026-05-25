package com.Laibin.SugarInventory.production.domain.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ProductionOutputCodeVO {
    private Long id;
    private Long productionOrderId;
    private String orderNo;
    private Long outputId;
    private Integer palletCodeId;
    private Long labelCodeId;
    private String palletCode;
    private Integer productId;
    private String productName;
    private Integer quantity;
    private String unit;
    private Integer pieces;
    private Integer palletTaskId;
    private Integer inventoryId;
    private String status;
    private LocalDateTime printedAt;
    private LocalDateTime inboundAt;
    private LocalDateTime createdAt;
    private String remark;
}
