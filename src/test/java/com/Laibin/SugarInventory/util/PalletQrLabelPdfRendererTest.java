package com.Laibin.SugarInventory.util;

import org.junit.jupiter.api.Test;

import java.awt.Font;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

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

    @Test
    void renderA4LabelsWithTitleShouldCreatePrintablePdf() throws Exception {
        byte[] pdf = PalletQrLabelPdfRenderer.renderA4LabelsWithTitle(List.of(
                new PalletQrLabelPdfRenderer.LabelPayload("BT0008T5", "黄中冰"),
                new PalletQrLabelPdfRenderer.LabelPayload("BT0009AA", "白中冰")
        ));
        String header = new String(pdf, 0, 5, StandardCharsets.ISO_8859_1);
        String body = new String(pdf, StandardCharsets.ISO_8859_1);

        assertTrue(pdf.length > 1024);
        assertTrue(header.startsWith("%PDF-"));
        assertTrue(body.contains("/MediaBox [0 0 595.28 841.89]"));
        assertTrue(body.contains("/Subtype /Image"));
        assertTrue(body.endsWith("%%EOF\n"));
    }

    @Test
    void titleFontShouldUseBundledChineseFont() {
        Font font = PalletQrLabelPdfRenderer.pickFont(Font.BOLD, 42);

        assertTrue(font.canDisplayUpTo("黄中冰白砂糖") == -1);
        assertTrue(font.getFontName(Locale.ENGLISH).contains("Noto Sans SC"));
    }
}
