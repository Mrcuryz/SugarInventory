package com.Laibin.SugarInventory.domain.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class FinishInboundExecutionControlResultVO {
    private String executionRef;
    private String executionStatus;
    private String executionStatusLabel;
    private String resultCode;
    private String errorCode;
    private boolean replayed;
    private int attemptCount;
    private int businessWrites;
    private int affectedPalletCount;
    private List<String> palletCodes;
    private LocalDateTime completedAt;
}
