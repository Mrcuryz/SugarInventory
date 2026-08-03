package com.Laibin.SugarInventory.analytics.domain.vo;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Value
@Builder
public class RegisteredReportComparisonVO {
    String comparisonMode;
    String comparisonLabel;
    LocalDate currentStartDate;
    LocalDate currentEndDate;
    String currentDateRangeLabel;
    LocalDate comparisonStartDate;
    LocalDate comparisonEndDate;
    String comparisonDateRangeLabel;
    int currentPeriodDays;
    int comparisonPeriodDays;
    boolean differentPeriodLengths;
    LocalDateTime comparisonDataAsOf;
    List<RegisteredReportMetricComparisonVO> metrics;
    List<String> notes;
}
