package com.Laibin.SugarInventory.production.domain.vo;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Value
@Builder
public class ProductionBoilingBatchTraceVO {
    String dataScope;
    String batchRef;
    String batchNo;
    LocalDate boilingDate;
    String sugarType;
    String productName;
    String status;
    BigDecimal totalWeightKg;
    BigDecimal reservedWeightKg;
    BigDecimal consumedWeightKg;
    BigDecimal remainingWeightKg;
    int usageCount;
    int nodeCount;
    int edgeCount;
    List<Usage> usages;
    List<Node> nodes;
    List<Edge> edges;
    List<TimelineRecord> timeline;
    List<String> limitations;

    @Value
    @Builder
    public static class Usage {
        String orderNo;
        String orderType;
        String orderStatus;
        String usageUnit;
        BigDecimal usageQuantity;
        BigDecimal bucketQuantity;
        BigDecimal weightKg;
        String status;
        LocalDateTime createdAt;
    }

    @Value
    @Builder
    public static class Node {
        String nodeRef;
        String type;
        String name;
        String documentNo;
        String productName;
        String quantityText;
        String warehouseName;
        String status;
        String occurredAt;
    }

    @Value
    @Builder
    public static class Edge {
        String sourceNodeRef;
        String targetNodeRef;
        String action;
        String label;
        String quantityText;
        String status;
    }

    @Value
    @Builder
    public static class TimelineRecord {
        LocalDateTime occurredAt;
        String actionType;
        String documentNo;
        String productName;
        String quantityText;
        String warehouseName;
        String relatedObject;
        String status;
    }
}
