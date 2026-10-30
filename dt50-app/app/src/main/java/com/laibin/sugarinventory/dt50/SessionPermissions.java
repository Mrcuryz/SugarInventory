package com.laibin.sugarinventory.dt50;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class SessionPermissions {
    private static final String SEPARATOR = "\n";

    private SessionPermissions() {
    }

    static String encode(List<String> values) {
        Set<String> normalized = new LinkedHashSet<>();
        if (values != null) {
            for (String value : values) {
                if (value != null && !value.trim().isEmpty()) {
                    normalized.add(value.trim());
                }
            }
        }
        return String.join(SEPARATOR, normalized);
    }

    static List<String> decode(String value) {
        List<String> result = new ArrayList<>();
        if (value == null || value.trim().isEmpty()) {
            return result;
        }
        for (String item : value.split(SEPARATOR)) {
            if (!item.trim().isEmpty() && !result.contains(item.trim())) {
                result.add(item.trim());
            }
        }
        return result;
    }

    static boolean contains(String encoded, String permission) {
        return permission != null && decode(encoded).contains(permission.trim());
    }
}
