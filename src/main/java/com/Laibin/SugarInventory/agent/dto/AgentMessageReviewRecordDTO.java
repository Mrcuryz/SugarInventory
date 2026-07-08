package com.Laibin.SugarInventory.agent.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

    @JsonAlias("intent_type")
    @Size(max = 80)
    private String intentType;

    @JsonAlias("intent_subtype")
    @Size(max = 120)
    private String intentSubtype;

    @JsonAlias("business_domain")
    @Size(max = 80)
    private String businessDomain;

    @JsonAlias("business_objects")
    private Map<String, Object> businessObjects;

    @JsonAlias("missing_slots")
    private List<@Size(max = 100) String> missingSlots = new ArrayList<>();

    @JsonAlias("support_status")
    @Size(max = 80)
    private String supportStatus;

    @JsonAlias("next_action")
    @Size(max = 80)
    private String nextAction;

    @JsonAlias("planned_tools")
    private List<@Size(max = 100) String> plannedTools = new ArrayList<>();

    @JsonAlias("actual_tools")
    private List<@Size(max = 100) String> actualToolNames = new ArrayList<>();

    private Boolean hasCards;

    @Size(max = 40)
    private String finishReason;
}
