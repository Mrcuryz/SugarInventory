package com.Laibin.SugarInventory.domain.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class FinishInboundExecutionConfirmationVO {
    private String confirmationRef;
    private String confirmationStatus;
    private String confirmationStatusLabel;
    private LocalDateTime expiresAt;
    private String executionToken;
    private String idempotencyKey;
    private boolean replayed;
}
