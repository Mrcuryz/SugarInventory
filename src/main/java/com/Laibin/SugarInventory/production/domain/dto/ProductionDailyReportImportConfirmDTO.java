package com.Laibin.SugarInventory.production.domain.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
public class ProductionDailyReportImportConfirmDTO {
    @NotNull
    private LocalDate reportDate;

    private Integer version;
    private LocalDate preparedDate;

    @Size(max = 100)
    private String preparedByName;

    @NotEmpty
    @Valid
    private List<ProductionDailyReportImportSectionDTO> sections = new ArrayList<>();
}
