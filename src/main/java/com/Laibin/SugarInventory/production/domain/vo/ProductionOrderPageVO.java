package com.Laibin.SugarInventory.production.domain.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class ProductionOrderPageVO {
    private Long id;
    private String orderNo;
    private String orderType;
    private String status;
    private LocalDate productionDate;
    private String plannedMaterialText;
    private String plannedOutputText;
    private String actualMaterialText;
    private String outputText;
    private Integer actualMaterialCount;
    private Integer outputCount;
    private Integer boundQrCount;
    private String inboundProgress;
    private Integer inboundQrCount;
    private Integer requiredQrCount;
    private String createdByName;
    private LocalDateTime createdAt;
    private String teamName;
    private String remark;
}
