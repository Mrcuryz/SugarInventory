package com.Laibin.SugarInventory.printerassistant.model;

public record LocalPrinterConfigData(String defaultPrinterName, Boolean launchOnStartup) {

    public boolean safeLaunchOnStartup() {
        return Boolean.TRUE.equals(launchOnStartup);
    }
}
