package com.Laibin.SugarInventory.agent.python.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class PythonAgentChatRequestDTO {
    private String agentSessionId;
    private UserSummary user;
    private List<String> scopes = new ArrayList<>();
    private Message message;
    private Map<String, Object> pageContext = new LinkedHashMap<>();
    private ClientContext client;

    @Data
    public static class UserSummary {
        private Integer userId;
        private String name;
        private String roleCode;
        private List<String> permissionCodes = new ArrayList<>();
    }

    @Data
    public static class Message {
        private String type;
        private String content;
        private Selection selection;
    }

    @Data
    public static class Selection {
        private String optionId;
        private String optionType;
        private String displayLabel;
    }

    @Data
    public static class ClientContext {
        private String traceId;
        private String requestId;
        private boolean debug;
    }
}