package com.Laibin.SugarInventory.agent.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AgentMessageReviewRecordDTO {
    @Size(max = 100)
    private String messageId;

    @Size(max = 1000)
    private String userQuestion;

    @Size(max = 10000)
    private String assistantAnswerTextSafe;

    @Size(max = 1000)
    private String assistantAnswerSummary;

    @Size(max = 255)
    private String pagePath;

    @Size(max = 40)
    private String answerStatus;

    @Size(max = 20)
    private String confidenceLevel;

    @Size(max = 1000)
    private String actualIntentSummary;

    private List<@Size(max = 100) String> actualToolNames = new ArrayList<>();

    private Boolean hasCards;

    @Size(max = 40)
    private String finishReason;
}
