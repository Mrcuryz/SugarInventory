package com.Laibin.SugarInventory.domain.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class InventoryQualityQueryDTO {
    @Valid
    @NotNull
    private InventoryDistributionQueryDTO.ProductScope productScope;

    @Valid
    @NotNull
    private InventoryDistributionQueryDTO.WarehouseScope warehouseScope;

    @NotNull
    @Pattern(regexp = "JUDGE_STATUS|STANDARD|METRIC")
    private String mode;

    @Pattern(regexp = "PASS|FAIL|NO_STANDARD|MULTIPLE_CANDIDATES|MISSING_ASSAY")
    private String judgeStatus;

    @Pattern(regexp = "[A-Za-z0-9_-]{1,64}")
    private String standardCode;

    @Min(1)
    private Integer standardVersion;

    @Valid
    private MetricCondition metricCondition;

    @Min(1)
    @Max(100)
    private Integer limit = 50;

    @JsonIgnore
    private Integer resolvedStandardId;

    @JsonIgnore
    private String resolvedStandardName;

    @JsonIgnore
    private String resolvedStandardProductType;

    @JsonIgnore
    private Integer resolvedStandardVersion;

    @Data
    public static class MetricCondition {
        @NotNull
        @Pattern(regexp = "color_value|reducing_sugar|dry_weight_loss|conductivity_ash|sucrose|insoluble_impurity|ph")
        private String metricCode;

        @NotNull
        @Pattern(regexp = "GT|GTE|LT|LTE|EQ|BETWEEN")
        private String operator;

        private java.math.BigDecimal value;
        private java.math.BigDecimal minValue;
        private java.math.BigDecimal maxValue;
    }
}
