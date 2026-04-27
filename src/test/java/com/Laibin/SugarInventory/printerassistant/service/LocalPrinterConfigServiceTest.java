package com.Laibin.SugarInventory.printerassistant.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalPrinterConfigServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void saveDefaultPrinterShouldPersistToJsonFile() throws Exception {
        Path configPath = tempDir.resolve("config.json");
        LocalPrinterConfigService service = new LocalPrinterConfigService(configPath);

        service.save("POSTEK G-3106", true);

        assertEquals("POSTEK G-3106", service.load().defaultPrinterName());
        assertEquals(true, service.load().safeLaunchOnStartup());
        String content = Files.readString(configPath, StandardCharsets.UTF_8);
        assertTrue(content.contains("POSTEK G-3106"));
        assertTrue(content.contains("\"launchOnStartup\":true"));
    }

    @Test
    void loadShouldFallbackToEmptyConfigWhenFileBroken() throws Exception {
        Path configPath = tempDir.resolve("config.json");
        Files.writeString(configPath, "{bad-json", StandardCharsets.UTF_8);
        LocalPrinterConfigService service = new LocalPrinterConfigService(configPath);

        assertNull(service.load().defaultPrinterName());
        assertEquals(false, service.load().safeLaunchOnStartup());
    }
}
