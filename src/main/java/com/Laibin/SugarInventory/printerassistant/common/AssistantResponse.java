package com.Laibin.SugarInventory.printerassistant.common;

public record AssistantResponse<T>(boolean success, String message, T data) {

    public static <T> AssistantResponse<T> success(T data, String message) {
        return new AssistantResponse<>(true, message, data);
    }

    public static <T> AssistantResponse<T> success(T data) {
        return success(data, "success");
    }

    public static <T> AssistantResponse<T> error(String message) {
        return new AssistantResponse<>(false, message, null);
    }
}
