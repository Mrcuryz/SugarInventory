package com.Laibin.SugarInventory.analytics.domain.vo;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

@Value
@Builder
public class RegisteredReportOperationsStatusVO {
    LocalDateTime windowStartedAt;
    LocalDateTime windowEndedAt;
    long executionCount;
    long successCount;
    long rejectionCount;
    long failureCount;
    long partialDataCount;
    long durationP50Ms;
    long durationP95Ms;
    long maxDurationMs;
    long exportCount;
    long activeSnapshotCount;
    long expiredSnapshotCount;
    String latestExecutionStatus;
    String latestExecutionReportDefinitionId;
    LocalDateTime latestExecutionAt;
    String latestRejectionSummary;
    String latestFailureSummary;
    String latestCleanupStatus;
    Integer latestCleanupSelectedCount;
    Integer latestCleanupDeletedCount;
    LocalDateTime latestCleanupAt;
    String inventoryTrendGateStatus;
    Integer inventoryTrendConsecutivePassedDays;
    Integer inventoryTrendRequiredPassedDays;
    String summary;
}
