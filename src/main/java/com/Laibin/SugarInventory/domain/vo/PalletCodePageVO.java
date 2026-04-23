package com.Laibin.SugarInventory.domain.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class PalletCodePageVO {
    private Integer id;
    private String code;
    private String status;
    private Integer productId;
    private String productName;
    private String productType;
    private String productStatus;
    private LocalDate productionDate;
    private String screenMeshName;
    private Integer assayId;
    private LocalDateTime createdAt;
    private String createdByName;
    private LocalDateTime updatedAt;
    private String updatedByName;
}

