package com.Laibin.SugarInventory.agent.internal.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InternalAgentToolErrorVO {
    private String code;
    private String message;
    private String severity;
    private String field;
    private boolean retryable;
    private List<String> suggestedActions;
    private Integer upstreamStatus;
}
