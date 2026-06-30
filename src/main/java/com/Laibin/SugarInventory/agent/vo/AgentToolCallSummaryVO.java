package com.Laibin.SugarInventory.agent.vo;

import lombok.Data;

@Data
public class AgentToolCallSummaryVO {
    private String toolName;

    private String upstreamPath;
    private String resultCode;
    private String errorCode;
    private Long durationMs;
    private String requestSummary;
    private String responseSummary;
}


