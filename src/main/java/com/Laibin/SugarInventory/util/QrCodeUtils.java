package com.Laibin.SugarInventory.util;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

public class QrCodeUtils {

    public static BufferedImage generateQrCode(String content, int width, int height) {
        BitMatrix matrix = generateQrCodeMatrix(content, width, height);
        int matrixWidth = matrix.getWidth();
        int matrixHeight = matrix.getHeight();
        BufferedImage image = new BufferedImage(matrixWidth, matrixHeight, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < matrixWidth; x++) {
            for (int y = 0; y < matrixHeight; y++) {
                image.setRGB(x, y, matrix.get(x, y) ? 0xFF000000 : 0xFFFFFFFF);
            }
        }
        return image;
    }

    public static BitMatrix generateQrCodeMatrix(String content, int width, int height) {
        if (content == null || content.trim().isEmpty()) {
            throw new IllegalArgumentException("编码不能为空");
        }
        try {
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(EncodeHintType.MARGIN, 1);

            QRCodeWriter writer = new QRCodeWriter();
            return writer.encode(content.trim(), BarcodeFormat.QR_CODE, width, height, hints);
        } catch (WriterException e) {
            throw new RuntimeException("生成二维码失败", e);
        }
    }

    public static String generateQrCodeSvg(String content, int width, int height) {
        BitMatrix matrix = generateQrCodeMatrix(content, width, height);
        StringBuilder svg = new StringBuilder();
        svg.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        svg.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"")
                .append(width)
                .append("\" height=\"")
                .append(height)
                .append("\" viewBox=\"0 0 ")
                .append(width)
                .append(' ')
                .append(height)
                .append("\">\n");
        svg.append("<rect width=\"100%\" height=\"100%\" fill=\"#fff\"/>\n");
        svg.append("<path fill=\"#000\" d=\"");
        for (int y = 0; y < matrix.getHeight(); y++) {
            for (int x = 0; x < matrix.getWidth(); x++) {
                if (matrix.get(x, y)) {
                    svg.append('M').append(x).append(' ').append(y).append("h1v1h-1z");
                }
            }
        }
        svg.append("\"/>\n</svg>\n");
        return svg.toString();
    }
}

