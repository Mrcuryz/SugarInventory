package com.Laibin.SugarInventory.domain.vo;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Value
@Builder
public class AssayRecordRowVO {
    String recordRef;
    String recordLabel;
    String productLabel;
    LocalDate sampleDate;
    LocalDateTime createdAt;
    String judgeStatus;
    String judgeLabel;
    String failedMetricText;
    Integer failedMetricCount;
    String standardLabel;
    String testerLabel;
    String actionHint;
}
