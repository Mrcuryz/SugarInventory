package com.laibin.sugarinventory.dt50;

import java.net.URLDecoder;
import java.io.UnsupportedEncodingException;
import java.util.Locale;

final class ScanCodeClassifier {
    private static final String WAREHOUSE_PREFIX = "LSI|WAREHOUSE|1|";

    private ScanCodeClassifier() {
    }

    static boolean isWarehouseCode(String raw) {
        return normalizeRaw(raw).toUpperCase(Locale.ROOT).startsWith(WAREHOUSE_PREFIX);
    }

    static String normalizePalletInput(String raw) {
        String value = normalizeRaw(raw);
        int questionMark = value.indexOf('?');
        if (questionMark >= 0 && questionMark < value.length() - 1) {
            String[] parts = value.substring(questionMark + 1).split("&");
            for (String part : parts) {
                int separator = part.indexOf('=');
                if (separator <= 0) {
                    continue;
                }
                String key = decode(part.substring(0, separator));
                if ("code".equalsIgnoreCase(key) || "palletCode".equalsIgnoreCase(key)) {
                    return decode(part.substring(separator + 1)).trim();
                }
            }
        }
        return value;
    }

    static String duplicateKey(String raw) {
        return normalizePalletInput(raw).trim().toUpperCase(Locale.ROOT);
    }

    private static String normalizeRaw(String raw) {
        return raw == null ? "" : raw.trim();
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value, "UTF-8");
        } catch (UnsupportedEncodingException exception) {
            return value;
        }
    }
}
