package com.Laibin.SugarInventory.domain.vo;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;

@Value
@Builder
public class InventoryQualityRecordVO {
    String productLabel;
    LocalDate productionDate;
    String warehouseLabel;
    String stockText;
    String totalWeightText;
    long palletCount;
    String judgeStatus;
    String judgeLabel;
    String standardLabel;
    String failedMetricText;
    String metricLabel;
    String metricValueText;
    String reportRef;
}
