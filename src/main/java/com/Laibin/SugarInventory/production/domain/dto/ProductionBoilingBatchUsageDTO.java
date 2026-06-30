package com.Laibin.SugarInventory.production.domain.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ProductionBoilingBatchUsageDTO {
    @NotNull
    private Long batchId;

    private String usageUnit = "BUCKET";

    @NotNull
    private BigDecimal usageQuantity;

    private String remark;
}
