package com.Laibin.SugarInventory.printerassistant.model;

import jakarta.validation.constraints.Size;

public record DefaultPrinterRequest(
        @Size(max = 255, message = "printerName 最长为 255 个字符")
        String printerName
) {
}
