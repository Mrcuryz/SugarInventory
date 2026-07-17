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
public class ProductsWithoutRecentAssayQueryDTO {
    @Valid
    @NotNull
    private AssayRecordsQueryDTO.ProductScope productScope;

    @Valid
    @NotNull
    private InventoryDistributionQueryDTO.WarehouseScope warehouseScope;

    @Pattern(regexp = "CURRENT_INVENTORY")
    private String population = "CURRENT_INVENTORY";

    @Valid
    private AssayRecordsQueryDTO.DateRange dateRange;

    @Pattern(regexp = "product|warehouse|product_warehouse")
    private String groupBy = "product";

    @Min(1)
    @Max(100)
    private Integer limit = 50;

    @JsonIgnore
    private LocalDate resolvedFrom;

    @JsonIgnore
    private LocalDate resolvedTo;
}
