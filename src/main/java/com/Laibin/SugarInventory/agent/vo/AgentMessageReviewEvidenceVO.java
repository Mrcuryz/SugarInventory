package com.Laibin.SugarInventory.agent.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AgentMessageReviewEvidenceVO {
    private Long id;
    private String evidenceType;
    private String refId;
    private String evidenceSummary;
    private LocalDateTime createdAt;
}
