package com.Laibin.SugarInventory.mcp.security;

import com.Laibin.SugarInventory.mcp.config.WarehouseApiProperties;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class EnvironmentWarehouseTokenProvider implements WarehouseTokenProvider {
    private final WarehouseApiProperties properties;

    public EnvironmentWarehouseTokenProvider(WarehouseApiProperties properties) {
        this.properties = properties;
    }

    @Override
    public Optional<String> currentToken() {
        if (properties.delegatedToken() != null && !properties.delegatedToken().isBlank()) {
            return Optional.of(properties.delegatedToken().trim());
        }
        if (properties.token() != null && !properties.token().isBlank()) {
            return Optional.of(properties.token().trim());
        }
        return Optional.empty();
    }

    @Override
    public Optional<String> agentSessionId() {
        if (properties.agentSessionId() == null || properties.agentSessionId().isBlank()) {
            return Optional.empty();
        }
        return Optional.of(properties.agentSessionId().trim());
    }
}
