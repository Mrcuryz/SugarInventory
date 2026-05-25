package com.Laibin.SugarInventory.production.domain.vo;

import lombok.Data;

import java.time.LocalDate;

@Data
public class ProductionOrderOptionVO {
    private Long id;
    private String orderNo;
    private String orderType;
    private String status;
    private LocalDate productionDate;
}
