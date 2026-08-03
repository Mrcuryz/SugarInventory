package com.Laibin.SugarInventory.analytics.domain.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class InventoryReplayBalanceRowVO {
    private Integer productId;
    private String productName;
    private long totalPieces;
    private BigDecimal totalWeightKg;
}
