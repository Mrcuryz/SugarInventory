package com.Laibin.SugarInventory.agent.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

public final class AuditAgentVO {
    private AuditAgentVO() {}

    @Data @Builder
    public static class PageResult<T> {
        private String dataScope;
        private long total;
        private int page;
        private int size;
        private List<T> records;
        private List<String> limitations;
    }

    @Data @Builder
    public static class OperationLogRow {
        private String module;
        private String operationType;
        private String operator;
        private LocalDateTime operationTime;
        private List<String> changedFieldNames;
    }

    @Data @Builder
    public static class ToolAuditRow {
        private String capability;
        private String resultCode;
        private String errorCode;
        private Long durationMs;
        private LocalDateTime occurredAt;
    }

    @Data @Builder
    public static class AnswerReviewRow {
        private LocalDateTime createdAt;
        private String answerStatus;
        private String confidenceLevel;
        private String failureDomain;
        private String failureCategory;
        private String suggestedFixType;
        private String intentType;
        private String reviewStatus;
        private String testCaseStatus;
    }
}
