package com.Laibin.SugarInventory.domain.vo;

import lombok.Builder;
import lombok.Value;
import java.time.LocalDateTime;
import java.util.List;

@Value @Builder
public class WarehouseRecentOperationsAgentVO {
    String dataScope;
    int count;
    List<Row> records;
    List<String> limitations;

    @Value @Builder
    public static class Row {
        LocalDateTime operationTime;
        String eventType;
        String operationName;
        String operatorName;
        String palletCode;
        String productName;
        String fromWarehouseName;
        String toWarehouseName;
        String remark;
    }
}
