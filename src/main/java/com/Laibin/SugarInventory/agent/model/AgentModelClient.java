package com.Laibin.SugarInventory.agent.model;

import java.util.Map;

public interface AgentModelClient {
    AgentPlan plan(String message, Map<String, Object> pageContext);
}
