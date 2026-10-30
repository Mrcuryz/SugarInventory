package com.Laibin.SugarInventory.printerassistant.service;

import com.Laibin.SugarInventory.printerassistant.model.PrintLabelRequest;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LabelRenderServiceTest {

    private final LabelRenderService labelRenderService = new LabelRenderService();

    @Test
    void renderLabelsShouldCreateFixedTemplateImages() {
        List<BufferedImage> images = labelRenderService.renderLabels(
                LabelRenderService.FIXED_PRODUCT_QRCODE_TEMPLATE,
                List.of(
                        new PrintLabelRequest("bt000zjm", "黄中冰"),
                        new PrintLabelRequest("BT000RLG", "")
                )
        );

        assertEquals(2, images.size());
        assertEquals(900, images.get(0).getWidth());
        assertEquals(675, images.get(0).getHeight());
    }

    @Test
    void renderLabelsShouldRejectUnsupportedTemplate() {
        assertThrows(
                IllegalArgumentException.class,
                () -> labelRenderService.renderLabels("unsupported_template", List.of(new PrintLabelRequest("BT001", "测试")))
        );
    }

    @Test
    void renderLabelsShouldCreateWarehouseLocationImages() {
        List<BufferedImage> images = labelRenderService.renderLabels(
                LabelRenderService.WAREHOUSE_LOCATION_QRCODE_TEMPLATE,
                List.of(new PrintLabelRequest("LSI|WAREHOUSE|1|3", "3号库位"))
        );

        assertEquals(1, images.size());
        assertEquals(900, images.get(0).getWidth());
        assertEquals(675, images.get(0).getHeight());
    }
}
