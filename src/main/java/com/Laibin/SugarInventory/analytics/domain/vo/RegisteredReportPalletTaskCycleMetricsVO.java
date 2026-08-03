package com.Laibin.SugarInventory.analytics.domain.vo;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class RegisteredReportPalletTaskCycleMetricsVO {
    int cohortTaskCount;
    int completedTaskCount;
    int inProgressTaskCount;
    int canceledTaskCount;
    int invalidTaskCount;
    Long averageDurationSeconds;
    Long medianDurationSeconds;
    Long p90DurationSeconds;
    Long maximumDurationSeconds;
    Long medianWaitingSeconds;
    Long p90WaitingSeconds;
    Long maximumWaitingSeconds;
}
