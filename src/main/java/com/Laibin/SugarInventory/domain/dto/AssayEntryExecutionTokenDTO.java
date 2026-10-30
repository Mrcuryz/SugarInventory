package com.Laibin.SugarInventory.domain.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class AssayEntryExecutionTokenDTO {
    @NotBlank @Pattern(regexp = "aet1_[A-Za-z0-9_-]{43}")
    private String executionToken;
    @NotBlank @Pattern(regexp = "aei1_[A-Za-z0-9_-]{43}")
    private String idempotencyKey;
    @JsonIgnore
    private boolean unsupportedFields;
    @JsonAnySetter
    public void rejectAdditionalInput(String field, Object ignored) { unsupportedFields = true; }
}
