package com.Laibin.SugarInventory.production.domain.vo;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.List;

@Value
@Builder
public class ProductionLabelCompletionVO {
    String dataScope;
    String orderRef;
    String orderNo;
    String orderStatus;
    int labelBatchCount;
    int reservedLabelCount;
    int usedLabelCount;
    int recycledLabelCount;
    int requiredQrCount;
    int boundQrCount;
    int inboundQrCount;
    int notBoundQrCount;
    int notInboundQrCount;
    List<LabelBatch> batches;
    List<String> limitations;

    @Value
    @Builder
    public static class LabelBatch {
        String batchNo;
        String productName;
        Integer reservedCount;
        Integer usedCount;
        Integer recycledCount;
        String status;
        LocalDateTime printedAt;
        LocalDateTime closedAt;
        LocalDateTime createdAt;
    }
}
