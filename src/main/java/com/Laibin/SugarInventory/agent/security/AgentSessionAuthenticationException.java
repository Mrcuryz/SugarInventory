package com.Laibin.SugarInventory.agent.security;

import lombok.Getter;

@Getter
public class AgentSessionAuthenticationException extends RuntimeException {
    private final int status;
    private final String errorCode;

    public AgentSessionAuthenticationException(int status, String errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }
}
