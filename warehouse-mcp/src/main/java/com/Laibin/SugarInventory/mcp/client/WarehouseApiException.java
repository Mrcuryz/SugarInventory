package com.Laibin.SugarInventory.mcp.client;

public class WarehouseApiException extends RuntimeException {
    private final String code;
    private final Integer upstreamStatus;
    private final boolean retryable;

    public WarehouseApiException(String code, String message, Integer upstreamStatus, boolean retryable) {
        super(message);
        this.code = code;
        this.upstreamStatus = upstreamStatus;
        this.retryable = retryable;
    }

    public String code() {
        return code;
    }

    public Integer upstreamStatus() {
        return upstreamStatus;
    }

    public boolean retryable() {
        return retryable;
    }
}
