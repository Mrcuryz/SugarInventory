package com.Laibin.SugarInventory.production.service;

import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.production.domain.vo.ProductionQuantitySplitVO;
import com.Laibin.SugarInventory.production.service.impl.ProductionOrderServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductionOrderServiceImplTest {
    private final ProductionOrderServiceImpl service = new ProductionOrderServiceImpl(
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            new ObjectMapper()
    );

    @Test
    void splitQuantityKeepsSingleQrPerBoardAndOneQrForRemainder() {
        ProductionQuantitySplitVO split = service.splitQuantity(7, 16, 25);

        assertEquals(7, split.getFinalBoardCount());
        assertEquals(16, split.getFinalPieces());
        assertEquals(191, split.getTotalPieces());
        assertEquals(8, split.getRequiredQrCount());
    }

    @Test
    void splitQuantityConvertsLargePiecesToBoardsAndRemainder() {
        ProductionQuantitySplitVO split = service.splitQuantity(0, 60, 25);

        assertEquals(2, split.getFinalBoardCount());
        assertEquals(10, split.getFinalPieces());
        assertEquals(60, split.getTotalPieces());
        assertEquals(3, split.getRequiredQrCount());
    }

    @Test
    void splitQuantityKeepsInternalConversionSeparateFromOriginalDisplay() {
        ProductionQuantitySplitVO split = service.splitQuantity(1, 20, 20);

        assertEquals(2, split.getFinalBoardCount());
        assertEquals(0, split.getFinalPieces());
        assertEquals(40, split.getTotalPieces());
        assertEquals(2, split.getRequiredQrCount());
    }

    @Test
    void splitQuantityRejectsMissingPiecesPerPallet() {
        assertThrows(BusinessException.class, () -> service.splitQuantity(1, 0, 0));
    }
}
