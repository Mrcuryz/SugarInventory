package com.Laibin.SugarInventory.production.domain.vo;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
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
    List<BoilingSource> boilingSources;
    List<OutputProgress> outputs;
    LocalDateTime updatedAt;
    LocalDateTime completedAt;
    List<String> limitations;

    @Value
    @Builder
    public static class BoilingSource {
        String batchNo;
        String usageUnit;
        BigDecimal usageQuantity;
        BigDecimal bucketQuantity;
        BigDecimal weightKg;
        String status;
    }

    @Value
    @Builder
    public static class OutputProgress {
        String productName;
        String productStatus;
        Integer boardCount;
        Integer pieceCount;
        Integer totalPieces;
        BigDecimal totalWeight;
        Integer requiredQrCount;
        Integer boundQrCount;
        Integer inboundQrCount;
        String status;
        List<InboundDestination> inboundDestinations;
    }

    @Value
    @Builder
    public static class InboundDestination {
        String warehouseName;
        int inboundCodeCount;
        List<String> palletCodes;
    }
}
