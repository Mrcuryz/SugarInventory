package com.Laibin.SugarInventory.production.domain.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ProductionTraceOutputCodeRowVO {
    private Long outputId;
    private Long productionOrderId;
    private String orderNo;
    private String orderType;
    private String orderStatus;
    private String outputProductName;
    private String outputProductStatus;
    private Integer boardCount;
    private Integer pieceCount;
    private Integer outputTotalPieces;
    private Integer piecesPerPallet;
    private String outputStatus;
    private LocalDateTime outputCreatedAt;
    private Long outputCodeId;
    private Integer palletCodeId;
    private Integer palletCycleNo;
    private String palletCode;
    private Long labelBatchId;
    private String labelBatchNo;
    private Integer labelBatchReservedCount;
    private Integer labelBatchUsedCount;
    private Integer labelBatchRecycledCount;
    private String labelBatchStatus;
    private LocalDateTime labelBatchCreatedAt;
    private Integer quantity;
    private String unit;
    private Integer pieces;
    private Integer palletTaskId;
    private Integer inventoryId;
    private String codeStatus;
    private LocalDateTime codeCreatedAt;
    private LocalDateTime inboundAt;
    private Integer warehouseId;
    private String warehouseName;
}