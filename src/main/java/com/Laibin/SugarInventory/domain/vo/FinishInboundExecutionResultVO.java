package com.Laibin.SugarInventory.domain.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class FinishInboundExecutionResultVO {
    private String statusLabel;
    private int affectedPalletCount;
    private List<String> palletCodes;
    private boolean replayed;
    private LocalDateTime completedAt;
}
