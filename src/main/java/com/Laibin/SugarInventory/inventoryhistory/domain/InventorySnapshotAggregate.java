package com.Laibin.SugarInventory.inventoryhistory.domain;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class InventorySnapshotAggregate {
    private Integer productId;
    private String productName;
    private String productStatus;
    private Integer warehouseId;
    private String warehouseName;
    private LocalDate productionDate;
    private Integer boardCount;
    private Integer loosePieceCount;
    private Integer totalPieces;
    private BigDecimal totalWeightKg;
    private Integer inventoryRecordCount;
    private Integer palletCount;
    private Integer piecesPerPallet;
    private BigDecimal weightPerPiece;
}
