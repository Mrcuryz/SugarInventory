package com.Laibin.SugarInventory.domain.vo;

import lombok.Data;

import java.util.List;

@Data
public class PalletAnomalyGroupVO {
    private String anomalyType;
    private String groupLabel;
    private long count;
    private List<String> examples;
    private List<String> riskLabels;
}
