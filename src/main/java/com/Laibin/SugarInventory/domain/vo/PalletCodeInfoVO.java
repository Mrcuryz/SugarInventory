package com.Laibin.SugarInventory.domain.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class PalletCodeInfoVO {
    private Integer id;
    private String code;
    private String status;
    private String productName;
    private String productStatus;
    private LocalDate productionDate;
    private String screenMeshName;
    private Integer assayId;
    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;
}

