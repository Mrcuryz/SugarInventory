package com.Laibin.SugarInventory.mapper.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class AssayAbnormalityGroupRow {
    private String groupKey;
    private String groupLabel;
    private String productName;
    private String packagingMethod;
    private BigDecimal weightPerPiece;
    private Integer piecesPerPallet;
    private Long total;
    private Long failedCount;
    private Long noStandardCount;
    private Long multipleCandidatesCount;
    private LocalDate latestSampleDate;
}
