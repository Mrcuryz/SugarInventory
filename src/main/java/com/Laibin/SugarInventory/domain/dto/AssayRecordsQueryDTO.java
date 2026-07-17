package com.Laibin.SugarInventory.domain.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.time.LocalDate;

@Data
public class AssayRecordsQueryDTO {
    @Valid
    @NotNull
    private ProductScope productScope;

    @Valid
    private DateRange dateRange;

    @Pattern(regexp = "ANY|PASS|FAILED|NO_STANDARD|MULTIPLE_CANDIDATES")
    private String judgeStatus = "ANY";

    @Pattern(regexp = "sampleDate|createdAt")
    private String sortBy = "sampleDate";

    @Pattern(regexp = "ASC|DESC")
    private String sortDirection = "DESC";

    @Min(1)
    private Integer page = 1;

    @Min(1)
    @Max(100)
    private Integer size = 20;

    @JsonIgnore
    private LocalDate resolvedFrom;

    @JsonIgnore
    private LocalDate resolvedTo;

    @JsonIgnore
    private String resolvedJudgeResult;

    @Data
    public static class ProductScope {
        @NotNull
        @Pattern(regexp = "SINGLE_PRODUCT|EXACT_PRODUCT_NAME_GROUP|PRODUCT_TYPE_GROUP|ALL")
        private String type;

        @Min(1)
        private Integer productId;

        private String productName;

        private String productType;
    }

    @Data
    public static class DateRange {
        @NotNull
        @Pattern(regexp = "EXACT|LAST_DAYS|RANGE")
        private String type;

        private LocalDate date;

        @Min(1)
        @Max(366)
        private Integer days;

        private LocalDate from;

        private LocalDate to;
    }
}
