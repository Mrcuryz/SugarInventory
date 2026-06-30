package com.Laibin.SugarInventory.production.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class ProductionOrderCreateDTO {
    @NotBlank
    private String orderType;

    @NotNull
    private LocalDate productionDate;

    private Object plannedMaterialJson;
    private Object plannedOutputJson;
    private List<ProductionBoilingBatchUsageDTO> boilingSources;
    private String teamName;
    private String remark;
}
