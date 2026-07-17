package com.Laibin.SugarInventory.domain.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class PalletTaskAgentQueryDTO {
    private String code;
    private String taskType;
    private String bizScene;
    private String status;
    private String productName;
    private String productType;
    private String productStatus;
    private String targetWarehouseName;
    private LocalDate productionDateStart;
    private LocalDate productionDateEnd;
    private Integer page = 1;
    private Integer size = 20;
}
