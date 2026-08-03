package com.Laibin.SugarInventory.analytics.domain.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class InventoryTrustedTrendPointRowVO {
    private LocalDate businessDate;
    private Integer productId;
    private String productName;
    private long totalPieces;
    private BigDecimal totalWeightKg;
    private LocalDateTime dataAsOf;
}
