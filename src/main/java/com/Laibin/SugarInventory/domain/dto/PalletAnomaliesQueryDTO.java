package com.Laibin.SugarInventory.domain.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class PalletAnomaliesQueryDTO {
    @Valid
    private AssayRecordsQueryDTO.ProductScope productScope;

    @Min(1)
    private Integer warehouseId;

    @Valid
    private AssayRecordsQueryDTO.DateRange dateRange;

    @Size(max = 5)
    private List<String> anomalyTypes;

    @Min(1)
    @Max(100)
    private Integer limit;

    @JsonIgnore
    private LocalDate resolvedFrom;

    @JsonIgnore
    private LocalDate resolvedTo;
}
