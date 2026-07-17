package com.Laibin.SugarInventory.domain.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PalletFlowRecordVO {
    private LocalDateTime time;
    private String eventType;
    private String eventLabel;
    private String codeLabel;
    private String productLabel;
    private String fromWarehouseLabel;
    private String toWarehouseLabel;
    private String operatorLabel;
    private Integer cycleNo;
}
