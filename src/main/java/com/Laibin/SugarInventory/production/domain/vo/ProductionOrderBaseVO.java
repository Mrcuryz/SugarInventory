package com.Laibin.SugarInventory.production.domain.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class ProductionOrderBaseVO {
    private Long id;
    private String orderNo;
    private String orderType;
    private String status;
    private LocalDate productionDate;
    private Object plannedMaterialJson;
    private Object plannedOutputJson;
    private String plannedMaterialText;
    private String plannedOutputText;
    private String teamName;
    private String remark;
    private Integer createdBy;
    private String createdByName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime completedAt;
}
