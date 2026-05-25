package com.Laibin.SugarInventory.production.domain.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class ProductionLabelBatchVO {
    private Long id;
    private Long productionOrderId;
    private String orderNo;
    private String batchNo;
    private Integer productId;
    private String productName;
    private Integer reservedCount;
    private Integer usedCount;
    private Integer recycledCount;
    private String status;
    private LocalDateTime printedAt;
    private LocalDateTime closedAt;
    private LocalDateTime createdAt;
    private String remark;
    private List<ProductionLabelCodeVO> codes;
}
