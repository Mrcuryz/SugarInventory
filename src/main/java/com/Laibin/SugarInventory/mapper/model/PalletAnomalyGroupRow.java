package com.Laibin.SugarInventory.mapper.model;

import lombok.Data;

@Data
public class PalletAnomalyGroupRow {
    private String anomalyType;
    private Long anomalyCount;
    private String examples;
}
