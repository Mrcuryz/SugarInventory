package com.Laibin.SugarInventory.mapper.model;

import lombok.Data;

@Data
public class PrintedNotInboundGroupRow {
    private String groupLabel;
    private Long printedCount;
    private Long inboundCount;
    private String notInboundExamples;
}
