package com.Laibin.SugarInventory.printerassistant.service;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
public class LaunchOnStartupService {

    private static final String RUN_KEY = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run";
    private static final String VALUE_NAME = "LaibinPrinterAssistant";

    public boolean isEnabled() {
        try {
            Process process = new ProcessBuilder("reg", "query", RUN_KEY, "/v", VALUE_NAME)
                    .redirectErrorStream(true)
                    .start();
            return process.waitFor() == 0;
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public boolean isSupported() {
        return StringUtils.hasText(resolveLaunchCommand());
    }

    public void setEnabled(boolean enabled) {
        String launchCommand = resolveLaunchCommand();
        if (!StringUtils.hasText(launchCommand)) {
            throw new IllegalStateException("当前运行方式不支持配置开机自启，请使用安装包版本");
        }
        try {
            Process process = enabled
                    ? new ProcessBuilder("reg", "add", RUN_KEY, "/v", VALUE_NAME, "/t", "REG_SZ", "/d", launchCommand, "/f")
                    .redirectErrorStream(true)
                    .start()
                    : new ProcessBuilder("reg", "delete", RUN_KEY, "/v", VALUE_NAME, "/f")
                    .redirectErrorStream(true)
                    .start();
            if (process.waitFor() != 0) {
                throw new IllegalStateException(enabled ? "设置开机自启失败" : "关闭开机自启失败");
            }
        } catch (IOException e) {
            throw new IllegalStateException(enabled ? "设置开机自启失败" : "关闭开机自启失败", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(enabled ? "设置开机自启失败" : "关闭开机自启失败", e);
        }
    }

    private String resolveLaunchCommand() {
        String appPath = System.getProperty("jpackage.app-path");
        if (StringUtils.hasText(appPath)) {
            return quote(appPath);
        }

        String javaCommand = ProcessHandle.current().info().command().orElse(null);
        String classPath = System.getProperty("java.class.path");
        if (!StringUtils.hasText(javaCommand) || !StringUtils.hasText(classPath)) {
            return null;
        }

        List<String> segments = new ArrayList<>();
        segments.add(quote(javaCommand));
        segments.add("-cp");
        segments.add(quote(classPath));
        segments.add("com.Laibin.SugarInventory.printerassistant.desktop.PrinterAssistantDesktopApplication");
        return String.join(" ", segments);
    }

    private static String quote(String text) {
        return "\"" + text + "\"";
    }
}
