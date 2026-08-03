package com.Laibin.SugarInventory.inventoryhistory.service;

import java.time.LocalDate;

public record InventoryHistoryJobResult(
        LocalDate businessDate,
        String mode,
        boolean operationalSuccess,
        boolean dataQualityPassed,
        boolean snapshotReused,
        String snapshotRunId,
        String reconciliationRunId,
        String reconciliationStatus,
        String message
) {
    public int exitCode() {
        if (!operationalSuccess) {
            return 2;
        }
        return dataQualityPassed ? 0 : 3;
    }
}
