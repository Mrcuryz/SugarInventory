package com.Laibin.SugarInventory.printerassistant;

import com.Laibin.SugarInventory.printerassistant.service.PrinterAssistantPaths;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.IOException;
import java.nio.file.Files;

public final class PrinterAssistantBootstrap {

    private PrinterAssistantBootstrap() {
    }

    public static ConfigurableApplicationContext start(String[] args) {
        System.setProperty("java.awt.headless", "false");
        ensureAssistantDirectories();
        return new SpringApplicationBuilder(PrinterAssistantApplication.class)
                .headless(false)
                .run(mergeArgs(args,
                        "--server.address=127.0.0.1",
                        "--server.port=9527",
                        "--logging.file.name=" + PrinterAssistantPaths.getLogFilePath()
                ));
    }

    public static String[] mergeArgs(String[] originalArgs, String... extraArgs) {
        String[] safeOriginalArgs = originalArgs == null ? new String[0] : originalArgs;
        String[] mergedArgs = new String[safeOriginalArgs.length + extraArgs.length];
        System.arraycopy(safeOriginalArgs, 0, mergedArgs, 0, safeOriginalArgs.length);
        System.arraycopy(extraArgs, 0, mergedArgs, safeOriginalArgs.length, extraArgs.length);
        return mergedArgs;
    }

    private static void ensureAssistantDirectories() {
        try {
            Files.createDirectories(PrinterAssistantPaths.getHomeDir());
            Files.createDirectories(PrinterAssistantPaths.getLogsDir());
        } catch (IOException e) {
            throw new IllegalStateException("初始化打印助手目录失败", e);
        }
    }
}
