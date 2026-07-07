package com.Laibin.SugarInventory.domain.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
public class InventoryDistributionQueryDTO {
    @Valid
    @NotNull
    private ProductScope productScope;

    @Valid
    @NotNull
    private WarehouseScope warehouseScope;

    @Valid
    private StatusFilter statusFilter = new StatusFilter();

    @NotNull
    @Pattern(regexp = "warehouse|product|warehouse_product")
    private String groupBy;

    @Min(1)
    @Max(100)
    private Integer limit = 20;

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
    public static class WarehouseScope {
        @NotNull
        @Pattern(regexp = "ALL|SINGLE_WAREHOUSE")
        private String type;

        @Min(1)
        private Integer warehouseId;
    }

    @Data
    public static class StatusFilter {
        private List<String> productStatuses = new ArrayList<>();
        private List<String> warehouseStatuses = new ArrayList<>();
        private List<String> palletStatuses = new ArrayList<>();

        @Pattern(regexp = "HAS_ASSAY|MISSING_ASSAY|PASS|FAIL|NO_STANDARD|MULTIPLE_CANDIDATES")
        private String assayStatus;

        private LocalDate entryDateFrom;
        private LocalDate entryDateTo;
    }
}
