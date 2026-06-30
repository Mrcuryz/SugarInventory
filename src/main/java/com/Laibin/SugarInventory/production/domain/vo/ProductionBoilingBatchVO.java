package com.Laibin.SugarInventory.production.domain.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class ProductionBoilingBatchVO {
    private Long id;
    private String batchNo;
    private LocalDate boilingDate;
    private String teamName;
    private String sugarType;
    private Integer productId;
    private String productName;
    private BigDecimal potCount;
    private BigDecimal bucketCount;
    private BigDecimal kgPerBucket;
    private BigDecimal totalWeightKg;
    private BigDecimal reservedBucketCount;
    private BigDecimal reservedWeightKg;
    private BigDecimal consumedBucketCount;
    private BigDecimal consumedWeightKg;
    private BigDecimal remainingBucketCount;
    private BigDecimal remainingWeightKg;
    private String status;
    private String sourceText;
    private String remark;
    private String createdByName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<ProductionBoilingBatchUsageVO> usages;
}
