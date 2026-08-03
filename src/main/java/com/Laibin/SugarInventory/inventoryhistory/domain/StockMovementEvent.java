package com.Laibin.SugarInventory.inventoryhistory.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("stock_movement_event")
public class StockMovementEvent {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String eventId;
    private String eventType;
    private String businessActionId;
    private String sourceType;
    private Long sourceRecordId;
    private LocalDateTime occurredAt;
    private LocalDateTime recordedAt;
    private Integer productId;
    private String productStatus;
    private LocalDate productionDate;
    private Integer fromWarehouseId;
    private Integer toWarehouseId;
    private Integer palletCodeId;
    private Integer boardQuantity;
    private Integer loosePieceQuantity;
    private Integer totalPieces;
    private BigDecimal totalWeightKg;
    private String conversionRuleVersion;
    private Integer operatorId;
    private String actionKind;
    private String metadataJson;
    private LocalDateTime createdAt;
}
