package com.Laibin.SugarInventory.analytics.domain;

import lombok.Data;

@Data
public class ReportExecutionWindowAggregate {
    private Long executionCount;
    private Long successCount;
    private Long rejectionCount;
    private Long failureCount;
    private Long partialDataCount;
    private Long maxDurationMs;
}
