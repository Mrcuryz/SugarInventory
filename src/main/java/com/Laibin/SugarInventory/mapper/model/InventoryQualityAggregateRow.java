package com.Laibin.SugarInventory.mapper.model;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class InventoryQualityAggregateRow {
    private Long totalGroups;
    private Long totalEquivalentPieces;
    private BigDecimal totalWeight;
}
