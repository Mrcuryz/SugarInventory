package com.Laibin.SugarInventory.production.domain.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class ProductionBoilingBatchSaveDTO {
    private String batchNo;

    @NotNull
    private LocalDate boilingDate;

    private String teamName;
    private String sugarType;
    private Integer productId;
    private BigDecimal potCount;
    private BigDecimal bucketCount;
    private BigDecimal kgPerBucket;
    private String sourceText;
    private String remark;
}

