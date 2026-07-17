package com.Laibin.SugarInventory.domain.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AssayStandardCoverageQueryDTO {
    @Valid
    @NotNull
    private AssayRecordsQueryDTO.ProductScope productScope;

    @Valid
    private AssayRecordsQueryDTO.DateRange dateRange;

    @Pattern(regexp = "PRODUCT_WITHOUT_STANDARD|ASSAY_WITHOUT_STANDARD|UNUSED_STANDARD")
    private String coverageType = "PRODUCT_WITHOUT_STANDARD";

    @Min(1)
    @Max(100)
    private Integer limit = 50;

    @JsonIgnore
    private LocalDateTime resolvedAt;
}
