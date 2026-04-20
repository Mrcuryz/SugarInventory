package com.Laibin.SugarInventory.domain.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class PalletAssayVO {
    private Integer id;
    private String productName;
    private LocalDate sampleDate;
    private BigDecimal colorValue;
    private BigDecimal reducingSugar;
    private BigDecimal dryWeight;
    private BigDecimal conductivityAsh;
    private BigDecimal sucrose;
    private BigDecimal insolubleImpurity;
    private BigDecimal phValue;
    private String testerName;
    private String isQualified;
    private String qualifiedStandards;
    private LocalDateTime createdAt;
    private String resolveSource;
    private String resolveStatus;
    private String resolveMessage;
    private Boolean autoBound;
    private Boolean multipleCandidates;
    private Integer candidateCount;
}

