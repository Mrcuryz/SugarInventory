package com.Laibin.SugarInventory.mapper.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PalletPrintInfoRow {
    private String batchNo;
    private String orderNo;
    private String batchStatus;
    private String labelStatus;
    private LocalDateTime printedAt;
    private LocalDateTime usedAt;
    private LocalDateTime recycledAt;
}
