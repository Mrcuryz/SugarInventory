package com.Laibin.SugarInventory.mcp.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

@ConfigurationProperties(prefix = "warehouse.api")
public record WarehouseApiProperties(
        String baseUrl,
        String token,
        String delegatedToken,
        String agentSessionId,
        Duration timeout
) {
    public WarehouseApiProperties(String baseUrl, String token, Duration timeout) {
        this(baseUrl, token, "", "", timeout);
    }

    @ConstructorBinding
    public WarehouseApiProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "http://localhost:8080";
        }
        if (token == null) {
            token = "";
        }
        if (delegatedToken == null) {
            delegatedToken = "";
        }
        if (agentSessionId == null) {
            agentSessionId = "";
        }
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            timeout = Duration.ofSeconds(5);
        }
    }
}
