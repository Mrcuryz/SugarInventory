package com.Laibin.SugarInventory.analytics.domain.vo;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;

@Value
@Builder
public class RegisteredReportOperationsOverviewVO {
    LocalDate businessDate;
    RegisteredReportMetricsVO productionOutput;
    RegisteredReportQualityMetricsVO assayQuality;
    RegisteredReportProductionFlowMetricsVO productionFlow;
    RegisteredReportCurrentInventoryMetricsVO currentInventory;
    RegisteredReportPalletTaskCycleMetricsVO todayPalletTasks;
    long currentPendingTaskCount;
}
