package com.Laibin.SugarInventory.printerassistant.model;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PrintJobRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsBoundedPrintJob() {
        PrintJobRequest request = new PrintJobRequest(
                "fixed_product_qrcode",
                null,
                2,
                List.of(new PrintLabelRequest("ABC123", "测试产品"))
        );

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void rejectsOversizedBatchTotalAndLabelFields() {
        List<PrintLabelRequest> tooManyLabels = Collections.nCopies(
                101,
                new PrintLabelRequest("ABC123", "测试产品")
        );
        PrintJobRequest oversizedBatch = new PrintJobRequest(
                "fixed_product_qrcode",
                null,
                2,
                tooManyLabels
        );
        PrintJobRequest oversizedPages = new PrintJobRequest(
                "fixed_product_qrcode",
                null,
                20,
                Collections.nCopies(11, new PrintLabelRequest("ABC123", "测试产品"))
        );
        PrintJobRequest oversizedCode = new PrintJobRequest(
                "fixed_product_qrcode",
                null,
                1,
                List.of(new PrintLabelRequest("A".repeat(129), "测试产品"))
        );

        assertThat(validator.validate(oversizedBatch)).anyMatch(v -> v.getMessage().contains("最多打印 100"));
        assertThat(validator.validate(oversizedPages)).anyMatch(v -> v.getMessage().contains("总页数不能超过 200"));
        assertThat(validator.validate(oversizedCode)).anyMatch(v -> v.getMessage().contains("最长为 128"));
    }
}
