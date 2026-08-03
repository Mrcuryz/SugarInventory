package com.Laibin.SugarInventory.agent.python.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
public class PythonAgentChatResponseDTO {
    private String agentSessionId;
    private String answer;
    private boolean needsUserSelection;
    private List<BusinessCard> cards = new ArrayList<>();
    private List<String> suggestions = new ArrayList<>();
    private JsonNode reviewTrace;
    private JsonNode debug;
    private AgentError error;

    @Data
    public static class BusinessCard {
        private String cardType;
        private String title;
        private String prompt;
        private List<UserOption> options = new ArrayList<>();
        private List<Map<String, Object>> fields = new ArrayList<>();
    }

    @Data
    public static class UserOption {
        private String optionId;
        private String optionType;
        private String displayLabel;
        private String description;
        private boolean supported = true;
        private String disabledReason;
    }

    @Data
    public static class AgentError {
        private String code;
        private String message;
        private boolean retryable;
    }
}
