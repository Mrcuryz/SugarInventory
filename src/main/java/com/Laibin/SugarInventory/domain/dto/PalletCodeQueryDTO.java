package com.Laibin.SugarInventory.domain.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class PalletCodeQueryDTO {
    private String code;
    private String status;
    private String productName;
    private String productType;
    private String productStatus;
    private LocalDate productionDateStart;
    private LocalDate productionDateEnd;
    private Boolean inventoryOnly;
    private Long pageNum;
    private Long pageSize;
}

