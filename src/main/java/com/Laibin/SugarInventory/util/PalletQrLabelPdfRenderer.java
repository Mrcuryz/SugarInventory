package com.Laibin.SugarInventory.util;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.FontFormatException;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.DeflaterOutputStream;

/**
 * Lightweight A4 PDF renderer for pallet QR labels.
 *
 * <p>The project does not currently depend on a PDF library. This renderer
 * writes a small PDF directly and embeds QR images as Flate-compressed RGB
 * streams, avoiding JPG compression so office printing stays crisp.</p>
 */
public final class PalletQrLabelPdfRenderer {

    private static final byte[] PDF_EOL = "\n".getBytes(StandardCharsets.ISO_8859_1);
    private static final float PAGE_WIDTH = 595.28f;
    private static final float PAGE_HEIGHT = 841.89f;
    private static final float POINT_PER_MM = 72f / 25.4f;
    private static final float LABEL_WIDTH = 80f * POINT_PER_MM;
    private static final float LABEL_HEIGHT = 60f * POINT_PER_MM;
    private static final int COLUMNS = 2;
    private static final int ROWS = 4;
    private static final int LABELS_PER_PAGE = COLUMNS * ROWS;
    private static final float GAP_X = 14f;
    private static final float GAP_Y = 14f;
    private static final float START_X = (PAGE_WIDTH - (COLUMNS * LABEL_WIDTH) - ((COLUMNS - 1) * GAP_X)) / 2f;
    private static final float START_Y = (PAGE_HEIGHT - (ROWS * LABEL_HEIGHT) - ((ROWS - 1) * GAP_Y)) / 2f;
    private static final float QR_SIZE = 120f;
    private static final int QR_IMAGE_SIZE = 512;
    private static final int LABEL_IMAGE_WIDTH = 900;
    private static final int LABEL_IMAGE_HEIGHT = 675;
    private static final String BUNDLED_CJK_FONT_RESOURCE = "/fonts/NotoSansSC-VF.ttf";
    private static final String CJK_FONT_SAMPLE = "固定产品二维码黄中冰白砂糖";
    private static volatile Font bundledCjkBaseFont;

    private PalletQrLabelPdfRenderer() {
    }

    public record LabelPayload(String code, String title) {
    }

    public static byte[] renderA4Labels(List<String> codes) throws IOException {
        if (codes == null || codes.isEmpty()) {
            throw new IllegalArgumentException("托盘码不能为空");
        }

        PdfDocument document = new PdfDocument();
        List<Integer> pageIds = new ArrayList<>();

        int index = 0;
        while (index < codes.size()) {
            int end = Math.min(index + LABELS_PER_PAGE, codes.size());
            List<String> pageCodes = codes.subList(index, end);
            pageIds.add(renderPage(document, pageCodes));
            index = end;
        }

        document.putObject(1, "<< /Type /Catalog /Pages 2 0 R >>");
        StringBuilder kids = new StringBuilder();
        for (Integer pageId : pageIds) {
            kids.append(pageId).append(" 0 R ");
        }
        document.putObject(2, "<< /Type /Pages /Kids [ " + kids + "] /Count " + pageIds.size() + " >>");
        document.putObject(3, "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold >>");

        return document.write();
    }

    public static byte[] renderA4LabelsWithTitle(List<LabelPayload> labels) throws IOException {
        if (labels == null || labels.isEmpty()) {
            throw new IllegalArgumentException("二维码标签列表不能为空");
        }

        PdfDocument document = new PdfDocument();
        List<Integer> pageIds = new ArrayList<>();

        int index = 0;
        while (index < labels.size()) {
            int end = Math.min(index + LABELS_PER_PAGE, labels.size());
            List<LabelPayload> pageLabels = labels.subList(index, end);
            pageIds.add(renderPageWithTitle(document, pageLabels));
            index = end;
        }

        document.putObject(1, "<< /Type /Catalog /Pages 2 0 R >>");
        StringBuilder kids = new StringBuilder();
        for (Integer pageId : pageIds) {
            kids.append(pageId).append(" 0 R ");
        }
        document.putObject(2, "<< /Type /Pages /Kids [ " + kids + "] /Count " + pageIds.size() + " >>");

        return document.write();
    }

    private static int renderPage(PdfDocument document, List<String> codes) throws IOException {
        int pageObjectId = document.nextObjectId();
        int contentObjectId = document.nextObjectId();

        List<ImageRef> images = new ArrayList<>();
        StringBuilder content = new StringBuilder();
        for (int i = 0; i < codes.size(); i++) {
            String code = codes.get(i);
            int imageObjectId = document.nextObjectId();
            BufferedImage qrImage = QrCodeUtils.generateQrCode(code, QR_IMAGE_SIZE, QR_IMAGE_SIZE);
            document.putObject(imageObjectId, buildImageObject(qrImage));
            images.add(new ImageRef(imageObjectId, "/Im" + imageObjectId));

            int row = i / COLUMNS;
            int column = i % COLUMNS;
            float x = START_X + column * (LABEL_WIDTH + GAP_X);
            float y = PAGE_HEIGHT - START_Y - LABEL_HEIGHT - row * (LABEL_HEIGHT + GAP_Y);
            appendLabelContent(content, code, images.get(images.size() - 1).name(), x, y);
        }

        byte[] contentBytes = content.toString().getBytes(StandardCharsets.ISO_8859_1);
        document.putObject(contentObjectId, streamObject(
                "<< /Length " + contentBytes.length + " >>",
                contentBytes
        ));

        StringBuilder xObject = new StringBuilder();
        for (ImageRef image : images) {
            xObject.append(image.name()).append(' ').append(image.objectId()).append(" 0 R ");
        }
        document.putObject(pageObjectId,
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 " + format(PAGE_WIDTH) + ' ' + format(PAGE_HEIGHT) + "] "
                        + "/Resources << /Font << /F1 3 0 R >> /XObject << " + xObject + ">> >> "
                        + "/Contents " + contentObjectId + " 0 R >>"
        );
        return pageObjectId;
    }

    private static int renderPageWithTitle(PdfDocument document, List<LabelPayload> labels) throws IOException {
        int pageObjectId = document.nextObjectId();
        int contentObjectId = document.nextObjectId();

        List<ImageRef> images = new ArrayList<>();
        StringBuilder content = new StringBuilder();
        for (int i = 0; i < labels.size(); i++) {
            LabelPayload label = labels.get(i);
            int imageObjectId = document.nextObjectId();
            BufferedImage labelImage = createLabelImage(label.code(), label.title());
            document.putObject(imageObjectId, buildImageObject(labelImage));
            images.add(new ImageRef(imageObjectId, "/Im" + imageObjectId));

            int row = i / COLUMNS;
            int column = i % COLUMNS;
            float x = START_X + column * (LABEL_WIDTH + GAP_X);
            float y = PAGE_HEIGHT - START_Y - LABEL_HEIGHT - row * (LABEL_HEIGHT + GAP_Y);
            appendImageLabelContent(content, images.get(images.size() - 1).name(), x, y);
        }

        byte[] contentBytes = content.toString().getBytes(StandardCharsets.ISO_8859_1);
        document.putObject(contentObjectId, streamObject(
                "<< /Length " + contentBytes.length + " >>",
                contentBytes
        ));

        StringBuilder xObject = new StringBuilder();
        for (ImageRef image : images) {
            xObject.append(image.name()).append(' ').append(image.objectId()).append(" 0 R ");
        }
        document.putObject(pageObjectId,
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 " + format(PAGE_WIDTH) + ' ' + format(PAGE_HEIGHT) + "] "
                        + "/Resources << /XObject << " + xObject + ">> >> "
                        + "/Contents " + contentObjectId + " 0 R >>"
        );
        return pageObjectId;
    }

    public static BufferedImage renderLabelImage(String code, String title) {
        return createLabelImage(code, title);
    }

    private static BufferedImage createLabelImage(String code, String title) {
        BufferedImage canvas = new BufferedImage(LABEL_IMAGE_WIDTH, LABEL_IMAGE_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = canvas.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, LABEL_IMAGE_WIDTH, LABEL_IMAGE_HEIGHT);

            graphics.setColor(new Color(214, 221, 235));
            graphics.setStroke(new BasicStroke(4f));
            graphics.drawRect(2, 2, LABEL_IMAGE_WIDTH - 4, LABEL_IMAGE_HEIGHT - 4);

            Font titleFont = pickFont(Font.BOLD, 42);
            Font codeFont = new Font("SansSerif", Font.BOLD, 34);
            if (title != null && !title.isBlank()) {
                drawCenteredText(graphics, title.trim(), titleFont, 70);
            }

            BufferedImage qrImage = QrCodeUtils.generateQrCode(code, 420, 420);
            int qrX = (LABEL_IMAGE_WIDTH - qrImage.getWidth()) / 2;
            int qrY = 120;
            graphics.drawImage(qrImage, qrX, qrY, null);

            drawCenteredText(graphics, code, codeFont, LABEL_IMAGE_HEIGHT - 58);
        } finally {
            graphics.dispose();
        }
        return canvas;
    }

    private static void drawCenteredText(Graphics2D graphics, String text, Font font, int baselineY) {
        graphics.setFont(font);
        FontMetrics metrics = graphics.getFontMetrics(font);
        int x = Math.max((LABEL_IMAGE_WIDTH - metrics.stringWidth(text)) / 2, 24);
        graphics.setColor(new Color(20, 30, 48));
        graphics.drawString(text, x, baselineY);
    }

    static Font pickFont(int style, int size) {
        Font bundledFont = loadBundledCjkFont();
        if (canDisplayCjk(bundledFont)) {
            return bundledFont.deriveFont(style, (float) size);
        }

        String[] candidates = {"Microsoft YaHei", "SimHei", "SimSun", "SansSerif"};
        for (String candidate : candidates) {
            Font font = new Font(candidate, style, size);
            if (canDisplayCjk(font)) {
                return font;
            }
        }
        return new Font("SansSerif", style, size);
    }

    private static Font loadBundledCjkFont() {
        Font cached = bundledCjkBaseFont;
        if (cached != null) {
            return cached;
        }
        synchronized (PalletQrLabelPdfRenderer.class) {
            if (bundledCjkBaseFont != null) {
                return bundledCjkBaseFont;
            }
            try (InputStream inputStream = PalletQrLabelPdfRenderer.class.getResourceAsStream(BUNDLED_CJK_FONT_RESOURCE)) {
                if (inputStream == null) {
                    return null;
                }
                bundledCjkBaseFont = Font.createFont(Font.TRUETYPE_FONT, inputStream);
                return bundledCjkBaseFont;
            } catch (FontFormatException | IOException | RuntimeException e) {
                return null;
            }
        }
    }

    private static boolean canDisplayCjk(Font font) {
        return font != null && font.canDisplayUpTo(CJK_FONT_SAMPLE) == -1;
    }

    private static byte[] buildImageObject(BufferedImage image) throws IOException {
        ByteArrayOutputStream rgb = new ByteArrayOutputStream(image.getWidth() * image.getHeight() * 3);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int color = image.getRGB(x, y);
                rgb.write((color >> 16) & 0xFF);
                rgb.write((color >> 8) & 0xFF);
                rgb.write(color & 0xFF);
            }
        }

        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (DeflaterOutputStream deflater = new DeflaterOutputStream(compressed)) {
            rgb.writeTo(deflater);
        }

        String dictionary = "<< /Type /XObject /Subtype /Image /Width " + image.getWidth()
                + " /Height " + image.getHeight()
                + " /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /FlateDecode /Length "
                + compressed.size()
                + " >>";
        return streamObject(dictionary, compressed.toByteArray());
    }

    private static void appendLabelContent(StringBuilder content, String code, String imageName, float x, float y) {
        float qrX = x + (LABEL_WIDTH - QR_SIZE) / 2f;
        float qrY = y + 34f;
        float codeX = x + (LABEL_WIDTH / 2f) - (code.length() * 4.8f);
        float codeY = y + 16f;

        content.append("q\n");
        content.append("1 1 1 rg ").append(format(x)).append(' ').append(format(y)).append(' ')
                .append(format(LABEL_WIDTH)).append(' ').append(format(LABEL_HEIGHT)).append(" re f\n");
        content.append("0.82 0.86 0.92 RG 0.8 w ").append(format(x)).append(' ').append(format(y)).append(' ')
                .append(format(LABEL_WIDTH)).append(' ').append(format(LABEL_HEIGHT)).append(" re S\n");
        content.append(format(QR_SIZE)).append(" 0 0 ").append(format(QR_SIZE)).append(' ')
                .append(format(qrX)).append(' ').append(format(qrY)).append(" cm ")
                .append(imageName).append(" Do\n");
        content.append("Q\n");
        content.append("BT /F1 15 Tf 0.08 0.12 0.18 rg ")
                .append(format(codeX)).append(' ').append(format(codeY))
                .append(" Td (").append(escapePdfText(code)).append(") Tj ET\n");
    }

    private static void appendImageLabelContent(StringBuilder content, String imageName, float x, float y) {
        content.append("q\n");
        content.append(format(LABEL_WIDTH)).append(" 0 0 ").append(format(LABEL_HEIGHT)).append(' ')
                .append(format(x)).append(' ').append(format(y)).append(" cm ")
                .append(imageName).append(" Do\n");
        content.append("Q\n");
    }

    private static byte[] streamObject(String dictionary, byte[] stream) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(dictionary.getBytes(StandardCharsets.ISO_8859_1));
        out.write(PDF_EOL);
        out.write("stream".getBytes(StandardCharsets.ISO_8859_1));
        out.write(PDF_EOL);
        out.write(stream);
        out.write(PDF_EOL);
        out.write("endstream".getBytes(StandardCharsets.ISO_8859_1));
        return out.toByteArray();
    }

    private static String escapePdfText(String text) {
        return text.replace("\\", "\\\\")
                .replace("(", "\\(")
                .replace(")", "\\)");
    }

    private static String format(float value) {
        return String.format(Locale.US, "%.2f", value);
    }

    private record ImageRef(int objectId, String name) {
    }

    private static final class PdfDocument {
        private final Map<Integer, byte[]> objects = new HashMap<>();
        private int nextObjectId = 4;

        private int nextObjectId() {
            return nextObjectId++;
        }

        private void putObject(int id, String content) {
            putObject(id, content.getBytes(StandardCharsets.ISO_8859_1));
        }

        private void putObject(int id, byte[] content) {
            objects.put(id, content);
            nextObjectId = Math.max(nextObjectId, id + 1);
        }

        private byte[] write() throws IOException {
            int maxId = objects.keySet().stream().mapToInt(Integer::intValue).max().orElse(0);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            List<Integer> offsets = new ArrayList<>();

            out.write("%PDF-1.4\n".getBytes(StandardCharsets.ISO_8859_1));
            out.write("%\u00E2\u00E3\u00CF\u00D3\n".getBytes(StandardCharsets.ISO_8859_1));
            offsets.add(0);
            for (int id = 1; id <= maxId; id++) {
                byte[] content = objects.get(id);
                if (content == null) {
                    offsets.add(0);
                    continue;
                }
                offsets.add(out.size());
                out.write((id + " 0 obj\n").getBytes(StandardCharsets.ISO_8859_1));
                out.write(content);
                out.write(PDF_EOL);
                out.write("endobj\n".getBytes(StandardCharsets.ISO_8859_1));
            }

            int xrefOffset = out.size();
            out.write(("xref\n0 " + (maxId + 1) + "\n").getBytes(StandardCharsets.ISO_8859_1));
            out.write("0000000000 65535 f \n".getBytes(StandardCharsets.ISO_8859_1));
            for (int id = 1; id <= maxId; id++) {
                int offset = offsets.get(id);
                if (offset == 0) {
                    out.write("0000000000 65535 f \n".getBytes(StandardCharsets.ISO_8859_1));
                } else {
                    out.write(String.format(Locale.US, "%010d 00000 n \n", offset).getBytes(StandardCharsets.ISO_8859_1));
                }
            }
            out.write(("trailer\n<< /Size " + (maxId + 1) + " /Root 1 0 R >>\nstartxref\n"
                    + xrefOffset + "\n%%EOF\n").getBytes(StandardCharsets.ISO_8859_1));
            return out.toByteArray();
        }
    }
}
