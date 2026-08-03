package com.Laibin.SugarInventory.analytics.domain.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PalletTaskCycleFactRowVO {
    private Integer taskId;
    private String palletCode;
    private String taskType;
    private String status;
    private String productName;
    private String productStatus;
    private String targetWarehouseName;
    private String operationBatchNo;
    private LocalDateTime createdAt;
    private LocalDateTime confirmedAt;
    private Boolean hasFlowRecord;
}
