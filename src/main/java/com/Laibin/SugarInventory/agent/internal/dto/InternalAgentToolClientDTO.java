package com.Laibin.SugarInventory.agent.internal.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = false)
public class InternalAgentToolClientDTO {
    @Size(max = 100)
    private String traceId;

    @Size(max = 100)
    private String requestId;

    @Size(max = 80)
    @Pattern(regexp = "[a-z][a-z0-9_]*")
    private String expertAgent;

    @Size(max = 80)
    @Pattern(regexp = "[a-z][a-z0-9_]*")
    private String businessDomain;

    @Size(max = 40)
    @Pattern(regexp = "[a-z][a-z0-9_]*")
    private String handoffMode;

    @Size(max = 100)
    private String handoffId;

    @Size(max = 100)
    private String planId;

    @Size(max = 100)
    @Pattern(regexp = "[a-z][a-z0-9_]*")
    private String stepId;

    @JsonAnySetter
    public void rejectUnknownField(String ignoredName, Object ignoredValue) {
        throw new IllegalArgumentException("Unknown client field.");
    }
}
