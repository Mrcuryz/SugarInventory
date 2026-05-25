package com.Laibin.SugarInventory.production.domain.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class ProductionOrderQueryDTO {
    private String orderNo;
    private String orderType;
    private String status;
    private Integer createdBy;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer page = 1;
    private Integer size = 10;
}
