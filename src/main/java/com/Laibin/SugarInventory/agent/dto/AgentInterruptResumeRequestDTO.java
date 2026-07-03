package com.Laibin.SugarInventory.agent.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AgentInterruptResumeRequestDTO {
    @NotBlank
    @Size(max = 200)
    private String resumeToken;

    @NotBlank
    @Pattern(regexp = "SELECT_OPTION|CANCEL|APPROVE|REJECT|MODIFY|REQUEST_REPREVIEW")
    private String action;

    @Size(max = 100)
    private String clientRequestId;

    @Valid
    private Selection selection;

    @Data
    public static class Selection {
        @Size(max = 100)
        private String optionId;

        @Size(max = 100)
        private String previewId;
    }
}
