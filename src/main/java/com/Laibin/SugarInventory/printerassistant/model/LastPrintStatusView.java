package com.Laibin.SugarInventory.printerassistant.model;

import java.time.LocalDateTime;

public record LastPrintStatusView(
        boolean success,
        String message,
        String printerName,
        Integer printedCount,
        LocalDateTime timestamp
) {
}
