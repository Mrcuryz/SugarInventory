package com.Laibin.SugarInventory.domain.vo;

import lombok.Data;

import java.util.List;

@Data
public class PrintedNotInboundCodesVO {
    private String scopeLabel;
    private String dateRangeLabel;
    private String groupBy;
    private long printedCount;
    private long inboundCount;
    private long notInboundCount;
    private String completionRateText;
    private String summaryText;
    private List<PrintedNotInboundGroupVO> groups;
    private List<String> notes;
}
