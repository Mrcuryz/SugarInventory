package com.Laibin.SugarInventory.printerassistant.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PrintLabelRequest(
        @NotBlank(message = "code 不能为空")
        @Size(max = 128, message = "code 最长为 128 个字符")
        String code,
        @Size(max = 100, message = "title 最长为 100 个字符")
        String title
) {
}
