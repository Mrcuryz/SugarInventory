package com.Laibin.SugarInventory.domain.vo;

import lombok.Data;

import java.util.List;

@Data
public class PrintedNotInboundGroupVO {
    private String groupLabel;
    private long printedCount;
    private long inboundCount;
    private long notInboundCount;
    private String completionRateText;
    private List<String> examples;
    private List<String> riskLabels;
}
