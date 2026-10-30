package com.Laibin.SugarInventory.domain.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Human handoff uses a signed group reference, never a client-supplied member list. */
@Data
public class AssayGroupEntryReferencePreviewDTO {
    @NotBlank @Size(max = 500) private String groupRef;
    @NotNull private AssayEntryPreviewDTO values;
    @JsonIgnore private boolean unsupportedFields;
    @JsonAnySetter public void reject(String key, Object ignored) { unsupportedFields = true; }
}
