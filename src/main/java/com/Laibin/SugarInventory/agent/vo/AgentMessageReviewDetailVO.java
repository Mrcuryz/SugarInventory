package com.Laibin.SugarInventory.agent.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class AgentMessageReviewDetailVO {
    private Long id;
    private LocalDateTime createdAt;
    private String agentSessionId;
    private String messageId;
    private String userQuestion;
    private String assistantAnswerTextSafe;
    private String assistantAnswerSummary;
    private String answerTraceSummary;
    private Map<String, Object> agentDecisionSnapshot = new LinkedHashMap<>();
    private String answerStatus;
    private String confidenceLevel;
    private String failureDomain;
    private String failureCategory;
    private String suggestedFixType;
    private String reviewStatus;
    private String testCaseStatus;
    private String userFeedbackType;
    private String userFeedbackNote;
    private String expectedIntentSummary;
    private String actualIntentSummary;
    private List<String> plannedTools = new ArrayList<>();
    private List<String> actualToolNames = new ArrayList<>();
    private String adminNote;
    private List<AgentMessageReviewEvidenceVO> evidenceSummary = new ArrayList<>();
}
