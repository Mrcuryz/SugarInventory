package com.Laibin.SugarInventory.domain.dto;

import lombok.Data;
import java.time.LocalDate;

@Data
public class InventoryLedgerAgentQueryDTO {
    private String productName;
    private String warehouseName;
    private String screenMeshName;
    private String productStatus;
    private LocalDate entryDateStart;
    private LocalDate entryDateEnd;
    private Integer page = 1;
    private Integer size = 20;
}
