package com.Laibin.SugarInventory.analytics.domain.vo;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

@Value
@Builder
public class RegisteredReportPalletTaskPendingItemVO {
    String palletCode;
    String taskTypeLabel;
    String productName;
    LocalDateTime createdAt;
    long waitingSeconds;
    String targetWarehouseName;
}
