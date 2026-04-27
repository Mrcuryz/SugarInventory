package com.Laibin.SugarInventory.printerassistant.service;

import com.Laibin.SugarInventory.printerassistant.model.LocalPrinterConfigData;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class LocalPrinterConfigService {

    private final Path configPath;

    public LocalPrinterConfigService() {
        this(resolveDefaultConfigPath());
    }

    LocalPrinterConfigService(Path configPath) {
        this.configPath = configPath;
    }

    public synchronized LocalPrinterConfigData load() {
        if (!Files.exists(configPath)) {
            return new LocalPrinterConfigData(null, false);
        }
        try {
            String json = Files.readString(configPath, StandardCharsets.UTF_8);
            if (!StringUtils.hasText(json)) {
                return new LocalPrinterConfigData(null, false);
            }
            JSONObject data = JSON.parseObject(json);
            return new LocalPrinterConfigData(
                    data == null ? null : normalizePrinterName(data.getString("defaultPrinterName")),
                    data != null && Boolean.TRUE.equals(data.getBoolean("launchOnStartup"))
            );
        } catch (IOException | RuntimeException e) {
            return new LocalPrinterConfigData(null, false);
        }
    }

    public synchronized void saveDefaultPrinter(String printerName) {
        LocalPrinterConfigData current = load();
        save(printerName, current.safeLaunchOnStartup());
    }

    public synchronized void save(String printerName, boolean launchOnStartup) {
        String normalizedPrinterName = normalizePrinterName(printerName);
        JSONObject data = new JSONObject();
        data.put("defaultPrinterName", normalizedPrinterName);
        data.put("launchOnStartup", launchOnStartup);
        try {
            Files.createDirectories(configPath.getParent());
            Files.writeString(
                    configPath,
                    JSON.toJSONString(data, SerializerFeature.PrettyFormat),
                    StandardCharsets.UTF_8
            );
        } catch (IOException e) {
            throw new IllegalStateException("保存本地打印配置失败", e);
        }
    }

    private static String normalizePrinterName(String printerName) {
        return StringUtils.hasText(printerName) ? printerName.trim() : null;
    }

    private static Path resolveDefaultConfigPath() {
        return PrinterAssistantPaths.getConfigPath();
    }
}
