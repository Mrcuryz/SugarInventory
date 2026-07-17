package com.Laibin.SugarInventory.domain.vo;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class AutoInboundBatchesAgentVO {
    String dataScope;
    int count;
    List<Row> records;
    List<String> limitations;

    @Value @Builder
    public static class Row {
        String batchRef;
        String parseTime;
        String displayName;
        Integer taskCount;
        String status;
        String parseType;
    }
}
