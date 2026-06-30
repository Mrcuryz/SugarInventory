package com.Laibin.SugarInventory.production.service;

import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.production.domain.dto.ProductionBoilingBatchUsageDTO;
import com.Laibin.SugarInventory.production.domain.dto.ProductionOrderCreateDTO;
import com.Laibin.SugarInventory.production.domain.po.ProductionOrderLabelCode;
import com.Laibin.SugarInventory.production.domain.vo.ProductionQuantitySplitVO;
import com.Laibin.SugarInventory.production.mapper.ProductionOrderLabelCodeMapper;
import com.Laibin.SugarInventory.production.service.impl.ProductionOrderServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
            new ObjectMapper(),
            null
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

    @Test
    void createFinishOrderRejectsBoilingSources() {
        ProductionBoilingBatchUsageDTO source = new ProductionBoilingBatchUsageDTO();
        source.setBatchId(1L);
        source.setUsageQuantity(BigDecimal.ONE);
        ProductionOrderCreateDTO dto = new ProductionOrderCreateDTO();
        dto.setOrderType("FINISH");
        dto.setProductionDate(LocalDate.of(2026, 6, 30));
        dto.setBoilingSources(List.of(source));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.createOrder(dto, 1, "tester"));

        assertTrue(ex.getMessage().contains("煮糖批次来源仅支持半成品生产订单"));
    }

    @Test
    void labelCodeQrUsesAuthorizedLabelContent() {
        ProductionOrderLabelCodeMapper labelCodeMapper = mock(ProductionOrderLabelCodeMapper.class);
        ProductionOrderLabelCode labelCode = new ProductionOrderLabelCode();
        labelCode.setId(99L);
        labelCode.setQrContent("LB|productionOrderId=1|batchId=2|labelCodeId=99|token=test-token");
        when(labelCodeMapper.selectById(99L)).thenReturn(labelCode);
        ProductionOrderServiceImpl qrService = new ProductionOrderServiceImpl(
                null, null, null, null, null, labelCodeMapper, null, null, null, null,
                null, null, null, new ObjectMapper(), null
        );

        byte[] png = qrService.getLabelCodeQrPng(99L);

        assertTrue(png.length > 8);
        assertEquals((byte) 0x89, png[0]);
        assertEquals((byte) 0x50, png[1]);
        assertEquals((byte) 0x4E, png[2]);
        assertEquals((byte) 0x47, png[3]);
    }
}