package com.Laibin.SugarInventory.production.domain.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class ProductionBoilingBatchListQueryDTO {
    private String productQuery;
    private LocalDate startDate;
    private LocalDate endDate;
    private String status;
    private Integer limit = 10;
}
