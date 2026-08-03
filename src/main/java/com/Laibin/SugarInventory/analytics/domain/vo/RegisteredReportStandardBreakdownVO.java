package com.Laibin.SugarInventory.analytics.domain.vo;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

@Value
@Builder
public class RegisteredReportStandardBreakdownVO {
    String standardName;
    Integer standardVersion;
    String standardLabel;
    int assayRecordCount;
    int judgedRecordCount;
    int passCount;
    int failCount;
    BigDecimal passRatePercent;
}
