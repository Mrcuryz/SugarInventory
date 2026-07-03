package com.Laibin.SugarInventory.agent.python.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PythonAgentChatRequestDTO {
    private String agentSessionId;
    private String messageId;
    private UserSummary user;
    private List<String> scopes = new ArrayList<>();
    private Message message;
    private Map<String, Object> pageContext = new LinkedHashMap<>();
    private ClientContext client;

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class UserSummary {
        private Integer userId;
        private String name;
        private String roleCode;
        private List<String> permissionCodes = new ArrayList<>();
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Message {
        private String type;
        private String content;
        private Selection selection;
        private String interruptId;
        private String resumeToken;
        private String action;
        private String clientRequestId;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Selection {
        private String optionId;
        private String optionType;
        private String displayLabel;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ClientContext {
        private String traceId;
        private String requestId;
        private boolean debug;
    }
}
