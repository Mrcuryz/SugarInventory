package com.Laibin.SugarInventory.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_message_review")
public class AgentMessageReview {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private String agentSessionId;
    private String messageId;
    private Integer userId;
    private Integer conversationTurnIndex;
    private String pagePath;
    private String userQuestion;
    private String assistantAnswerTextSafe;
    private String assistantAnswerSummary;
    private String answerStatus;
    private String confidenceLevel;
    private String failureDomain;
    private String failureCategory;
    private String expectedIntentSummary;
    private String actualIntentSummary;
    private String expectedToolNames;
    private String actualToolNames;
    private String expectedCapability;
    private String suggestedFixType;
    private String suggestedToolName;
    private String suggestedBackendEndpoint;
    private String testCaseStatus;
    private String reviewSource;
    private String reviewStatus;
    private String severity;
    private String userFeedbackType;
    private String userFeedbackNote;
    private Integer reviewedBy;
    private String reviewNote;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
