package com.Laibin.SugarInventory.production.domain.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ProductionDailyReportImportSectionDTO {
    @NotBlank
    @Size(max = 60)
    private String departmentCode;

    private Integer version;

    @Valid
    private List<ProductionDailyMetricValueDTO> metricValues = new ArrayList<>();

    @Valid
    private List<ProductionDailyProductLineDTO> productLines = new ArrayList<>();
}
