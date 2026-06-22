package com.Laibin.SugarInventory.printerassistant.service;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.print.PageFormat;
import java.awt.print.Paper;
import java.awt.print.Printable;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class PrintExecutionServiceTest {

    @Test
    void buildPrintPagesShouldExpandLabelsByCopies() {
        BufferedImage first = createImage(Color.RED);
        BufferedImage second = createImage(Color.BLUE);

        List<BufferedImage> pages = PrintExecutionService.buildPrintPages(List.of(first, second), 2);

        assertEquals(4, pages.size());
        assertSame(first, pages.get(0));
        assertSame(first, pages.get(1));
        assertSame(second, pages.get(2));
        assertSame(second, pages.get(3));
    }

    @Test
    void labelImagesPrintableShouldAcceptEachGlobalPageIndex() {
        PrintExecutionService.LabelImagesPrintable printable = new PrintExecutionService.LabelImagesPrintable(List.of(
                createImage(Color.RED),
                createImage(Color.BLUE)
        ));
        BufferedImage canvas = new BufferedImage(400, 300, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = canvas.createGraphics();
        try {
            assertEquals(Printable.PAGE_EXISTS, printable.print(graphics, createPageFormat(), 0));
            assertEquals(Printable.PAGE_EXISTS, printable.print(graphics, createPageFormat(), 1));
            assertEquals(Printable.NO_SUCH_PAGE, printable.print(graphics, createPageFormat(), 2));
        } finally {
            graphics.dispose();
        }
    }

    private static BufferedImage createImage(Color color) {
        BufferedImage image = new BufferedImage(120, 80, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(color);
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private static PageFormat createPageFormat() {
        PageFormat pageFormat = new PageFormat();
        Paper paper = new Paper();
        paper.setSize(400, 300);
        paper.setImageableArea(0, 0, 400, 300);
        pageFormat.setPaper(paper);
        return pageFormat;
    }
}