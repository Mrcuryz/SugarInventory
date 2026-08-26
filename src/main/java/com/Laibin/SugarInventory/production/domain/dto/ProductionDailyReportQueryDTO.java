package com.Laibin.SugarInventory.production.domain.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class ProductionDailyReportQueryDTO {
    private Integer page = 1;
    private Integer size = 10;
    private LocalDate startDate;
    private LocalDate endDate;
    private String status;
}
