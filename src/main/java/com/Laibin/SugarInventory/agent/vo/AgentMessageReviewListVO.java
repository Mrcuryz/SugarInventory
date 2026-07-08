package com.Laibin.SugarInventory.agent.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class AgentMessageReviewListVO {
    private Long id;
    private LocalDateTime createdAt;
    private String userQuestionSummary;
    private String assistantAnswerSummary;
    private String answerStatus;
    private String confidenceLevel;
    private String failureDomain;
    private String failureCategory;
    private String suggestedFixType;
    private String intentType;
    private List<String> plannedTools = new ArrayList<>();
    private List<String> actualToolNames = new ArrayList<>();
    private String reviewStatus;
    private String testCaseStatus;
}
