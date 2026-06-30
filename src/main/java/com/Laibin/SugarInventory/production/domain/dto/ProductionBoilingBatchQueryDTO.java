package com.Laibin.SugarInventory.production.domain.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class ProductionBoilingBatchQueryDTO {
    private String batchNo;
    private String status;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer page = 1;
    private Integer size = 10;
}
