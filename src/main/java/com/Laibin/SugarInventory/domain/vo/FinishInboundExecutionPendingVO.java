package com.Laibin.SugarInventory.domain.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class FinishInboundExecutionPendingVO {
    private String previewRef;
    private String statusLabel;
    private LocalDateTime expiresAt;
    private List<Item> items;

    @Data
    @Builder
    public static class Item {
        private String palletCode;
        private String productName;
        private String warehouseName;
        private LocalDate entryDate;
        private String side;
        private Integer quantity;
        private String unitLabel;
        private String remark;
    }
}
