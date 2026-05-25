package com.Laibin.SugarInventory.domain.redis;

import lombok.Data;

import java.time.LocalDate;

@Data
public class ProductionConsumptionEntry {
    private LocalDate productionDate;
    private Integer boardCount;
    private Integer pieceCount;
    private String quantityText;
}
