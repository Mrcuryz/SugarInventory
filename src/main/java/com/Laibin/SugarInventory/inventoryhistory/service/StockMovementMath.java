package com.Laibin.SugarInventory.inventoryhistory.service;

import com.Laibin.SugarInventory.inventoryhistory.domain.StockMovementEvent;

import java.util.Objects;

public final class StockMovementMath {
    private StockMovementMath() {
    }

    public static int globalPieceDelta(StockMovementEvent event) {
        int pieces = safePieces(event);
        return switch (event.getEventType()) {
            case "INBOUND", "TRANSFER_IN" -> pieces;
            case "OUTBOUND", "TRANSFER_OUT" -> -pieces;
            case "TRANSFER" -> 0;
            default -> 0;
        };
    }

    public static int warehousePieceDelta(StockMovementEvent event, Integer warehouseId) {
        int pieces = safePieces(event);
        return switch (event.getEventType()) {
            case "INBOUND", "TRANSFER_IN" ->
                    Objects.equals(event.getToWarehouseId(), warehouseId) ? pieces : 0;
            case "OUTBOUND", "TRANSFER_OUT" ->
                    Objects.equals(event.getFromWarehouseId(), warehouseId) ? -pieces : 0;
            case "TRANSFER" -> {
                int delta = 0;
                if (Objects.equals(event.getFromWarehouseId(), warehouseId)) {
                    delta -= pieces;
                }
                if (Objects.equals(event.getToWarehouseId(), warehouseId)) {
                    delta += pieces;
                }
                yield delta;
            }
            default -> 0;
        };
    }

    private static int safePieces(StockMovementEvent event) {
        return event.getTotalPieces() == null ? 0 : Math.max(0, event.getTotalPieces());
    }
}
