package com.Laibin.SugarInventory.printerassistant.service;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class PrinterAssistantAccessKeyService {

    static final int MINIMUM_KEY_LENGTH = 24;
    private static final int GENERATED_KEY_BYTES = 32;

    private final Path accessKeyPath;
    private final String configuredAccessKey;
    private final SecureRandom secureRandom = new SecureRandom();

    @Autowired
    public PrinterAssistantAccessKeyService(
            @Value("${printer-assistant.access-key:}") String configuredAccessKey
    ) {
        this(PrinterAssistantPaths.getAccessKeyPath(), configuredAccessKey);
    }

    PrinterAssistantAccessKeyService(Path accessKeyPath, String configuredAccessKey) {
        this.accessKeyPath = accessKeyPath;
        this.configuredAccessKey = normalize(configuredAccessKey);
    }

    @PostConstruct
    void initialize() {
        getAccessKey();
    }

    public synchronized String getAccessKey() {
        if (StringUtils.hasText(configuredAccessKey)) {
            return validate(configuredAccessKey);
        }
        if (Files.exists(accessKeyPath)) {
            try {
                return validate(Files.readString(accessKeyPath, StandardCharsets.UTF_8).trim());
            } catch (IOException exception) {
                throw new IllegalStateException("读取打印助手连接密钥失败", exception);
            }
        }

        byte[] bytes = new byte[GENERATED_KEY_BYTES];
        secureRandom.nextBytes(bytes);
        String generated = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        try {
            Files.createDirectories(accessKeyPath.getParent());
            Files.writeString(
                    accessKeyPath,
                    generated,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE
            );
            return generated;
        } catch (IOException exception) {
            throw new IllegalStateException("生成打印助手连接密钥失败", exception);
        }
    }

    public boolean matches(String candidate) {
        if (!StringUtils.hasText(candidate)) {
            return false;
        }
        byte[] expected = getAccessKey().getBytes(StandardCharsets.UTF_8);
        byte[] actual = candidate.trim().getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, actual);
    }

    private static String validate(String accessKey) {
        String normalized = normalize(accessKey);
        if (!StringUtils.hasText(normalized) || normalized.length() < MINIMUM_KEY_LENGTH) {
            throw new IllegalStateException("打印助手连接密钥至少需要 24 个字符");
        }
        return normalized;
    }

    private static String normalize(String accessKey) {
        return StringUtils.hasText(accessKey) ? accessKey.trim() : null;
    }
}
