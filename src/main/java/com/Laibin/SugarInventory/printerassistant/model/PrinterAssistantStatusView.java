package com.Laibin.SugarInventory.printerassistant.model;

public record PrinterAssistantStatusView(
        boolean running,
        String address,
        String message,
        String logFile,
        boolean launchOnStartup,
        LastPrintStatusView lastPrintStatus
) {
}
