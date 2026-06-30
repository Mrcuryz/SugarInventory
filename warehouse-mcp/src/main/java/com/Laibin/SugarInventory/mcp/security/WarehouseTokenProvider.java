package com.Laibin.SugarInventory.mcp.security;

import java.util.Optional;

public interface WarehouseTokenProvider {
    Optional<String> currentToken();

    Optional<String> agentSessionId();
}
