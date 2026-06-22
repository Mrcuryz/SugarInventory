package com.Laibin.SugarInventory.mcp.service;

import com.Laibin.SugarInventory.mcp.client.WarehouseApiException;
import com.Laibin.SugarInventory.mcp.model.ToolModels.ToolError;

import java.util.List;

public final class ErrorMapper {
    private ErrorMapper() {
    }

    public static ToolError invalid(String field, String message) {
        return new ToolError(
                "INVALID_ARGUMENT",
                message,
                "BLOCKING",
                field,
                false,
                List.of("Correct the input field and retry."),
                null
        );
    }

    public static ToolError upstream(WarehouseApiException exception) {
        return new ToolError(
                exception.code(),
                exception.getMessage(),
                exception.retryable() ? "ERROR" : "BLOCKING",
                null,
                exception.retryable(),
                suggestedActions(exception),
                exception.upstreamStatus()
        );
    }

    public static ToolError unexpected() {
        return new ToolError(
                "INTERNAL_ERROR",
                "MCP tool failed while processing the request",
                "ERROR",
                null,
                false,
                List.of("Retry later or inspect warehouse-mcp log file."),
                null
        );
    }

    private static List<String> suggestedActions(WarehouseApiException exception) {
        return switch (exception.code()) {
            case "UPSTREAM_UNAUTHORIZED" -> List.of("Refresh WAREHOUSE_API_TOKEN.", "Verify the token uses Bearer authentication.");
            case "UPSTREAM_PERMISSION_DENIED" -> List.of("Use a token with read permission for the mapped backend endpoint.");
            case "UPSTREAM_TIMEOUT", "UPSTREAM_SERVER_ERROR", "UPSTREAM_IO_ERROR" -> List.of("Retry after checking the warehouse backend health.");
            default -> List.of("Check backend availability and request filters.");
        };
    }
}

