package com.Laibin.SugarInventory.inventoryhistory.service;

import com.Laibin.SugarInventory.inventoryhistory.domain.InventorySnapshotRun;

public record InventorySnapshotCaptureResult(
        InventorySnapshotRun snapshotRun,
        boolean reused
) {
}
