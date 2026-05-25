package com.Laibin.SugarInventory.service.model;

import com.Laibin.SugarInventory.domain.po.ParsedInboundItem;
import com.Laibin.SugarInventory.domain.po.ParsedSemiSource;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutoInboundSourcePostProcessorTest {

    @Test
    void splitsMaterialHeaderWithMultipleDatedQuantityLines() {
        ParsedInboundItem item = finishedItem("""
                翻中粮25Kg袋黄冰糖1280件（柳冰）
                袋印2025.12.9机破黄中
                11月19号16板
                11月21号1板十6件
                """);
        ParsedSemiSource source = source("机破黄中", "机破黄中\n11月19号16板\n11月21号1板十6件");
        item.setSources(List.of(source));

        List<ParsedSemiSource> result = AutoInboundSourcePostProcessor.normalizeSources(item, LocalDate.of(2025, 12, 9), itemDate());

        assertEquals(2, result.size());
        assertEquals("2025-11-19", result.get(0).getProductionDate());
        assertEquals(16, result.get(0).getBoardCount());
        assertEquals(0, result.get(0).getPieceCount());
        assertEquals("2025-11-21", result.get(1).getProductionDate());
        assertEquals(1, result.get(1).getBoardCount());
        assertEquals(6, result.get(1).getPieceCount());
    }

    @Test
    void parsesAugustDatedQuantityWithChinesePlus() {
        ParsedInboundItem item = finishedItem("袋印2025.12.9\n机破黄中\n8月7号9板十3件");
        ParsedSemiSource source = source("机破黄中", "机破黄中；8月7号9板十3件");
        item.setSources(List.of(source));

        List<ParsedSemiSource> result = AutoInboundSourcePostProcessor.normalizeSources(item, LocalDate.of(2025, 12, 9), itemDate());

        assertEquals(1, result.size());
        assertEquals("2025-08-07", result.get(0).getProductionDate());
        assertEquals(9, result.get(0).getBoardCount());
        assertEquals(3, result.get(0).getPieceCount());
    }

    @Test
    void filtersFinishedTaskWithoutProductAndQuantity() {
        ParsedInboundItem item = new ParsedInboundItem();
        item.setType("FINISHED_PRODUCT_IN");
        item.setProductId(null);
        item.setProductName("");
        ParsedInboundItem.Quantity quantity = new ParsedInboundItem.Quantity();
        quantity.setPallets(0);
        quantity.setPieces(0);
        item.setQuantity(quantity);

        assertTrue(AutoInboundSourcePostProcessor.isInvalidFinishedItem(item));
    }

    @Test
    void leavesUnknownDateEmptyInsteadOfUsingToday() {
        ParsedInboundItem item = finishedItem("袋印2025.12.9\n机破黄中\n16板");
        ParsedSemiSource source = source("机破黄中", "机破黄中；16板");
        source.setBoardCount(16);
        source.setPieceCount(0);
        item.setSources(List.of(source));

        List<ParsedSemiSource> result = AutoInboundSourcePostProcessor.normalizeSources(item, null, itemDate());

        assertEquals(1, result.size());
        assertNull(result.get(0).getProductionDate());
        assertEquals(16, result.get(0).getBoardCount());
    }

    @Test
    void canConvertInvalidFinishedBlockIntoMaterialSource() {
        ParsedInboundItem item = new ParsedInboundItem();
        item.setType("FINISHED_PRODUCT_IN");
        item.setProductNameRaw("机破浅黄中");
        item.setRawBlock("机破浅黄中\n8月4号4板十13件");

        ParsedSemiSource source = AutoInboundSourcePostProcessor.sourceFromInvalidFinishedItem(item);
        ParsedInboundItem carrier = finishedItem("袋印2025.12.9\n" + item.getRawBlock());
        carrier.setSources(List.of(source));

        List<ParsedSemiSource> result = AutoInboundSourcePostProcessor.normalizeSources(carrier, LocalDate.of(2025, 12, 9), itemDate());

        assertEquals(1, result.size());
        assertEquals("2025-08-04", result.get(0).getProductionDate());
        assertEquals(4, result.get(0).getBoardCount());
        assertEquals(13, result.get(0).getPieceCount());
    }

    private static ParsedInboundItem finishedItem(String rawBlock) {
        ParsedInboundItem item = new ParsedInboundItem();
        item.setType("FINISHED_PRODUCT_IN");
        item.setProductionDate("2025-12-09");
        item.setRawBlock(rawBlock);
        return item;
    }

    private static ParsedSemiSource source(String name, String remark) {
        ParsedSemiSource source = new ParsedSemiSource();
        source.setSourceType("SEMI_PRODUCT_IN");
        source.setProductNameRaw(name);
        source.setProductName(name);
        source.setRemark(remark);
        return source;
    }

    private static LocalDate itemDate() {
        return LocalDate.of(2025, 12, 9);
    }
}
