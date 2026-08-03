package com.Laibin.SugarInventory.analytics.domain.vo;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class RegisteredReportPalletTaskCycleTypeBreakdownVO {
    String taskTypeLabel;
    int taskCount;
    int completedTaskCount;
    int inProgressTaskCount;
    int canceledTaskCount;
    int invalidTaskCount;
    Long averageDurationSeconds;
    Long medianDurationSeconds;
    Long p90DurationSeconds;
    Long maximumDurationSeconds;
    Long maximumWaitingSeconds;
}
