package com.Laibin.SugarInventory.domain.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PalletPrintInfoVO {
    private String batchLabel;
    private String orderLabel;
    private String printStatusLabel;
    private String labelStatusLabel;
    private LocalDateTime printedAt;
    private LocalDateTime usedAt;
    private LocalDateTime recycledAt;
}
