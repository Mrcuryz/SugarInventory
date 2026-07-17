package com.Laibin.SugarInventory.mapper.model;

import lombok.Data;

@Data
public class QrBatchInboundCompletionRow {
    private String batchNo;
    private String orderNo;
    private Long printedCount;
    private Long inboundCount;
    private String unfinishedExamples;
}
