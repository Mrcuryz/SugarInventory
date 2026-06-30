package com.Laibin.SugarInventory.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AgentToolAuditDTO {
    @NotBlank
    @Size(max = 100)
    private String toolName;

    @Size(max = 100)
    private String toolCallId;

    @Size(max = 500)
    private String upstreamPath;

    @Size(max = 1000)
    private String argumentsSummary;

    @Size(max = 1000)
    private String requestSummary;

    @Size(max = 1000)
    private String responseSummary;

    @Size(max = 40)
    private String resultCode;

    @Size(max = 80)
    private String errorCode;

    private Long durationMs;
}


