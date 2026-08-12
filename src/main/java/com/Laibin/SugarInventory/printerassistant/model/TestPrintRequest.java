package com.Laibin.SugarInventory.printerassistant.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record TestPrintRequest(
        @Size(max = 255, message = "printerName 最长为 255 个字符")
        String printerName,
        @Min(value = 1, message = "copies 最少为 1")
        @Max(value = 20, message = "copies 最大为 20")
        Integer copies
) {

    public int safeCopies() {
        return copies == null ? 1 : copies;
    }
}
