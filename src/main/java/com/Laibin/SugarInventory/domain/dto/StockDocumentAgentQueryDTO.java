package com.Laibin.SugarInventory.domain.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class StockDocumentAgentQueryDTO {
    private String documentType;
    private String productName;
    private String warehouseName;
    private String operatorName;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer page = 1;
    private Integer size = 20;
}
