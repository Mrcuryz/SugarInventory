package com.Laibin.SugarInventory.domain.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class AssayAbnormalitiesQueryDTO {
    @Valid
    @NotNull
    private AssayRecordsQueryDTO.ProductScope productScope;

    @Valid
    private AssayRecordsQueryDTO.DateRange dateRange;

    private List<@Pattern(regexp = "FAILED|NO_STANDARD|MULTIPLE_CANDIDATES") String> abnormalTypes;

    @Pattern(regexp = "product|date|abnormal_type|metric")
    private String groupBy = "product";

    @Min(1)
    @Max(100)
    private Integer limit = 50;

    @JsonIgnore
    private LocalDate resolvedFrom;

    @JsonIgnore
    private LocalDate resolvedTo;

    @JsonIgnore
    private List<String> resolvedJudgeResults;
}
