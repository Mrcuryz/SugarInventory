package com.Laibin.SugarInventory.inventoryhistory;

import com.Laibin.SugarInventory.inventoryhistory.domain.StockMovementEvent;
import com.Laibin.SugarInventory.inventoryhistory.service.StockMovementMath;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StockMovementMathTest {
    @Test
    void legacyTransferTwoLegsHaveZeroGlobalDelta() {
        StockMovementEvent out = event("TRANSFER_OUT", 1, null, 40);
        StockMovementEvent in = event("TRANSFER_IN", null, 2, 40);

        assertEquals(0,
                StockMovementMath.globalPieceDelta(out) + StockMovementMath.globalPieceDelta(in));
        assertEquals(-40, StockMovementMath.warehousePieceDelta(out, 1));
        assertEquals(40, StockMovementMath.warehousePieceDelta(in, 2));
    }

    @Test
    void palletTransferMovesWarehouseStockButNotGlobalStock() {
        StockMovementEvent transfer = event("TRANSFER", 1, 2, 40);

        assertEquals(0, StockMovementMath.globalPieceDelta(transfer));
        assertEquals(-40, StockMovementMath.warehousePieceDelta(transfer, 1));
        assertEquals(40, StockMovementMath.warehousePieceDelta(transfer, 2));
    }

    private StockMovementEvent event(String type, Integer fromWarehouseId,
                                     Integer toWarehouseId, int pieces) {
        StockMovementEvent event = new StockMovementEvent();
        event.setEventType(type);
        event.setFromWarehouseId(fromWarehouseId);
        event.setToWarehouseId(toWarehouseId);
        event.setTotalPieces(pieces);
        return event;
    }
}
