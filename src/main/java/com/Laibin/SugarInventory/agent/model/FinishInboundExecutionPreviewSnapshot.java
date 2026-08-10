package com.Laibin.SugarInventory.agent.model;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class FinishInboundExecutionPreviewSnapshot {
    private final Integer previewVersion;
    private final List<Item> items;

    @Getter
    @Builder
    public static class Item {
        private final NormalizedInput normalizedInput;
        private final TaskState task;
        private final PalletState pallet;
        private final ProductState product;
        private final WarehouseState warehouse;
        private final ProductionOutputState productionOutput;
        private final List<MaterialInputState> materialInputs;
        private final List<MaterialBalanceState> materialBalances;
    }

    @Getter
    @Builder
    public static class NormalizedInput {
        private final String code;
        private final String warehouseName;
        private final LocalDate entryDate;
        private final String side;
        private final Integer quantity;
        private final String unit;
        private final String remark;
    }

    @Getter
    @Builder
    public static class TaskState {
        private final Integer id;
        private final String taskType;
        private final String status;
        private final Integer cycleNo;
        private final LocalDateTime createdAt;
    }

    @Getter
    @Builder
    public static class PalletState {
        private final Integer id;
        private final String status;
        private final Integer currentCycleNo;
        private final Integer productId;
        private final LocalDateTime updatedAt;
    }

    @Getter
    @Builder
    public static class ProductState {
        private final Integer id;
        private final String status;
        private final Integer piecesPerPallet;
        private final Boolean canStack;
        private final LocalDateTime updatedAt;
    }

    @Getter
    @Builder
    public static class WarehouseState {
        private final Integer id;
        private final String status;
        private final Integer maxRows;
        private final Integer curCapacity;
        private final List<Integer> leftUsedRowsLayer1;
        private final List<Integer> rightUsedRowsLayer1;
        private final List<Integer> leftUsedRowsLayer2;
        private final List<Integer> rightUsedRowsLayer2;
        private final LocalDateTime updatedAt;
    }

    @Getter
    @Builder
    public static class ProductionOutputState {
        private final Long id;
        private final String status;
        private final Integer quantity;
        private final String unit;
        private final Integer inventoryId;
    }

    @Getter
    @Builder
    public static class MaterialInputState {
        private final Integer id;
        private final Long balanceId;
        private final Integer quantity;
        private final String unit;
        private final Integer totalPieces;
        private final Boolean useAssay;
    }

    @Getter
    @Builder
    public static class MaterialBalanceState {
        private final Long id;
        private final String status;
        private final Integer remainingPieces;
        private final LocalDateTime updatedAt;
    }
}
