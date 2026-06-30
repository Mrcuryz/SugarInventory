package com.Laibin.SugarInventory.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

@Data
public class AgentMessageRequestDTO {
    @NotBlank
    @Size(min = 1, max = 1000)
    private String message;

    private Map<String, Object> pageContext;
}
