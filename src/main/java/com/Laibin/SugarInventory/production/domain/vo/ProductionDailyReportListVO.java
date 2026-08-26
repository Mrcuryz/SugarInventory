package com.Laibin.SugarInventory.production.domain.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class ProductionDailyReportListVO {
    private Long id;
    private LocalDate reportDate;
    private LocalDate preparedDate;
    private String preparedByName;
    private String status;
    private String updatedByName;
    private LocalDateTime updatedAt;
}
