package com.Laibin.SugarInventory.domain.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class PalletTaskPageVO {
    private Integer taskId;
    private String taskType;
    private String bizScene;
    private String taskStatus;
    private Integer palletCodeId;
    private String code;
    private Integer targetWarehouseId;
    private String targetWarehouseName;
    private String targetSide;
    private Integer productId;
    private String productName;
    private String productType;
    private String productStatus;
    private BigDecimal totalWeight;
    private LocalDate productionDate;
    private Integer screenMeshId;
    private String screenMeshName;
    private Boolean hasSemiItems;
    private Integer semiItemCount;
    private List<TaskSemiItemVO> semiItems;
    private Integer assayId;
    private String createdBy;
    private LocalDateTime createdAt;
    private String confirmedBy;
    private LocalDateTime confirmedAt;
    private String remark;
    private String operationBatchNo;
    private Long productionOrderId;
    private String productionOrderNo;
    private String productionOrderType;
    private String productionOrderStatus;
    private Long productionOutputCodeId;
    private Integer productionOutputQuantity;
    private String productionOutputUnit;
    private Integer productionOutputPieces;
    private String productionLabelBatchNo;
}
