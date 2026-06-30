package com.Laibin.SugarInventory.agent.internal.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = false)
public class InternalAgentToolClientDTO {
    @Size(max = 100)
    private String traceId;

    @Size(max = 100)
    private String requestId;
    @JsonAnySetter
    public void rejectUnknownField(String ignoredName, Object ignoredValue) {
        throw new IllegalArgumentException("Unknown client field.");
    }
}
