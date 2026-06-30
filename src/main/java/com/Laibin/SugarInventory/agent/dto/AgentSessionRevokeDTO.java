package com.Laibin.SugarInventory.agent.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AgentSessionRevokeDTO {
    @Size(max = 200)
    private String revokedReason;
}
