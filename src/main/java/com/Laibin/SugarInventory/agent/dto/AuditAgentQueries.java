package com.Laibin.SugarInventory.agent.dto;

import lombok.Data;

import java.time.LocalDateTime;

public final class AuditAgentQueries {
    private AuditAgentQueries() {}

    @Data
    public static class OperationLogs {
        private String module;
        private String operationType;
        private String operator;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private Integer page = 1;
        private Integer size = 20;
    }

    @Data
    public static class ToolAudit {
        private String capability;
        private String resultCode;
        private String errorCode;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private Integer page = 1;
        private Integer size = 20;
    }

    @Data
    public static class AnswerReviews {
        private String reviewStatus;
        private String answerStatus;
        private String failureDomain;
        private String failureCategory;
        private String suggestedFixType;
        private String testCaseStatus;
        private Boolean priorityOnly;
        private Integer page = 1;
        private Integer size = 20;
    }
}
