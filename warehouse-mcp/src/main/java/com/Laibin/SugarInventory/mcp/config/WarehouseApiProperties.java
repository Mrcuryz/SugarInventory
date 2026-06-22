package com.Laibin.SugarInventory.mcp.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "warehouse.api")
public record WarehouseApiProperties(
        String baseUrl,
        String token,
        Duration timeout
) {
    public WarehouseApiProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "http://localhost:8080";
        }
        if (token == null) {
            token = "";
        }
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            timeout = Duration.ofSeconds(5);
        }
    }
}
