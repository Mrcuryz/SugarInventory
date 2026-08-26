package com.Laibin.SugarInventory.production.domain.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class ProductionDailyReportHeaderSaveDTO {
    private Integer version;
    private LocalDate preparedDate;

    @Size(max = 100)
    private String preparedByName;
}
