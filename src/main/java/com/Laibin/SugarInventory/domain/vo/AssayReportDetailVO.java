package com.Laibin.SugarInventory.domain.vo;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Value
@Builder
public class AssayReportDetailVO {
    String reportRef;
    String reportLabel;
    String productLabel;
    LocalDate sampleDate;
    LocalDateTime createdAt;
    String judgeStatus;
    String judgeLabel;
    String judgeMessage;
    String standardLabel;
    List<AssayReportMetricVO> metrics;
    List<String> matchedStandards;
    List<String> riskLabels;
    List<String> notes;
    String summaryText;
}
