package com.Laibin.SugarInventory.production.domain.dto;

import lombok.Data;

@Data
public class ProductionEntityResolveQueryDTO {
    private String entityType;
    private String query;
    private Integer limit = 5;
}
