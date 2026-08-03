package com.Laibin.SugarInventory.analytics.domain.vo;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;

@Value
@Builder
public class RegisteredReportPalletTaskCycleDailyPointVO {
    LocalDate businessDate;
    int taskCount;
    int completedTaskCount;
    int inProgressTaskCount;
    int canceledTaskCount;
    Long averageDurationSeconds;
    Long medianDurationSeconds;
    Long p90DurationSeconds;
    Long maximumDurationSeconds;
    Long maximumWaitingSeconds;
}
