package com.Laibin.SugarInventory.printerassistant.model;

import jakarta.validation.constraints.NotBlank;

public record PrintLabelRequest(
        @NotBlank(message = "code 不能为空")
        String code,
        String title
) {
}
