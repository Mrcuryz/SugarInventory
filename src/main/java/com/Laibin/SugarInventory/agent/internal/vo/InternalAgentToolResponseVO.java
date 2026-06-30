package com.Laibin.SugarInventory.agent.internal.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.ALWAYS)
public class InternalAgentToolResponseVO {
    private String toolName;
    private String toolCallId;
    private String status;
    private JsonNode result;
    private InternalAgentToolErrorVO error;
    private String auditRef;

    public static InternalAgentToolResponseVO success(String toolName, String toolCallId, JsonNode result, String auditRef) {
        return new InternalAgentToolResponseVO(toolName, toolCallId, "SUCCESS", result, null, auditRef);
    }

    public static InternalAgentToolResponseVO error(String toolName, String toolCallId,
                                                    InternalAgentToolErrorVO error, String auditRef) {
        return new InternalAgentToolResponseVO(toolName, toolCallId, "ERROR", null, error, auditRef);
    }
}
