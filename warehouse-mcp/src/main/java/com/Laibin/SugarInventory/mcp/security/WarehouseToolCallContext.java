package com.Laibin.SugarInventory.mcp.security;

import java.util.Optional;
import java.util.function.Supplier;

public final class WarehouseToolCallContext {
    private static final ThreadLocal<String> TOOL_NAME = new ThreadLocal<>();

    private WarehouseToolCallContext() {
    }

    public static <T> T withToolName(String toolName, Supplier<T> supplier) {
        String previous = TOOL_NAME.get();
        TOOL_NAME.set(toolName);
        try {
            return supplier.get();
        } finally {
            if (previous == null) {
                TOOL_NAME.remove();
            } else {
                TOOL_NAME.set(previous);
            }
        }
    }

    public static Optional<String> currentToolName() {
        String toolName = TOOL_NAME.get();
        if (toolName == null || toolName.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(toolName);
    }
}
