package com.Laibin.SugarInventory.domain.vo;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Value
@Builder
public class PalletTasksAgentVO {
    String dataScope;
    long total;
    int page;
    int size;
    List<Row> records;
    List<String> limitations;

    @Value
    @Builder
    public static class Row {
        String taskType;
        String bizScene;
        String taskStatus;
        String code;
        String operationBatchNo;
        String targetWarehouseName;
        String targetSide;
        String productName;
        String productType;
        String productStatus;
        BigDecimal totalWeight;
        LocalDate productionDate;
        String screenMeshName;
        Integer semiItemCount;
        String productionOrderNo;
        String productionOrderType;
        String productionOrderStatus;
        String productionLabelBatchNo;
        String createdBy;
        LocalDateTime createdAt;
        String confirmedBy;
        LocalDateTime confirmedAt;
    }
}
