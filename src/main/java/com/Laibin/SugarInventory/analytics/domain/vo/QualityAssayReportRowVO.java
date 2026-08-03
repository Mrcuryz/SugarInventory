package com.Laibin.SugarInventory.analytics.domain.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class QualityAssayReportRowVO {
    private Integer assayId;
    private Integer productId;
    private String productName;
    private LocalDate sampleDate;
    private LocalDateTime createdAt;
    private String judgeResult;
    private String appliedStandardName;
    private Integer appliedStandardVersion;
    private String standardSnapshotJson;
    private BigDecimal colorValue;
    private BigDecimal reducingSugar;
    private BigDecimal dryWeight;
    private BigDecimal conductivityAsh;
    private BigDecimal sucrose;
    private BigDecimal insolubleImpurity;
    private BigDecimal phValue;
}
