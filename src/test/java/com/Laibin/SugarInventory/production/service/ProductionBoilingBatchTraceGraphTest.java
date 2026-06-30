package com.Laibin.SugarInventory.production.service;

import com.Laibin.SugarInventory.mapper.ProductMapper;
import com.Laibin.SugarInventory.production.domain.po.ProductionBoilingBatch;
import com.Laibin.SugarInventory.production.domain.vo.ProductionBoilingBatchTraceNodeVO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionBoilingBatchUsageVO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionTraceMaterialRowVO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionTraceOutputCodeRowVO;
import com.Laibin.SugarInventory.production.mapper.ProductionBoilingBatchMapper;
import com.Laibin.SugarInventory.production.mapper.ProductionBoilingBatchTraceMapper;
import com.Laibin.SugarInventory.production.mapper.ProductionBoilingBatchUsageMapper;
import com.Laibin.SugarInventory.production.service.impl.ProductionBoilingBatchServiceImpl;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProductionBoilingBatchTraceGraphTest {
    private final ProductionBoilingBatchMapper batchMapper = mock(ProductionBoilingBatchMapper.class);
    private final ProductionBoilingBatchUsageMapper usageMapper = mock(ProductionBoilingBatchUsageMapper.class);
    private final ProductMapper productMapper = mock(ProductMapper.class);
    private final ProductionBoilingBatchTraceMapper traceMapper = mock(ProductionBoilingBatchTraceMapper.class);
    private final ProductionBoilingBatchServiceImpl service = new ProductionBoilingBatchServiceImpl(
            batchMapper, usageMapper, productMapper, traceMapper);

    @Test
    void buildsGraphAndTimelineAcrossDownstreamProductionFlow() {
        ProductionBoilingBatch batch = new ProductionBoilingBatch();
        batch.setId(1L);
        batch.setBatchNo("20260630-01");
        batch.setBoilingDate(LocalDate.of(2026, 6, 30));
        batch.setProductNameSnapshot("黄中冰");
        batch.setBucketCount(new BigDecimal("182"));
        batch.setKgPerBucket(new BigDecimal("10.900"));
        batch.setTotalWeightKg(new BigDecimal("1983.800"));
        batch.setStatus("USED_UP");
        batch.setCreatedAt(LocalDateTime.of(2026, 6, 30, 14, 30));
        when(batchMapper.selectById(1L)).thenReturn(batch);
        when(usageMapper.sumBucketByStatus(anyLong(), anyString())).thenReturn(BigDecimal.ZERO);
        when(usageMapper.sumWeightByStatus(anyLong(), anyString())).thenReturn(BigDecimal.ZERO);

        ProductionBoilingBatchUsageVO usage = new ProductionBoilingBatchUsageVO();
        usage.setId(10L);
        usage.setBatchId(1L);
        usage.setBatchNo("20260630-01");
        usage.setProductionOrderId(2002L);
        usage.setOrderNo("PO202606300002");
        usage.setOrderStatus("COMPLETED");
        usage.setUsageUnit("BUCKET");
        usage.setUsageQuantity(new BigDecimal("152"));
        usage.setBucketQuantity(new BigDecimal("152"));
        usage.setWeightKg(new BigDecimal("1656.800"));
        usage.setStatus("CONSUMED");
        usage.setCreatedAt(LocalDateTime.of(2026, 6, 30, 14, 52));
        when(usageMapper.listByBatch(1L)).thenReturn(List.of(usage));

        ProductionTraceOutputCodeRowVO semiOutput = outputRow(3001L, 2002L, "PO202606300002", "SEMI", "黄中冰",
                "半成品", 1, 16, 56, 5001L, 7001, "BT000YGI", 1, "0", 0,
                "INSTOCK", 20, "20", LocalDateTime.of(2026, 6, 30, 15, 10), LocalDateTime.of(2026, 6, 30, 15, 20));
        semiOutput.setPalletCycleNo(4);
        semiOutput.setLabelBatchId(9001L);
        semiOutput.setLabelBatchNo("PO202606300002-LB001");
        semiOutput.setLabelBatchReservedCount(2);
        semiOutput.setLabelBatchUsedCount(2);
        semiOutput.setLabelBatchRecycledCount(0);
        semiOutput.setLabelBatchStatus("CLOSED");
        semiOutput.setLabelBatchCreatedAt(LocalDateTime.of(2026, 6, 30, 15, 5));

        ProductionTraceOutputCodeRowVO semiPieceOutput = outputRow(3001L, 2002L, "PO202606300002", "SEMI", "黄中冰",
                "半成品", 1, 16, 56, 5003L, 7003, "BT000YTV", 16, "1", 16,
                "INSTOCK", 20, "20", LocalDateTime.of(2026, 6, 30, 15, 10), LocalDateTime.of(2026, 6, 30, 15, 20));
        semiPieceOutput.setPalletCycleNo(3);
        semiPieceOutput.setLabelBatchId(9001L);
        semiPieceOutput.setLabelBatchNo("PO202606300002-LB001");
        semiPieceOutput.setLabelBatchReservedCount(2);
        semiPieceOutput.setLabelBatchUsedCount(2);
        semiPieceOutput.setLabelBatchRecycledCount(0);
        semiPieceOutput.setLabelBatchStatus("CLOSED");
        semiPieceOutput.setLabelBatchCreatedAt(LocalDateTime.of(2026, 6, 30, 15, 5));
        when(traceMapper.listOutputCodeRowsByOrder(2002L)).thenReturn(List.of(semiOutput, semiPieceOutput));

        ProductionTraceMaterialRowVO material = new ProductionTraceMaterialRowVO();
        material.setId(4001L);
        material.setProductionOrderId(2003L);
        material.setOrderNo("PO202606300003");
        material.setOrderType("FINISH");
        material.setOrderStatus("COMPLETED");
        material.setPalletCodeId(7001);
        material.setPalletCycleNo(4);
        material.setPalletCode("BT000YGI");
        material.setProductName("黄中冰");
        material.setProductStatus("半成品");
        material.setWarehouseName("20");
        material.setQuantity(1);
        material.setUnit("0");
        material.setPieces(0);
        material.setTotalPieces(40);
        material.setStatus("PICKED");
        material.setPickedAt(LocalDateTime.of(2026, 6, 30, 16, 0));
        ProductionTraceMaterialRowVO oldCycleMaterial = new ProductionTraceMaterialRowVO();
        oldCycleMaterial.setId(3999L);
        oldCycleMaterial.setProductionOrderId(1999L);
        oldCycleMaterial.setOrderNo("PO-OLD-CYCLE");
        oldCycleMaterial.setOrderStatus("COMPLETED");
        oldCycleMaterial.setPalletCodeId(7001);
        oldCycleMaterial.setPalletCycleNo(3);
        oldCycleMaterial.setPalletCode("BT000YGI");
        oldCycleMaterial.setPickedAt(LocalDateTime.of(2026, 5, 1, 10, 0));
        when(traceMapper.listMaterialsByPalletCodeIds(anyCollection()))
                .thenReturn(List.of(oldCycleMaterial, material));

        ProductionTraceOutputCodeRowVO finishedOutput = outputRow(3002L, 2003L, "PO202606300003", "FINISH", "黄冰糖",
                "成品", 2, 0, 80, 5002L, 7002, "PO202606300003-LB001", 2, "0", 0,
                "INSTOCK", 21, "21", LocalDateTime.of(2026, 6, 30, 17, 0), LocalDateTime.of(2026, 6, 30, 17, 20));
        when(traceMapper.listOutputCodeRowsByOrder(2003L)).thenReturn(List.of(finishedOutput));

        ProductionBoilingBatchTraceNodeVO trace = service.getTrace(1L);

        assertThat(trace.getNodes()).extracting(ProductionBoilingBatchTraceNodeVO.TraceGraphNodeVO::getType)
                .contains("BOILING_BATCH_ITEM", "PRODUCTION_ORDER", "ACTUAL_OUTPUT", "PALLET_CODE_BATCH",
                        "WAREHOUSE_LOCATION", "DOWNSTREAM_PRODUCTION_ORDER", "FINISHED_OUTPUT",
                        "FINISHED_WAREHOUSE_LOCATION");
        assertThat(trace.getNodes()).extracting(ProductionBoilingBatchTraceNodeVO.TraceGraphNodeVO::getName)
                .doesNotContain("PO-OLD-CYCLE");
        assertThat(trace.getEdges()).extracting(ProductionBoilingBatchTraceNodeVO.TraceGraphEdgeVO::getAction)
                .contains("引用煮糖批次", "产出", "生成订单码批次", "核销使用标签", "入库", "出库领用");
        assertThat(trace.getEdges().stream()
                .filter(edge -> "order-2002".equals(edge.getSource()) && "output-3001".equals(edge.getTarget())))
                .hasSize(1);
        assertThat(trace.getTimeline()).extracting(ProductionBoilingBatchTraceNodeVO.TraceTimelineRecordVO::getActionType)
                .contains("引用煮糖批次", "半成品产出", "半成品入库", "半成品领用/消耗", "成品产出", "成品入库");
        assertThat(trace.getTimeline()).extracting(ProductionBoilingBatchTraceNodeVO.TraceTimelineRecordVO::getWarehouseName)
                .contains("20号库", "21号库");
    }

    private ProductionTraceOutputCodeRowVO outputRow(Long outputId,
                                                     Long orderId,
                                                     String orderNo,
                                                     String orderType,
                                                     String productName,
                                                     String productStatus,
                                                     Integer boardCount,
                                                     Integer pieceCount,
                                                     Integer totalPieces,
                                                     Long outputCodeId,
                                                     Integer palletCodeId,
                                                     String palletCode,
                                                     Integer quantity,
                                                     String unit,
                                                     Integer pieces,
                                                     String codeStatus,
                                                     Integer warehouseId,
                                                     String warehouseName,
                                                     LocalDateTime outputCreatedAt,
                                                     LocalDateTime inboundAt) {
        ProductionTraceOutputCodeRowVO row = new ProductionTraceOutputCodeRowVO();
        row.setOutputId(outputId);
        row.setProductionOrderId(orderId);
        row.setOrderNo(orderNo);
        row.setOrderType(orderType);
        row.setOrderStatus("COMPLETED");
        row.setOutputProductName(productName);
        row.setOutputProductStatus(productStatus);
        row.setBoardCount(boardCount);
        row.setPieceCount(pieceCount);
        row.setOutputTotalPieces(totalPieces);
        row.setPiecesPerPallet(40);
        row.setOutputStatus("INSTOCK");
        row.setOutputCreatedAt(outputCreatedAt);
        row.setOutputCodeId(outputCodeId);
        row.setPalletCodeId(palletCodeId);
        row.setPalletCode(palletCode);
        row.setQuantity(quantity);
        row.setUnit(unit);
        row.setPieces(pieces);
        row.setCodeStatus(codeStatus);
        row.setWarehouseId(warehouseId);
        row.setWarehouseName(warehouseName);
        row.setInboundAt(inboundAt);
        row.setCodeCreatedAt(outputCreatedAt.plusMinutes(5));
        return row;
    }
}