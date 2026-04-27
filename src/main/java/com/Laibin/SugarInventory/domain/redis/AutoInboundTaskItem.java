package com.Laibin.SugarInventory.domain.redis;

import lombok.Data;

@Data
public class AutoInboundTaskItem {
    private Integer seq;
    private Integer quantity;
    private String unit;
    private String displayQuantity;

    private String code;
    private Integer palletTaskId;
    private String warehouseName;
    private String side;
    private Integer rowNumber;
    private Integer layer;
    private String status;
    private String message;
}
