package com.Laibin.SugarInventory.printerassistant.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record PrintJobRequest(
        @NotBlank(message = "template 不能为空")
        String template,
        String printerName,
        @Min(value = 1, message = "copies 最少为 1")
        @Max(value = 20, message = "copies 最大为 20")
        Integer copies,
        @NotEmpty(message = "labels 不能为空")
        List<@Valid PrintLabelRequest> labels
) {

    public int safeCopies() {
        return copies == null ? 1 : copies;
    }
}
