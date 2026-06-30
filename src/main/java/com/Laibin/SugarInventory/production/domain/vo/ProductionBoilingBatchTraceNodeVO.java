package com.Laibin.SugarInventory.production.domain.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
public class ProductionBoilingBatchTraceNodeVO {
    private String label;
    private String type;
    private String status;
    private String summary;
    private List<ProductionBoilingBatchTraceNodeVO> children = new ArrayList<>();
    private List<TraceGraphNodeVO> nodes = new ArrayList<>();
    private List<TraceGraphEdgeVO> edges = new ArrayList<>();
    private List<TraceTimelineRecordVO> timeline = new ArrayList<>();

    @Data
    public static class TraceGraphNodeVO {
        private String id;
        private String type;
        private String name;
        private String documentNo;
        private String productName;
        private String quantityText;
        private String warehouseName;
        private String status;
        private String occurredAt;
        private Map<String, Object> meta;
    }

    @Data
    public static class TraceGraphEdgeVO {
        private String id;
        private String source;
        private String target;
        private String action;
        private String label;
        private String quantityText;
        private String status;
    }

    @Data
    public static class TraceTimelineRecordVO {
        private LocalDateTime occurredAt;
        private String actionType;
        private String documentNo;
        private String productName;
        private String quantityText;
        private String warehouseName;
        private String relatedObject;
        private String status;
    }
}