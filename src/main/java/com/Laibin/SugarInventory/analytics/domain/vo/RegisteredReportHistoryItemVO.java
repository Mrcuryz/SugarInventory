package com.Laibin.SugarInventory.analytics.domain.vo;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

@Value
@Builder
public class RegisteredReportHistoryItemVO {
    String reportRunId;
    String reportDefinitionId;
    int reportVersion;
    String reportName;
    String dateRangeLabel;
    String scopeLabel;
    LocalDateTime dataAsOf;
    LocalDateTime generatedAt;
    LocalDateTime expiresAt;
    boolean comparisonIncluded;
    boolean partialData;
}
