package com.Laibin.SugarInventory.inventoryhistory.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("inventory_reconciliation_result")
public class InventoryReconciliationResult {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String reconciliationRunId;
    private LocalDate businessDate;
    private String dimensionType;
    private Integer productId;
    private String productNameSnapshot;
    private Integer warehouseId;
    private String warehouseNameSnapshot;
    private Integer openingPieces;
    private Integer inboundPieces;
    private Integer outboundPieces;
    private Integer transferInPieces;
    private Integer transferOutPieces;
    private Integer expectedClosingPieces;
    private Integer actualClosingPieces;
    private Integer pieceDifference;
    private BigDecimal openingWeightKg;
    private BigDecimal inboundWeightKg;
    private BigDecimal outboundWeightKg;
    private BigDecimal transferInWeightKg;
    private BigDecimal transferOutWeightKg;
    private BigDecimal expectedClosingWeightKg;
    private BigDecimal actualClosingWeightKg;
    private BigDecimal weightDifferenceKg;
    private String status;
    private LocalDateTime createdAt;
}
