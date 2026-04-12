package com.Laibin.SugarInventory.domain.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PalletFlowCyclePageVO {
    private Integer cycleNo;
    private Integer productId;
    private String productName;
    private String productStatus;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long flowCount;
    private Boolean isCurrentCycle;
    private Boolean isEnded;
}
