package com.Laibin.SugarInventory.domain.vo;

import lombok.Data;

import java.util.List;

@Data
public class PalletFlowRecordsVO {
    private String scopeLabel;
    private String dateRangeLabel;
    private long total;
    private String summaryText;
    private List<PalletFlowRecordVO> records;
    private List<String> notes;
}
