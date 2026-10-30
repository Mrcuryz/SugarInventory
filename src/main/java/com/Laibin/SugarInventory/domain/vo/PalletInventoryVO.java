package com.Laibin.SugarInventory.domain.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PalletInventoryVO {
    private String warehouseName;
    private Integer quantity;
    private Boolean unit; // false -> 板, true -> 件
    private LocalDateTime inStockTime;
}

