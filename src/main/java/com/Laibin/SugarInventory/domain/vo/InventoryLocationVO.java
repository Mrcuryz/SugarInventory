package com.Laibin.SugarInventory.domain.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class InventoryLocationVO {
    private String coordinates;
    private BigDecimal quantity;
}