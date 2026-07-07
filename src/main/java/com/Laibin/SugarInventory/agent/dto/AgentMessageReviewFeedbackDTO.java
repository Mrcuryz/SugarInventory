package com.Laibin.SugarInventory.agent.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AgentMessageReviewFeedbackDTO {
    @Size(max = 60)
    private String feedbackType;

    @Size(max = 1000)
    private String feedbackNote;

    @Size(max = 1000)
    private String expectedIntentSummary;

    @Size(max = 200)
    private String expectedCapability;

    private List<@Size(max = 100) String> expectedToolNames = new ArrayList<>();
}
