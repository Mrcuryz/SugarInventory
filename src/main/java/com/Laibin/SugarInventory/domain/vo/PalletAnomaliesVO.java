package com.Laibin.SugarInventory.domain.vo;

import lombok.Data;

import java.util.List;

@Data
public class PalletAnomaliesVO {
    private String scopeLabel;
    private String dateRangeLabel;
    private long total;
    private String summaryText;
    private List<PalletAnomalyGroupVO> groups;
    private List<String> notes;
}
