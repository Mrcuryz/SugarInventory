package com.Laibin.SugarInventory.agent.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class AgentSessionCreateDTO {
    @Size(max = 40)
    private String clientType;

    @Size(max = 40)
    private String mcpTransport;

    @Size(max = 20)
    private List<@Size(min = 1, max = 80) String> requestedScopes;
}
