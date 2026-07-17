package com.Laibin.SugarInventory.production.domain.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class ProductionInProcessMaterialsAgentQueryDTO {
    private String productName;
    private String productType;
    private LocalDate productionDateStart;
    private LocalDate productionDateEnd;
    private Integer page = 1;
    private Integer size = 20;
}
