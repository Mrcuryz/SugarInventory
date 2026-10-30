package com.Laibin.SugarInventory.production.domain.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.time.LocalDate;

@Data
public class ProductionDailyReportAgentQueryDTO {
    @NotNull private LocalDate startDate;
    @NotNull private LocalDate endDate;
    @Size(max = 30) private String departmentName;
    @Size(max = 100) private String itemQuery;
    @Min(1) @Max(10000) private Integer page = 1;
    @Min(1) @Max(50) private Integer size = 20;
}
