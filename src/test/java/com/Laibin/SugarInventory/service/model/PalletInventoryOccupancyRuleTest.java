package com.Laibin.SugarInventory.service.model;

import com.Laibin.SugarInventory.common.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PalletInventoryOccupancyRuleTest {

    @Test
    void shouldRejectQrLoosePiecesWhenPiecesReachOneFullPallet() {
        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> PalletInventoryOccupancyRule.validateSingleQrInventory("1", 50, 50, "二维码入库")
        );
        assertEquals("二维码入库的散件数量必须少于每板件数；整板请使用 1 板", ex.getMessage());
    }

    @Test
    void shouldSplitGenericPiecesIntoFullPalletsAndLoosePieces() {
        PalletInventoryOccupancyRule.PieceSplit split =
                PalletInventoryOccupancyRule.splitPieces(160, 50, "散件入库");
        assertEquals(3, split.fullPalletCount());
        assertEquals(10, split.loosePieces());
    }

    @Test
    void shouldRejectQrMultiPalletBoardQuantity() {
        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> PalletInventoryOccupancyRule.validateSingleQrInventory("0", 2, 50, "二维码入库")
        );
        assertEquals("二维码入库仅允许单个二维码承载 1 板；多板请拆成多个二维码", ex.getMessage());
    }
}
