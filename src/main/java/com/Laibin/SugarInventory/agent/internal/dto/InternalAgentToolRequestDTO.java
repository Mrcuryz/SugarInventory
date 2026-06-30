package com.Laibin.SugarInventory.agent.internal.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = false)
public class InternalAgentToolRequestDTO {
    @NotBlank
    @Size(max = 64)
    private String agentSessionId;

    @NotBlank
    @Size(max = 100)
    private String toolCallId;

    @NotNull
    private Map<String, Object> arguments;

    @Valid
    private InternalAgentToolClientDTO client;
    @JsonAnySetter
    public void rejectUnknownField(String ignoredName, Object ignoredValue) {
        throw new IllegalArgumentException("Unknown request field.");
    }
}
