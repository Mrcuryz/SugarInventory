package com.Laibin.SugarInventory.inventoryhistory.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("inventory_daily_snapshot")
public class InventoryDailySnapshot {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String snapshotRunId;
    private LocalDate snapshotDate;
    private Integer productId;
    private String productNameSnapshot;
    private String productStatusSnapshot;
    private Integer warehouseId;
    private String warehouseNameSnapshot;
    private LocalDate productionDate;
    private Integer boardCount;
    private Integer loosePieceCount;
    private Integer totalPieces;
    private BigDecimal totalWeightKg;
    private Integer inventoryRecordCount;
    private Integer palletCount;
    private Integer piecesPerPalletSnapshot;
    private BigDecimal weightPerPieceSnapshot;
    private LocalDateTime createdAt;
}
