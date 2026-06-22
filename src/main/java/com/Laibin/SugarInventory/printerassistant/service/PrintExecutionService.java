package com.Laibin.SugarInventory.printerassistant.service;

import org.springframework.stereotype.Service;

import javax.print.PrintService;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.print.Book;
import java.awt.print.PageFormat;
import java.awt.print.Paper;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.util.ArrayList;
import java.util.List;

@Service
public class PrintExecutionService {

    private static final double POINTS_PER_MM = 72d / 25.4d;
    private static final double LABEL_WIDTH_MM = 80d;
    private static final double LABEL_HEIGHT_MM = 60d;

    private final PrinterDiscoveryService printerDiscoveryService;

    public PrintExecutionService(PrinterDiscoveryService printerDiscoveryService) {
        this.printerDiscoveryService = printerDiscoveryService;
    }

    public PrintResult print(List<BufferedImage> labelImages, String requestedPrinterName, int copies) {
        if (labelImages == null || labelImages.isEmpty()) {
            throw new IllegalArgumentException("打印图片不能为空");
        }
        int safeCopies = Math.max(copies, 1);
        PrintService printer = printerDiscoveryService.resolvePrinter(requestedPrinterName);
        try {
            PrinterJob job = PrinterJob.getPrinterJob();
            job.setPrintService(printer);
            PageFormat pageFormat = createLabelPageFormat(job);
            List<BufferedImage> printPages = buildPrintPages(labelImages, safeCopies);
            Book book = new Book();
            book.append(new LabelImagesPrintable(printPages), pageFormat, printPages.size());
            job.setPageable(book);
            job.print();
            return new PrintResult(printer.getName(), printPages.size());
        } catch (PrinterException e) {
            throw new IllegalStateException("提交打印任务失败，请检查打印机状态", e);
        }
    }

    static List<BufferedImage> buildPrintPages(List<BufferedImage> labelImages, int copies) {
        int safeCopies = Math.max(copies, 1);
        List<BufferedImage> printPages = new ArrayList<>(labelImages.size() * safeCopies);
        for (BufferedImage image : labelImages) {
            for (int i = 0; i < safeCopies; i++) {
                printPages.add(image);
            }
        }
        return printPages;
    }

    private static PageFormat createLabelPageFormat(PrinterJob job) {
        PageFormat pageFormat = job.defaultPage();
        pageFormat.setOrientation(PageFormat.PORTRAIT);
        Paper paper = new Paper();
        double width = LABEL_WIDTH_MM * POINTS_PER_MM;
        double height = LABEL_HEIGHT_MM * POINTS_PER_MM;
        paper.setSize(width, height);
        paper.setImageableArea(0, 0, width, height);
        pageFormat.setPaper(paper);
        return pageFormat;
    }

    public record PrintResult(String printerName, int printedCount) {
    }

    static final class LabelImagesPrintable implements Printable {

        private final List<BufferedImage> images;

        LabelImagesPrintable(List<BufferedImage> images) {
            this.images = images;
        }

        @Override
        public int print(Graphics graphics, PageFormat pageFormat, int pageIndex) {
            if (pageIndex < 0 || pageIndex >= images.size()) {
                return NO_SUCH_PAGE;
            }
            BufferedImage image = images.get(pageIndex);
            Graphics2D graphics2D = (Graphics2D) graphics.create();
            try {
                graphics2D.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                graphics2D.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                double availableWidth = pageFormat.getImageableWidth();
                double availableHeight = pageFormat.getImageableHeight();
                double scale = Math.min(availableWidth / image.getWidth(), availableHeight / image.getHeight());
                int drawWidth = (int) Math.round(image.getWidth() * scale);
                int drawHeight = (int) Math.round(image.getHeight() * scale);
                int drawX = (int) Math.round(pageFormat.getImageableX() + (availableWidth - drawWidth) / 2d);
                int drawY = (int) Math.round(pageFormat.getImageableY() + (availableHeight - drawHeight) / 2d);
                graphics2D.drawImage(image, drawX, drawY, drawWidth, drawHeight, null);
                return PAGE_EXISTS;
            } finally {
                graphics2D.dispose();
            }
        }
    }
}
