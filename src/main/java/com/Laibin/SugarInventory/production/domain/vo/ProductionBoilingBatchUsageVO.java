package com.Laibin.SugarInventory.production.domain.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ProductionBoilingBatchUsageVO {
    private Long id;
    private Long batchId;
    private String batchNo;
    private Long productionOrderId;
    private String orderNo;
    private String orderType;
    private String orderStatus;
    private String usageUnit;
    private BigDecimal usageQuantity;
    private BigDecimal bucketQuantity;
    private BigDecimal weightKg;
    private String status;
    private String remark;
    private String createdByName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
