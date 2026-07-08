package com.Laibin.SugarInventory.agent.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AgentMessageReviewQueryDTO {
    @Min(1)
    private Integer page = 1;

    @Min(1)
    @Max(100)
    private Integer size = 20;

    @Size(max = 40)
    private String reviewStatus;

    @Size(max = 40)
    private String answerStatus;

    @Size(max = 80)
    private String failureDomain;

    @Size(max = 120)
    private String failureCategory;

    @Size(max = 80)
    private String suggestedFixType;

    @Size(max = 40)
    private String testCaseStatus;

    private Boolean priorityOnly;
}
