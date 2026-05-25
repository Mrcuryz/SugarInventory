package com.Laibin.SugarInventory.production.domain.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class ProductionMaterialCandidateQueryDTO {
    private Integer productId;
    private LocalDate productionDate;
    private String palletCode;
    private Integer warehouseId;
    private Integer page = 1;
    private Integer size = 10;
}
