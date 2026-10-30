package com.Laibin.SugarInventory.printerassistant.service;

import com.Laibin.SugarInventory.printerassistant.model.PrintLabelRequest;
import com.Laibin.SugarInventory.util.PalletQrLabelPdfRenderer;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.awt.image.BufferedImage;
import java.util.List;

@Service
public class LabelRenderService {

    public static final String FIXED_PRODUCT_QRCODE_TEMPLATE = "fixed_product_qrcode";
    public static final String WAREHOUSE_LOCATION_QRCODE_TEMPLATE = "warehouse_location_qrcode";

    public List<BufferedImage> renderLabels(String template, List<PrintLabelRequest> labels) {
        if (!FIXED_PRODUCT_QRCODE_TEMPLATE.equals(template)
                && !WAREHOUSE_LOCATION_QRCODE_TEMPLATE.equals(template)) {
            throw new IllegalArgumentException("暂不支持的标签模板: " + template);
        }
        if (labels == null || labels.isEmpty()) {
            throw new IllegalArgumentException("打印标签不能为空");
        }
        if (WAREHOUSE_LOCATION_QRCODE_TEMPLATE.equals(template)) {
            return labels.stream()
                    .map(label -> {
                        String warehouseName = normalizeTitle(label.title());
                        return PalletQrLabelPdfRenderer.renderLabelImage(
                                normalizeCode(label.code()),
                                "库位 " + warehouseName,
                                warehouseName
                        );
                    })
                    .toList();
        }
        return labels.stream()
                .map(label -> PalletQrLabelPdfRenderer.renderLabelImage(
                        normalizeCode(label.code()),
                        normalizeTitle(label.title())
                ))
                .toList();
    }

    public BufferedImage renderTestLabel() {
        return PalletQrLabelPdfRenderer.renderLabelImage("TEST0001", "标签打印测试");
    }

    private static String normalizeCode(String code) {
        if (!StringUtils.hasText(code)) {
            throw new IllegalArgumentException("标签编码不能为空");
        }
        return code.trim().toUpperCase();
    }

    private static String normalizeTitle(String title) {
        return StringUtils.hasText(title) ? title.trim() : "";
    }
}
