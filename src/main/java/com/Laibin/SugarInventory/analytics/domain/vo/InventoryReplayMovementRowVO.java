package com.Laibin.SugarInventory.analytics.domain.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class InventoryReplayMovementRowVO {
    private LocalDate businessDate;
    private Integer productId;
    private String productName;
    private long inboundPieces;
    private BigDecimal inboundWeightKg;
    private long outboundPieces;
    private BigDecimal outboundWeightKg;
    private int movementRecordCount;
    private LocalDateTime latestRecordedAt;
}
