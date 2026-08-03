package com.Laibin.SugarInventory.analytics.domain.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class InventoryReplayAnchorCheckRowVO {
    private Integer productId;
    private String productName;
    private long expectedPieces;
    private long currentPieces;
    private long pieceDifference;
    private BigDecimal expectedWeightKg;
    private BigDecimal currentWeightKg;
    private BigDecimal weightDifferenceKg;
}
