package com.Laibin.SugarInventory.inventoryhistory.domain.vo;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Value
@Builder
public class InventoryHistoryOperationsStatusVO {
    LocalDate businessDate;
    boolean snapshotAvailable;
    String snapshotStatus;
    String snapshotRunId;
    LocalDateTime dataAsOf;
    String reconciliationStatus;
    String reconciliationSummary;
    String latestExecutionStatus;
    String latestExecutionMode;
    String latestTriggerSource;
    String latestFailureSummary;
    LocalDateTime latestExecutionAt;
    String trendGateStatus;
    Integer consecutivePassedDays;
    Integer requiredPassedDays;
    String trendGateSummary;
}
