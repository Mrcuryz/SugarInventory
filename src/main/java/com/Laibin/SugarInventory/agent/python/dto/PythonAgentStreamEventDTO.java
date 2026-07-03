package com.Laibin.SugarInventory.agent.python.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

@Data
public class PythonAgentStreamEventDTO {
    private String eventId;
    private String messageId;
    private String agentSessionId;
    private String type;
    private Integer sequence;
    private JsonNode payload;
}
