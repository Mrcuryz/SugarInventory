package com.Laibin.SugarInventory.domain.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@EqualsAndHashCode(callSuper = true)
@Data
public class WarehouseVO extends BaseVO {
    private Integer id;
    private String warehouseId;
    private String warehouseName;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer maxCapacity;
    private Integer curCapacity;
    private Integer maxRows;
}
