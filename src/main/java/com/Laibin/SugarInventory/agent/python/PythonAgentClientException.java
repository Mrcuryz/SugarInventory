package com.Laibin.SugarInventory.agent.python;

public class PythonAgentClientException extends RuntimeException {
    private final String code;
    private final boolean retryable;

    public PythonAgentClientException(String code, boolean retryable) {
        super(code);
        this.code = code;
        this.retryable = retryable;
    }

    public String getCode() {
        return code;
    }

    public boolean isRetryable() {
        return retryable;
    }
}