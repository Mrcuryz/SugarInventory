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
public class PalletFlowRecordsQueryDTO {
    @Size(max = 100)
    private String code;

    @Valid
    private AssayRecordsQueryDTO.ProductScope productScope;

    @Min(1)
    private Integer warehouseId;

    @Valid
    private AssayRecordsQueryDTO.DateRange dateRange;

    @Size(max = 8)
    private List<String> eventTypes;

    @Min(1)
    private Integer page;

    @Min(1)
    @Max(100)
    private Integer size;

    @JsonIgnore
    private LocalDate resolvedFrom;

    @JsonIgnore
    private LocalDate resolvedTo;

    @JsonIgnore
    private List<String> resolvedEventTypes;
}
