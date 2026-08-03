package com.Laibin.SugarInventory.inventoryhistory.service;

import com.Laibin.SugarInventory.inventoryhistory.domain.InventoryBalanceAggregate;
import com.Laibin.SugarInventory.inventoryhistory.domain.StockMovementEvent;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class InventoryReconciliationCalculator {
    private static final Set<String> SUPPORTED_EVENT_TYPES = Set.of(
            "INBOUND",
            "OUTBOUND",
            "TRANSFER_IN",
            "TRANSFER_OUT",
            "TRANSFER"
    );

    public Calculation calculate(
            List<InventoryBalanceAggregate> openingGlobal,
            List<InventoryBalanceAggregate> closingGlobal,
            List<InventoryBalanceAggregate> openingWarehouse,
            List<InventoryBalanceAggregate> closingWarehouse,
            List<StockMovementEvent> dailyEvents,
            List<StockMovementEvent> validationEvents
    ) {
        List<IssueDraft> issues = validateEvents(dailyEvents, validationEvents);

        Map<DimensionKey, Balance> openingGlobalMap = balances(openingGlobal, true);
        Map<DimensionKey, Balance> closingGlobalMap = balances(closingGlobal, true);
        Map<DimensionKey, Balance> openingWarehouseMap = balances(openingWarehouse, false);
        Map<DimensionKey, Balance> closingWarehouseMap = balances(closingWarehouse, false);
        Map<DimensionKey, Movement> globalMovements = new HashMap<>();
        Map<DimensionKey, Movement> warehouseMovements = new HashMap<>();

        for (StockMovementEvent event : dailyEvents) {
            if (!SUPPORTED_EVENT_TYPES.contains(event.getEventType()) || event.getProductId() == null) {
                continue;
            }
            applyGlobalMovement(globalMovements, event);
            applyWarehouseMovement(warehouseMovements, event);
        }

        List<ResultDraft> results = new ArrayList<>();
        buildResults(
                "GLOBAL_PRODUCT",
                openingGlobalMap,
                closingGlobalMap,
                globalMovements,
                results,
                issues
        );
        buildResults(
                "WAREHOUSE_PRODUCT",
                openingWarehouseMap,
                closingWarehouseMap,
                warehouseMovements,
                results,
                issues
        );
        int maxAbsPieceDifference = results.stream()
                .mapToInt(result -> Math.abs(result.pieceDifference()))
                .max()
                .orElse(0);
        long failedResultCount = results.stream()
                .filter(result -> "FAILED".equals(result.status()))
                .count();
        return new Calculation(
                List.copyOf(results),
                List.copyOf(issues),
                (int) failedResultCount,
                maxAbsPieceDifference
        );
    }

    private Map<DimensionKey, Balance> balances(
            List<InventoryBalanceAggregate> rows,
            boolean global
    ) {
        Map<DimensionKey, Balance> result = new HashMap<>();
        for (InventoryBalanceAggregate row : rows) {
            int warehouseId = global ? 0 : safeInt(row.getWarehouseId());
            DimensionKey key = new DimensionKey(row.getProductId(), warehouseId);
            result.put(key, new Balance(
                    safeInt(row.getTotalPieces()),
                    decimal(row.getTotalWeightKg()),
                    row.getProductName(),
                    global ? null : row.getWarehouseName()
            ));
        }
        return result;
    }

    private void applyGlobalMovement(
            Map<DimensionKey, Movement> movements,
            StockMovementEvent event
    ) {
        DimensionKey key = new DimensionKey(event.getProductId(), 0);
        Movement movement = movements.computeIfAbsent(key, ignored -> new Movement());
        switch (event.getEventType()) {
            case "INBOUND" -> movement.addInbound(event);
            case "OUTBOUND" -> movement.addOutbound(event);
            case "TRANSFER_IN" -> movement.addTransferIn(event);
            case "TRANSFER_OUT" -> movement.addTransferOut(event);
            default -> {
                // A single TRANSFER changes warehouse distribution, not global stock.
            }
        }
    }

    private void applyWarehouseMovement(
            Map<DimensionKey, Movement> movements,
            StockMovementEvent event
    ) {
        switch (event.getEventType()) {
            case "INBOUND" -> movement(
                    movements,
                    event.getProductId(),
                    event.getToWarehouseId()
            ).addInbound(event);
            case "OUTBOUND" -> movement(
                    movements,
                    event.getProductId(),
                    event.getFromWarehouseId()
            ).addOutbound(event);
            case "TRANSFER_IN" -> movement(
                    movements,
                    event.getProductId(),
                    event.getToWarehouseId()
            ).addTransferIn(event);
            case "TRANSFER_OUT" -> movement(
                    movements,
                    event.getProductId(),
                    event.getFromWarehouseId()
            ).addTransferOut(event);
            case "TRANSFER" -> {
                movement(movements, event.getProductId(), event.getFromWarehouseId())
                        .addTransferOut(event);
                movement(movements, event.getProductId(), event.getToWarehouseId())
                        .addTransferIn(event);
            }
            default -> {
                // Unsupported events are captured as data-quality issues.
            }
        }
    }

    private Movement movement(
            Map<DimensionKey, Movement> movements,
            Integer productId,
            Integer warehouseId
    ) {
        return movements.computeIfAbsent(
                new DimensionKey(productId, safeInt(warehouseId)),
                ignored -> new Movement()
        );
    }

    private void buildResults(
            String dimensionType,
            Map<DimensionKey, Balance> opening,
            Map<DimensionKey, Balance> closing,
            Map<DimensionKey, Movement> movements,
            List<ResultDraft> results,
            List<IssueDraft> issues
    ) {
        Set<DimensionKey> keys = new LinkedHashSet<>();
        keys.addAll(opening.keySet());
        keys.addAll(closing.keySet());
        keys.addAll(movements.keySet());
        keys.stream()
                .sorted()
                .forEach(key -> {
                    Balance open = opening.getOrDefault(key, Balance.ZERO);
                    Balance close = closing.getOrDefault(key, Balance.ZERO);
                    Movement movement = movements.getOrDefault(key, new Movement());
                    int expectedPieces = open.pieces
                            + movement.inboundPieces
                            - movement.outboundPieces
                            + movement.transferInPieces
                            - movement.transferOutPieces;
                    int pieceDifference = close.pieces - expectedPieces;
                    BigDecimal expectedWeight = scale(open.weight
                            .add(movement.inboundWeight)
                            .subtract(movement.outboundWeight)
                            .add(movement.transferInWeight)
                            .subtract(movement.transferOutWeight));
                    BigDecimal weightDifference = scale(close.weight.subtract(expectedWeight));
                    String status = pieceDifference == 0
                            && weightDifference.compareTo(BigDecimal.ZERO) == 0
                            ? "PASSED"
                            : "FAILED";
                    String productName = firstNonBlank(close.productName, open.productName,
                            "产品#" + key.productId);
                    String warehouseName = "GLOBAL_PRODUCT".equals(dimensionType)
                            ? null
                            : firstNonBlank(close.warehouseName, open.warehouseName,
                            "仓库#" + key.warehouseId);
                    ResultDraft result = new ResultDraft(
                            dimensionType,
                            key.productId,
                            productName,
                            key.warehouseId,
                            warehouseName,
                            open.pieces,
                            movement.inboundPieces,
                            movement.outboundPieces,
                            movement.transferInPieces,
                            movement.transferOutPieces,
                            expectedPieces,
                            close.pieces,
                            pieceDifference,
                            scale(open.weight),
                            scale(movement.inboundWeight),
                            scale(movement.outboundWeight),
                            scale(movement.transferInWeight),
                            scale(movement.transferOutWeight),
                            expectedWeight,
                            scale(close.weight),
                            weightDifference,
                            status
                    );
                    results.add(result);
                    if ("FAILED".equals(status)) {
                        issues.add(new IssueDraft(
                                "GLOBAL_PRODUCT".equals(dimensionType)
                                        ? "GLOBAL_RECONCILIATION_DIFFERENCE"
                                        : "WAREHOUSE_RECONCILIATION_DIFFERENCE",
                                "BLOCKER",
                                "RECONCILIATION_RESULT",
                                null,
                                null,
                                key.productId,
                                "GLOBAL_PRODUCT".equals(dimensionType) ? null : key.warehouseId,
                                "库存守恒差异：期望 " + expectedPieces
                                        + " 件，实际 " + close.pieces
                                        + " 件，差异 " + pieceDifference + " 件",
                                null
                        ));
                    }
                });
    }

    private List<IssueDraft> validateEvents(
            List<StockMovementEvent> dailyEvents,
            List<StockMovementEvent> validationEvents
    ) {
        List<IssueDraft> issues = new ArrayList<>();
        Set<String> sourceKeys = new HashSet<>();
        Set<String> transferActionsInDay = new HashSet<>();
        for (StockMovementEvent event : dailyEvents) {
            String sourceKey = event.getSourceType() + "|"
                    + event.getSourceRecordId() + "|" + event.getEventType();
            if (!sourceKeys.add(sourceKey)) {
                issues.add(issue(event, "DUPLICATE_EVENT_SOURCE", "BLOCKER",
                        "同一来源记录出现重复库存事件"));
            }
            if (!SUPPORTED_EVENT_TYPES.contains(event.getEventType())) {
                issues.add(issue(event, "UNKNOWN_EVENT_TYPE", "BLOCKER",
                        "存在未登记的库存事件类型"));
                continue;
            }
            if (safeInt(event.getTotalPieces()) <= 0) {
                issues.add(issue(event, "NON_POSITIVE_EVENT_QUANTITY", "BLOCKER",
                        "库存事件折算件数必须大于 0"));
            }
            if (decimal(event.getTotalWeightKg()).signum() < 0) {
                issues.add(issue(event, "NEGATIVE_EVENT_WEIGHT", "BLOCKER",
                        "库存事件重量不能为负数"));
            }
            validateEndpoints(event, issues);
            if ("TRANSFER_IN".equals(event.getEventType())
                    || "TRANSFER_OUT".equals(event.getEventType())) {
                transferActionsInDay.add(event.getBusinessActionId());
            }
        }
        validateLegacyTransferActions(validationEvents, transferActionsInDay, issues);
        return issues;
    }

    private void validateEndpoints(StockMovementEvent event, List<IssueDraft> issues) {
        boolean missing = switch (event.getEventType()) {
            case "INBOUND", "TRANSFER_IN" -> event.getToWarehouseId() == null;
            case "OUTBOUND", "TRANSFER_OUT" -> event.getFromWarehouseId() == null;
            case "TRANSFER" -> event.getFromWarehouseId() == null
                    || event.getToWarehouseId() == null
                    || event.getFromWarehouseId().equals(event.getToWarehouseId());
            default -> false;
        };
        if (missing) {
            issues.add(issue(event, "MISSING_MOVEMENT_ENDPOINT", "BLOCKER",
                    "库存事件缺少有效的来源或目标仓库"));
        }
    }

    private void validateLegacyTransferActions(
            List<StockMovementEvent> validationEvents,
            Set<String> transferActionsInDay,
            List<IssueDraft> issues
    ) {
        Map<String, LegacyTransferBalance> balances = new HashMap<>();
        for (StockMovementEvent event : validationEvents) {
            if (!transferActionsInDay.contains(event.getBusinessActionId())) {
                continue;
            }
            if (!"TRANSFER_IN".equals(event.getEventType())
                    && !"TRANSFER_OUT".equals(event.getEventType())) {
                continue;
            }
            LegacyTransferBalance balance = balances.computeIfAbsent(
                    event.getBusinessActionId(),
                    ignored -> new LegacyTransferBalance(event)
            );
            balance.add(event);
        }
        for (LegacyTransferBalance balance : balances.values()) {
            if (balance.inPieces != balance.outPieces
                    || scale(balance.inWeight).compareTo(scale(balance.outWeight)) != 0
                    || balance.inCount == 0
                    || balance.outCount == 0) {
                StockMovementEvent event = balance.sample;
                issues.add(issue(event, "LEGACY_TRANSFER_IMBALANCE", "BLOCKER",
                        "旧调拨的调出与调入记录不完整或数量不一致"));
            }
        }
    }

    private IssueDraft issue(
            StockMovementEvent event,
            String code,
            String severity,
            String message
    ) {
        return new IssueDraft(
                code,
                severity,
                event.getSourceType(),
                event.getSourceRecordId(),
                event.getBusinessActionId(),
                event.getProductId(),
                firstNonNull(event.getFromWarehouseId(), event.getToWarehouseId()),
                message,
                null
        );
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private BigDecimal decimal(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private BigDecimal scale(BigDecimal value) {
        return decimal(value).setScale(4, RoundingMode.HALF_UP);
    }

    private Integer firstNonNull(Integer first, Integer second) {
        return first != null ? first : second;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    public record Calculation(
            List<ResultDraft> results,
            List<IssueDraft> issues,
            int failedResultCount,
            int maxAbsPieceDifference
    ) {
    }

    public record ResultDraft(
            String dimensionType,
            Integer productId,
            String productName,
            Integer warehouseId,
            String warehouseName,
            int openingPieces,
            int inboundPieces,
            int outboundPieces,
            int transferInPieces,
            int transferOutPieces,
            int expectedClosingPieces,
            int actualClosingPieces,
            int pieceDifference,
            BigDecimal openingWeight,
            BigDecimal inboundWeight,
            BigDecimal outboundWeight,
            BigDecimal transferInWeight,
            BigDecimal transferOutWeight,
            BigDecimal expectedClosingWeight,
            BigDecimal actualClosingWeight,
            BigDecimal weightDifference,
            String status
    ) {
    }

    public record IssueDraft(
            String issueCode,
            String severity,
            String sourceType,
            Long sourceRecordId,
            String businessActionId,
            Integer productId,
            Integer warehouseId,
            String message,
            String detailsJson
    ) {
    }

    private record DimensionKey(Integer productId, int warehouseId)
            implements Comparable<DimensionKey> {
        @Override
        public int compareTo(DimensionKey other) {
            int productComparison = Integer.compare(productId, other.productId);
            return productComparison != 0
                    ? productComparison
                    : Integer.compare(warehouseId, other.warehouseId);
        }
    }

    private static final class Balance {
        private static final Balance ZERO =
                new Balance(0, BigDecimal.ZERO, null, null);

        private final int pieces;
        private final BigDecimal weight;
        private final String productName;
        private final String warehouseName;

        private Balance(
                int pieces,
                BigDecimal weight,
                String productName,
                String warehouseName
        ) {
            this.pieces = pieces;
            this.weight = weight;
            this.productName = productName;
            this.warehouseName = warehouseName;
        }
    }

    private static final class Movement {
        private int inboundPieces;
        private int outboundPieces;
        private int transferInPieces;
        private int transferOutPieces;
        private BigDecimal inboundWeight = BigDecimal.ZERO;
        private BigDecimal outboundWeight = BigDecimal.ZERO;
        private BigDecimal transferInWeight = BigDecimal.ZERO;
        private BigDecimal transferOutWeight = BigDecimal.ZERO;

        private void addInbound(StockMovementEvent event) {
            inboundPieces += event.getTotalPieces();
            inboundWeight = inboundWeight.add(event.getTotalWeightKg());
        }

        private void addOutbound(StockMovementEvent event) {
            outboundPieces += event.getTotalPieces();
            outboundWeight = outboundWeight.add(event.getTotalWeightKg());
        }

        private void addTransferIn(StockMovementEvent event) {
            transferInPieces += event.getTotalPieces();
            transferInWeight = transferInWeight.add(event.getTotalWeightKg());
        }

        private void addTransferOut(StockMovementEvent event) {
            transferOutPieces += event.getTotalPieces();
            transferOutWeight = transferOutWeight.add(event.getTotalWeightKg());
        }
    }

    private static final class LegacyTransferBalance {
        private final StockMovementEvent sample;
        private int inCount;
        private int outCount;
        private int inPieces;
        private int outPieces;
        private BigDecimal inWeight = BigDecimal.ZERO;
        private BigDecimal outWeight = BigDecimal.ZERO;

        private LegacyTransferBalance(StockMovementEvent sample) {
            this.sample = sample;
        }

        private void add(StockMovementEvent event) {
            if ("TRANSFER_IN".equals(event.getEventType())) {
                inCount++;
                inPieces += event.getTotalPieces();
                inWeight = inWeight.add(event.getTotalWeightKg());
            } else {
                outCount++;
                outPieces += event.getTotalPieces();
                outWeight = outWeight.add(event.getTotalWeightKg());
            }
        }
    }
}
