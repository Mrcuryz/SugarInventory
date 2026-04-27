package com.Laibin.SugarInventory.service.model;

import com.Laibin.SugarInventory.domain.redis.AutoInboundTaskItem;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AutoInboundQuantityNormalizerTest {

    @Test
    void shouldSplitBoardsAndPiecesIntoSingleQrItems() {
        List<AutoInboundTaskItem> items = AutoInboundQuantityNormalizer.normalize(3, 10, 25, "报数入库");

        assertEquals(4, items.size());
        assertEquals("0", items.get(0).getUnit());
        assertEquals(1, items.get(0).getQuantity());
        assertEquals("1", items.get(3).getUnit());
        assertEquals(10, items.get(3).getQuantity());
        assertEquals("10件，占1板位", items.get(3).getDisplayQuantity());
    }

    @Test
    void shouldNormalizeOverflowPiecesIntoBoardsAndLoosePieces() {
        List<AutoInboundTaskItem> items = AutoInboundQuantityNormalizer.normalize(0, 60, 25, "报数入库");

        assertEquals(3, items.size());
        assertEquals("1板", items.get(0).getDisplayQuantity());
        assertEquals("1板", items.get(1).getDisplayQuantity());
        assertEquals("10件，占1板位", items.get(2).getDisplayQuantity());
    }

    @Test
    void shouldRejectEmptyQuantity() {
        assertThrows(IllegalArgumentException.class,
                () -> AutoInboundQuantityNormalizer.normalize(0, 0, 25, "报数入库"));
    }
}
