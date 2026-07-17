package com.Laibin.SugarInventory.mapper.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PalletLifecycleEventRow {
    private String code;
    private String operationType;
    private String operationName;
    private LocalDateTime operationTime;
    private String productName;
    private String fromWarehouseName;
    private String toWarehouseName;
    private String operatorName;
    private Integer cycleNo;
}
