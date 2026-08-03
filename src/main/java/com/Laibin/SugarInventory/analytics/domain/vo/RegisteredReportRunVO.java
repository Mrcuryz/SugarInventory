package com.Laibin.SugarInventory.analytics.domain.vo;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Value
@Builder(toBuilder = true)
public class RegisteredReportRunVO {
    String dataScope;
    String reportRunId;
    String reportDefinitionId;
    int reportVersion;
    String reportName;
    String metricDefinitionVersion;
    LocalDate startDate;
    LocalDate endDate;
    String dateRangeLabel;
    LocalDateTime dataAsOf;
    LocalDateTime latestRecordAt;
    Map<String, String> filtersApplied;
    RegisteredReportMetricsVO metrics;
    List<RegisteredReportDailyPointVO> dailySeries;
    List<RegisteredReportProductBreakdownVO> productBreakdowns;
    RegisteredReportQualityMetricsVO qualityMetrics;
    String seriesGranularity;
    List<RegisteredReportQualityPeriodPointVO> qualitySeries;
    List<RegisteredReportQualityProductBreakdownVO> qualityProductBreakdowns;
    List<RegisteredReportStandardBreakdownVO> standardBreakdowns;
    RegisteredReportMetricSummaryVO metricTrendSummary;
    List<RegisteredReportMetricPeriodPointVO> metricSeries;
    List<RegisteredReportMetricProductBreakdownVO> metricProductBreakdowns;
    List<RegisteredReportMetricStandardBreakdownVO> metricStandardBreakdowns;
    RegisteredReportProductionFlowMetricsVO productionFlowMetrics;
    List<RegisteredReportProductionFlowDailyPointVO> productionFlowDailySeries;
    List<RegisteredReportProductionFlowOrderBreakdownVO> productionFlowOrderBreakdowns;
    RegisteredReportPalletTaskCycleMetricsVO palletTaskCycleMetrics;
    List<RegisteredReportPalletTaskCycleDailyPointVO> palletTaskCycleDailySeries;
    List<RegisteredReportPalletTaskCycleTypeBreakdownVO> palletTaskCycleTypeBreakdowns;
    List<RegisteredReportPalletTaskPendingItemVO> palletTaskPendingItems;
    RegisteredReportInventoryTrendMetricsVO inventoryTrendMetrics;
    List<RegisteredReportInventoryTrendDailyPointVO> inventoryTrendDailySeries;
    List<RegisteredReportInventoryTrendProductBreakdownVO> inventoryTrendProductBreakdowns;
    RegisteredReportOperationsOverviewVO operationsOverview;
    RegisteredReportComparisonVO comparison;
    RegisteredReportDataQualityVO dataQuality;
    List<String> limitations;
}
