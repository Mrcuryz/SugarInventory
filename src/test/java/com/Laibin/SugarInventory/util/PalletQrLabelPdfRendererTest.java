package com.Laibin.SugarInventory.util;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PalletQrLabelPdfRendererTest {

    @Test
    void renderA4LabelsShouldCreatePrintablePdf() throws Exception {
        byte[] pdf = PalletQrLabelPdfRenderer.renderA4Labels(List.of("BT0008T5", "BT0009AA"));
        String header = new String(pdf, 0, 5, StandardCharsets.ISO_8859_1);
        String body = new String(pdf, StandardCharsets.ISO_8859_1);

        assertTrue(pdf.length > 1024);
        assertTrue(header.startsWith("%PDF-"));
        assertTrue(body.contains("/MediaBox [0 0 595.28 841.89]"));
        assertTrue(body.contains("/Subtype /Image"));
        assertTrue(body.endsWith("%%EOF\n"));
    }
}
