package com.Laibin.SugarInventory.printerassistant.service;

import java.nio.file.Path;
import java.nio.file.Paths;

public final class PrinterAssistantPaths {

    private PrinterAssistantPaths() {
    }

    public static Path getHomeDir() {
        return Paths.get(System.getProperty("user.home"), ".laibin-printer-assistant");
    }

    public static Path getConfigPath() {
        return getHomeDir().resolve("config.json");
    }

    public static Path getLogsDir() {
        return getHomeDir().resolve("logs");
    }

    public static Path getLogFilePath() {
        return getLogsDir().resolve("printer-assistant.log");
    }
}
