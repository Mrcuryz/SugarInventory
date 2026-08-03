package com.Laibin.SugarInventory.analytics.domain.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class RegisteredReportRunQueryDTO {
    private String reportDefinitionId;
    private Integer reportVersion;
    private LocalDate startDate;
    private LocalDate endDate;
    private String productQuery;
    private String metricKey;
    private String taskType;
    private String comparisonMode;
    private LocalDate comparisonStartDate;
    private LocalDate comparisonEndDate;
}
