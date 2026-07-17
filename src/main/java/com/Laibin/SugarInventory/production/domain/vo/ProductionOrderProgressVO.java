package com.Laibin.SugarInventory.production.domain.vo;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Value
@Builder
public class ProductionOrderProgressVO {
    String dataScope;
    String orderRef;
    String orderNo;
    String orderType;
    String status;
    LocalDate productionDate;
    String teamName;
    String plannedMaterialText;
    String plannedOutputText;
    int materialRecordCount;
    int outputRecordCount;
    int requiredQrCount;
    int boundQrCount;
    int inboundQrCount;
    int labelBatchCount;
    int reservedLabelCount;
    int usedLabelCount;
    int recycledLabelCount;
    LocalDateTime updatedAt;
    LocalDateTime completedAt;
    List<String> limitations;
}
