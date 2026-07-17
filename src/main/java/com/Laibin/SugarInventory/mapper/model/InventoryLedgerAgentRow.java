package com.Laibin.SugarInventory.mapper.model;

import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class InventoryLedgerAgentRow {
    private String warehouseName;
    private String productName;
    private String productType;
    private String productStatus;
    private String screenMeshName;
    private String palletCode;
    private String side;
    private Integer rowNumber;
    private Integer layer;
    private Integer palletQuantity;
    private Integer pieces;
    private LocalDate entryDate;
    private LocalDate productionDate;
    private LocalDateTime recordedAt;
}
