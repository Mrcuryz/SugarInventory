package com.Laibin.SugarInventory.domain.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Human UI handoff: signed product reference plus values, never a model-generated product id. */
@Data
public class AssayEntryReferencePreviewDTO {
    @NotBlank @Size(max = 500) private String productRef;
    @NotNull private AssayEntryPreviewDTO values;
    @JsonIgnore private boolean unsupportedFields;
    @JsonAnySetter public void reject(String key, Object ignored) { unsupportedFields = true; }
}
