package com.Laibin.SugarInventory.inventoryhistory;

import com.Laibin.SugarInventory.inventoryhistory.domain.InventoryBalanceAggregate;
import com.Laibin.SugarInventory.inventoryhistory.domain.StockMovementEvent;
import com.Laibin.SugarInventory.inventoryhistory.service.InventoryReconciliationCalculator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryReconciliationCalculatorTest {
    private final InventoryReconciliationCalculator calculator =
            new InventoryReconciliationCalculator();

    @Test
    void inboundAndOutboundReconcileAtGlobalAndWarehouseLevels() {
        InventoryBalanceAggregate openingGlobal = balance(1, 0, 100, "2500.0000");
        InventoryBalanceAggregate closingGlobal = balance(1, 0, 130, "3250.0000");
        InventoryBalanceAggregate openingWarehouse = balance(1, 2, 100, "2500.0000");
        InventoryBalanceAggregate closingWarehouse = balance(1, 2, 130, "3250.0000");
        StockMovementEvent inbound = event("INBOUND", 50, "1250.0000", null, 2, "in-1");
        StockMovementEvent outbound = event("OUTBOUND", 20, "500.0000", 2, null, "out-1");

        InventoryReconciliationCalculator.Calculation calculation = calculator.calculate(
                List.of(openingGlobal),
                List.of(closingGlobal),
                List.of(openingWarehouse),
                List.of(closingWarehouse),
                List.of(inbound, outbound),
                List.of(inbound, outbound)
        );

        assertEquals(2, calculation.results().size());
        assertTrue(calculation.results().stream()
                .allMatch(result -> "PASSED".equals(result.status())));
        assertEquals(0, calculation.failedResultCount());
        assertEquals(0, calculation.maxAbsPieceDifference());
        assertTrue(calculation.issues().isEmpty());
    }

    @Test
    void singlePalletTransferIsGlobalZeroAndWarehouseBalanced() {
        InventoryBalanceAggregate openingGlobal = balance(1, 0, 40, "1000.0000");
        InventoryBalanceAggregate closingGlobal = balance(1, 0, 40, "1000.0000");
        InventoryBalanceAggregate sourceOpening = balance(1, 2, 40, "1000.0000");
        InventoryBalanceAggregate targetClosing = balance(1, 3, 40, "1000.0000");
        StockMovementEvent transfer =
                event("TRANSFER", 40, "1000.0000", 2, 3, "transfer-1");

        InventoryReconciliationCalculator.Calculation calculation = calculator.calculate(
                List.of(openingGlobal),
                List.of(closingGlobal),
                List.of(sourceOpening),
                List.of(targetClosing),
                List.of(transfer),
                List.of(transfer)
        );

        assertEquals(3, calculation.results().size());
        assertTrue(calculation.results().stream()
                .allMatch(result -> "PASSED".equals(result.status())));
        assertTrue(calculation.issues().isEmpty());
    }

    @Test
    void incompleteLegacyTransferIsAReleaseBlockingIssue() {
        InventoryBalanceAggregate openingGlobal = balance(1, 0, 40, "1000.0000");
        InventoryBalanceAggregate closingGlobal = balance(1, 0, 40, "1000.0000");
        StockMovementEvent transferOut =
                event("TRANSFER_OUT", 40, "1000.0000", 2, null, "legacy-1");

        InventoryReconciliationCalculator.Calculation calculation = calculator.calculate(
                List.of(openingGlobal),
                List.of(closingGlobal),
                List.of(),
                List.of(),
                List.of(transferOut),
                List.of(transferOut)
        );

        assertTrue(calculation.issues().stream().anyMatch(issue ->
                "LEGACY_TRANSFER_IMBALANCE".equals(issue.issueCode())
                        && "BLOCKER".equals(issue.severity())));
    }

    @Test
    void missingWarehouseEndpointIsNotSilentlyIgnored() {
        StockMovementEvent inbound =
                event("INBOUND", 40, "1000.0000", null, null, "in-missing");

        InventoryReconciliationCalculator.Calculation calculation = calculator.calculate(
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(inbound),
                List.of(inbound)
        );

        assertTrue(calculation.issues().stream().anyMatch(issue ->
                "MISSING_MOVEMENT_ENDPOINT".equals(issue.issueCode())));
    }

    private InventoryBalanceAggregate balance(
            int productId,
            int warehouseId,
            int pieces,
            String weight
    ) {
        InventoryBalanceAggregate balance = new InventoryBalanceAggregate();
        balance.setProductId(productId);
        balance.setProductName("黄冰糖（袋）");
        balance.setWarehouseId(warehouseId);
        balance.setWarehouseName(warehouseId == 0 ? null : warehouseId + "号库位");
        balance.setTotalPieces(pieces);
        balance.setTotalWeightKg(new BigDecimal(weight));
        return balance;
    }

    private StockMovementEvent event(
            String type,
            int pieces,
            String weight,
            Integer fromWarehouse,
            Integer toWarehouse,
            String actionId
    ) {
        StockMovementEvent event = new StockMovementEvent();
        event.setEventType(type);
        event.setEventId("evt_" + actionId);
        event.setBusinessActionId(actionId);
        event.setSourceType("TEST");
        event.setSourceRecordId((long) Math.abs(actionId.hashCode()));
        event.setOccurredAt(LocalDateTime.of(2026, 7, 30, 8, 0));
        event.setProductId(1);
        event.setProductStatus("成品");
        event.setFromWarehouseId(fromWarehouse);
        event.setToWarehouseId(toWarehouse);
        event.setTotalPieces(pieces);
        event.setTotalWeightKg(new BigDecimal(weight));
        return event;
    }
}
