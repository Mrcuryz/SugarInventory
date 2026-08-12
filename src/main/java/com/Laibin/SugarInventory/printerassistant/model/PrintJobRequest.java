package com.Laibin.SugarInventory.printerassistant.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record PrintJobRequest(
        @NotBlank(message = "template 不能为空")
        @Size(max = 64, message = "template 最长为 64 个字符")
        String template,
        @Size(max = 255, message = "printerName 最长为 255 个字符")
        String printerName,
        @Min(value = 1, message = "copies 最少为 1")
        @Max(value = 20, message = "copies 最大为 20")
        Integer copies,
        @NotEmpty(message = "labels 不能为空")
        @Size(max = 100, message = "单次最多打印 100 个标签")
        List<@Valid PrintLabelRequest> labels
) {

    private static final int MAXIMUM_PRINT_PAGES = 200;

    public int safeCopies() {
        return copies == null ? 1 : copies;
    }

    @AssertTrue(message = "单次打印总页数不能超过 200")
    public boolean isWithinPrintPageLimit() {
        return labels == null || (long) labels.size() * safeCopies() <= MAXIMUM_PRINT_PAGES;
    }
}
