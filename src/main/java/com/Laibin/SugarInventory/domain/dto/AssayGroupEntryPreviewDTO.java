package com.Laibin.SugarInventory.domain.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/** One measured sample applies to the actual members of one existing assay group. */
@Data
public class AssayGroupEntryPreviewDTO {
    @NotNull @Positive
    private Integer groupId;
    // Product identity is resolved from group membership; nested productId is forbidden.
    @NotNull
    private AssayEntryPreviewDTO values;
    @JsonIgnore
    private boolean unsupportedFields;
    @JsonAnySetter
    public void reject(String field, Object value) { unsupportedFields = true; }
}
