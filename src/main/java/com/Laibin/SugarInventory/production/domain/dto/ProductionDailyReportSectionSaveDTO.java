package com.Laibin.SugarInventory.production.domain.dto;

import jakarta.validation.Valid;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ProductionDailyReportSectionSaveDTO {
    private Integer version;

    @Valid
    private List<ProductionDailyMetricValueDTO> metricValues = new ArrayList<>();

    @Valid
    private List<ProductionDailyProductLineDTO> productLines = new ArrayList<>();
}
