package com.Laibin.SugarInventory.domain.vo;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data @Builder
public class InventoryLedgerAgentVO {
    private String dataScope;
    private long total;
    private int page;
    private int size;
    private LocalDateTime inventoryAsOf;
    private List<Row> records;
    private List<String> limitations;

    @Data @Builder
    public static class Row {
        private String warehouseName;
        private String productName;
        private String productType;
        private String productStatus;
        private String screenMeshName;
        private String palletCode;
        private String location;
        private Integer palletQuantity;
        private Integer pieces;
        private LocalDate entryDate;
        private LocalDate productionDate;
        private LocalDateTime recordedAt;
    }
}
