package com.Laibin.SugarInventory.inventoryhistory.domain;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Value
@Builder
public class StockMovementEventCommand {
    String eventType;
    String sourceType;
    Long sourceRecordId;
    LocalDateTime occurredAt;
    Integer productId;
    String productStatus;
    LocalDate productionDate;
    Integer fromWarehouseId;
    Integer toWarehouseId;
    Integer palletCodeId;
    Integer boardQuantity;
    Integer loosePieceQuantity;
    Integer totalPieces;
    BigDecimal totalWeightKg;
    Integer operatorId;
    String actionKind;
    String businessActionId;
    String metadataJson;
}
