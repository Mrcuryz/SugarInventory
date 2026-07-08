package com.Laibin.SugarInventory.agent.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AgentMessageReviewStatusUpdateDTO {
    @Size(max = 40)
    private String reviewStatus;

    @Size(max = 40)
    private String testCaseStatus;

    @Size(max = 1000)
    private String adminNote;
}
