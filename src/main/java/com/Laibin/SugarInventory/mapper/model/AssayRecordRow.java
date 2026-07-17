package com.Laibin.SugarInventory.mapper.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class AssayRecordRow {
    private Integer id;
    private Integer productId;
    private String productName;
    private String productType;
    private String packagingMethod;
    private BigDecimal weightPerPiece;
    private Integer piecesPerPallet;
    private LocalDate sampleDate;
    private LocalDateTime createdAt;
    private String judgeResult;
    private String isQualified;
    private String judgeMessage;
    private Integer failedMetricCount;
    private String failedMetricsJson;
    private String appliedStandardName;
    private Integer appliedStandardVersion;
    private String testerName;
}
