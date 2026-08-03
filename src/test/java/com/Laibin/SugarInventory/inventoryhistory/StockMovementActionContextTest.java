package com.Laibin.SugarInventory.inventoryhistory;

import com.Laibin.SugarInventory.inventoryhistory.service.StockMovementActionContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StockMovementActionContextTest {
    @Test
    void nestedScopeReusesOuterActionAndClearsAfterOwnerCloses() {
        StockMovementActionContext context = new StockMovementActionContext();

        try (StockMovementActionContext.Scope outer =
                     context.open("TRANSFER_LEGACY", "transfer-001")) {
            assertEquals("transfer-001", context.current().orElseThrow().businessActionId());
            assertEquals("TRANSFER_LEGACY", context.current().orElseThrow().actionKind());
            var actionTime = context.current().orElseThrow().occurredAt();

            try (StockMovementActionContext.Scope ignored = context.open("INBOUND", null)) {
                assertEquals("transfer-001", context.current().orElseThrow().businessActionId());
                assertEquals("TRANSFER_LEGACY", context.current().orElseThrow().actionKind());
                assertEquals(actionTime, context.current().orElseThrow().occurredAt());
            }

            assertEquals("transfer-001", context.current().orElseThrow().businessActionId());
        }

        assertTrue(context.current().isEmpty());
    }
}
