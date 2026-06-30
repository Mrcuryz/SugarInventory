package com.Laibin.SugarInventory.agent.model;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class AgentPlan {
    private AgentIntent intent = AgentIntent.UNSUPPORTED;
    private String entityQuery;
    private String palletCode;
    private String productionDate;
    private Map<String, Object> hints = new HashMap<>();
}
