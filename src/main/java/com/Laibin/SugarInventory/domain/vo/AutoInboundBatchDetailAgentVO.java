package com.Laibin.SugarInventory.domain.vo;

import com.Laibin.SugarInventory.domain.enumObject.AutoInboundRiskLevel;
import com.Laibin.SugarInventory.domain.enumObject.AutoInboundType;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;
import java.util.List;

@Value
@Builder
public class AutoInboundBatchDetailAgentVO {
    String dataScope;
    String batchRef;
    int taskCount;
    List<Task> tasks;
    List<String> globalRemarks;
    List<String> limitations;

    @Value @Builder
    public static class Task {
        AutoInboundType type;
        AutoInboundRiskLevel riskLevel;
        String riskReason;
        LocalDate entryDate;
        String side;
        Boolean hasAssay;
        String productName;
        String warehouseName;
        Integer boardQuantity;
        Integer pieceQuantity;
        Integer requiredQrCount;
        Integer availableQrCount;
        List<String> missingFields;
        List<String> warnings;
        String status;
        boolean canAutoStockIn;
    }
}
