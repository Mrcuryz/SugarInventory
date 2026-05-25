package com.Laibin.SugarInventory.production.domain.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ProductionLabelCodeVO {
    private Long id;
    private Long batchId;
    private String batchNo;
    private Integer sequenceNo;
    private Integer palletCodeId;
    private String palletCode;
    private Integer productId;
    private String productName;
    private String qrContent;
    private String status;
    private Long usedOutputCodeId;
    private LocalDateTime usedAt;
    private LocalDateTime recycledAt;
    private LocalDateTime createdAt;
}
