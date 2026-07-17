package com.Laibin.SugarInventory.domain.vo;

import lombok.Data;

import java.util.List;

@Data
public class QrBatchInboundCompletionVO {
    private String batchLabel;
    private String orderLabel;
    private long printedCount;
    private long inboundCount;
    private long notInboundCount;
    private String completionRateText;
    private List<String> unfinishedExamples;
    private String summaryText;
    private List<String> notes;
}
