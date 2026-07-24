package com.Laibin.SugarInventory.production.service.impl;

import com.Laibin.SugarInventory.agent.security.AgentEntityRefCodec;
import com.Laibin.SugarInventory.common.PageResult;
import com.Laibin.SugarInventory.production.domain.dto.ProductionEntityResolveQueryDTO;
import com.Laibin.SugarInventory.production.domain.dto.ProductionBoilingBatchListQueryDTO;
import com.Laibin.SugarInventory.production.domain.dto.ProductionBoilingBatchQueryDTO;
import com.Laibin.SugarInventory.production.domain.dto.ProductionBoilingBatchTraceQueryDTO;
import com.Laibin.SugarInventory.production.domain.dto.ProductionMaterialPickTraceQueryDTO;
import com.Laibin.SugarInventory.production.domain.dto.ProductionLabelCompletionQueryDTO;
import com.Laibin.SugarInventory.production.domain.dto.ProductionInProcessMaterialsAgentQueryDTO;
import com.Laibin.SugarInventory.production.domain.dto.ProductionMaterialCandidatesAgentQueryDTO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionBoilingBatchTraceNodeVO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionBoilingBatchVO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionBoilingBatchUsageVO;
import com.Laibin.SugarInventory.production.domain.dto.ProductionOrderProgressQueryDTO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionLabelBatchVO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionOrderBaseVO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionOrderDetailVO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionOrderPageVO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionOrderProgressVO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionMaterialVO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionOutputVO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionTraceOutputCodeRowVO;
import com.Laibin.SugarInventory.production.mapper.ProductionBoilingBatchTraceMapper;
import com.Laibin.SugarInventory.production.service.ProductionBoilingBatchService;
import com.Laibin.SugarInventory.production.service.ProductionOrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductionAgentReadServiceImplTest {
    private ProductionOrderService orderService;
    private ProductionBoilingBatchService boilingService;
    private AgentEntityRefCodec refCodec;
    private ProductionBoilingBatchTraceMapper traceMapper;
    private ProductionAgentReadServiceImpl service;

    @BeforeEach
    void setUp() {
        orderService = mock(ProductionOrderService.class);
        boilingService = mock(ProductionBoilingBatchService.class);
        refCodec = mock(AgentEntityRefCodec.class);
        traceMapper = mock(ProductionBoilingBatchTraceMapper.class);
        service = new ProductionAgentReadServiceImpl(orderService, boilingService, refCodec, traceMapper);
    }

    @Test
    void resolvesOrderWithoutExposingInternalId() {
        ProductionOrderPageVO row = new ProductionOrderPageVO();
        row.setId(42L);
        row.setOrderNo("PO-20260713-001");
        row.setStatus("IN_PROGRESS");
        row.setProductionDate(LocalDate.of(2026, 7, 13));
        when(orderService.pageOrders(any())).thenReturn(new PageResult<>(1L, List.of(row)));
        when(refCodec.encode("PRODUCTION_ORDER", 42L, 7, "production:order:view"))
                .thenReturn("aer_controlled");
        ProductionEntityResolveQueryDTO query = new ProductionEntityResolveQueryDTO();
        query.setEntityType("PRODUCTION_ORDER");
        query.setQuery("PO-20260713-001");

        var result = service.resolveEntities(query, 7);

        assertThat(result.getResolutionStatus()).isEqualTo("EXACT");
        assertThat(result.getCandidates()).singleElement().satisfies(candidate -> {
            assertThat(candidate.getEntityRef()).isEqualTo("aer_controlled");
            assertThat(candidate.getDisplayCode()).isEqualTo("PO-20260713-001");
        });
    }

    @Test
    void aggregatesCurrentOrderProgressWithExplicitLimitations() {
        when(refCodec.decodeAndValidate("aer_controlled", "PRODUCTION_ORDER", 7,
                "production:order:view"))
                .thenReturn(new AgentEntityRefCodec.DecodedRef("PRODUCTION_ORDER", 42L, 99L));
        ProductionOrderBaseVO base = new ProductionOrderBaseVO();
        base.setOrderNo("PO-20260713-001");
        base.setStatus("IN_PROGRESS");
        ProductionOutputVO output = new ProductionOutputVO();
        output.setRequiredQrCount(10);
        output.setBoundQrCount(8);
        output.setInboundQrCount(6);
        output.setId(21L);
        output.setProductName("黄冰糖（袋）");
        ProductionTraceOutputCodeRowVO inbound = new ProductionTraceOutputCodeRowVO();
        inbound.setOutputId(21L);
        inbound.setPalletCode("BT0014LU");
        inbound.setWarehouseName("2号库位");
        inbound.setInventoryId(31);
        ProductionTraceOutputCodeRowVO pending = new ProductionTraceOutputCodeRowVO();
        pending.setOutputId(21L);
        pending.setPalletCode("BT0014LV");
        pending.setWarehouseName("3号库位");
        ProductionLabelBatchVO label = new ProductionLabelBatchVO();
        label.setReservedCount(10);
        label.setUsedCount(8);
        label.setRecycledCount(1);
        ProductionBoilingBatchUsageVO boilingSource = new ProductionBoilingBatchUsageVO();
        boilingSource.setId(900L);
        boilingSource.setBatchId(901L);
        boilingSource.setBatchNo("20260630-01");
        boilingSource.setUsageUnit("KG");
        boilingSource.setUsageQuantity(new BigDecimal("327"));
        boilingSource.setWeightKg(new BigDecimal("327"));
        boilingSource.setStatus("RESERVED");
        ProductionOrderDetailVO detail = new ProductionOrderDetailVO();
        detail.setBaseInfo(base);
        detail.setBoilingSources(List.of(boilingSource));
        detail.setMaterials(List.of());
        detail.setOutputs(List.of(output));
        detail.setLabelBatches(List.of(label));
        when(orderService.getOrderDetail(42L)).thenReturn(detail);
        when(traceMapper.listOutputCodeRowsByOrder(42L)).thenReturn(List.of(inbound, pending));
        ProductionOrderProgressQueryDTO query = new ProductionOrderProgressQueryDTO();
        query.setOrderRef("aer_controlled");

        ProductionOrderProgressVO result = service.queryOrderProgress(query, 7);

        verify(orderService).getOrderDetail(42L);
        assertThat(result.getRequiredQrCount()).isEqualTo(10);
        assertThat(result.getInboundQrCount()).isEqualTo(6);
        assertThat(result.getReservedLabelCount()).isEqualTo(10);
        assertThat(result.getBoilingSources()).singleElement().satisfies(item -> {
            assertThat(item.getBatchNo()).isEqualTo("20260630-01");
            assertThat(item.getWeightKg()).isEqualByComparingTo("327");
            assertThat(item.getStatus()).isEqualTo("RESERVED");
        });
        assertThat(result.getOutputs()).singleElement().satisfies(item -> {
            assertThat(item.getProductName()).isEqualTo("黄冰糖（袋）");
            assertThat(item.getInboundDestinations()).singleElement().satisfies(destination -> {
                assertThat(destination.getWarehouseName()).isEqualTo("2号库位");
                assertThat(destination.getInboundCodeCount()).isEqualTo(1);
                assertThat(destination.getPalletCodes()).containsExactly("BT0014LU");
            });
        });
        assertThat(result.getLimitations()).anyMatch(value -> value.contains("质量放行"));
    }

    @Test
    void returnsOnlyRegisteredBoilingTraceEdgesWithSafeNodeRefs() {
        when(refCodec.decodeAndValidate("aer_batch", "BOILING_BATCH", 7,
                "production:boiling:view"))
                .thenReturn(new AgentEntityRefCodec.DecodedRef("BOILING_BATCH", 9L, 99L));
        ProductionBoilingBatchVO detail = new ProductionBoilingBatchVO();
        detail.setBatchNo("BT-20260713-001");
        detail.setStatus("AVAILABLE");
        detail.setUsages(List.of());
        ProductionBoilingBatchTraceNodeVO.TraceGraphNodeVO source = new ProductionBoilingBatchTraceNodeVO.TraceGraphNodeVO();
        source.setId("batch:9");
        source.setType("BOILING_BATCH");
        source.setName("BT-20260713-001");
        ProductionBoilingBatchTraceNodeVO.TraceGraphNodeVO target = new ProductionBoilingBatchTraceNodeVO.TraceGraphNodeVO();
        target.setId("order:42");
        target.setType("PRODUCTION_ORDER");
        target.setDocumentNo("PO-20260713-001");
        ProductionBoilingBatchTraceNodeVO.TraceGraphEdgeVO registered = new ProductionBoilingBatchTraceNodeVO.TraceGraphEdgeVO();
        registered.setSource("batch:9");
        registered.setTarget("order:42");
        registered.setAction("USED_BY");
        ProductionBoilingBatchTraceNodeVO.TraceGraphEdgeVO dangling = new ProductionBoilingBatchTraceNodeVO.TraceGraphEdgeVO();
        dangling.setSource("batch:9");
        dangling.setTarget("missing:1");
        ProductionBoilingBatchTraceNodeVO trace = new ProductionBoilingBatchTraceNodeVO();
        trace.setNodes(List.of(source, target));
        trace.setEdges(List.of(registered, dangling));
        when(boilingService.getDetail(9L)).thenReturn(detail);
        when(boilingService.getTrace(9L)).thenReturn(trace);
        ProductionBoilingBatchTraceQueryDTO query = new ProductionBoilingBatchTraceQueryDTO();
        query.setBatchRef("aer_batch");

        var result = service.queryBoilingBatchTrace(query, 7);

        assertThat(result.getNodes()).extracting("nodeRef")
                .containsExactly("trace_node_1", "trace_node_2");
        assertThat(result.getEdges()).singleElement().satisfies(edge -> {
            assertThat(edge.getSourceNodeRef()).isEqualTo("trace_node_1");
            assertThat(edge.getTargetNodeRef()).isEqualTo("trace_node_2");
        });
        assertThat(result.getLimitations()).containsExactly(
                "仅展示系统已登记的煮糖批次详情、使用记录、追溯节点和关系边。");
    }

    @Test
    void listsBoilingBatchesByOptionalProductAndBusinessDateRange() {
        ProductionBoilingBatchVO batch = new ProductionBoilingBatchVO();
        batch.setId(9L);
        batch.setBatchNo("20260718-01");
        batch.setBoilingDate(LocalDate.of(2026, 7, 18));
        batch.setProductName("白冰糖");
        batch.setSugarType("白糖");
        batch.setTeamName("甲班");
        batch.setStatus("AVAILABLE");
        batch.setTotalWeightKg(new BigDecimal("1000"));
        when(boilingService.pageBatches(any())).thenReturn(new PageResult<>(1L, List.of(batch)));
        when(refCodec.encode("BOILING_BATCH", 9L, 7, "production:boiling:view"))
                .thenReturn("aer_batch_9");
        ProductionBoilingBatchListQueryDTO query = new ProductionBoilingBatchListQueryDTO();
        query.setProductQuery(" 白冰糖 ");
        query.setStartDate(LocalDate.of(2026, 6, 21));
        query.setEndDate(LocalDate.of(2026, 7, 20));

        var result = service.queryBoilingBatches(query, 7);

        ArgumentCaptor<ProductionBoilingBatchQueryDTO> captor = ArgumentCaptor.forClass(ProductionBoilingBatchQueryDTO.class);
        verify(boilingService).pageBatches(captor.capture());
        assertThat(captor.getValue().getProductQuery()).isEqualTo("白冰糖");
        assertThat(captor.getValue().getStartDate()).isEqualTo(LocalDate.of(2026, 6, 21));
        assertThat(captor.getValue().getEndDate()).isEqualTo(LocalDate.of(2026, 7, 20));
        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getScopeLabel()).isEqualTo("白冰糖");
        assertThat(result.getDateRangeLabel()).isEqualTo("2026-06-21 至 2026-07-20");
        assertThat(result.getCandidates()).singleElement().satisfies(candidate -> {
            assertThat(candidate.getEntityRef()).isEqualTo("aer_batch_9");
            assertThat(candidate.getDisplayCode()).isEqualTo("20260718-01");
            assertThat(candidate.getSummary()).contains("白冰糖", "1000 kg");
        });
    }

    @Test
    void labelsSingleDayBoilingBatchQueryAsExactDate() {
        when(boilingService.pageBatches(any())).thenReturn(new PageResult<>(0L, List.of()));
        ProductionBoilingBatchListQueryDTO query = new ProductionBoilingBatchListQueryDTO();
        query.setStartDate(LocalDate.of(2026, 6, 30));
        query.setEndDate(LocalDate.of(2026, 6, 30));

        var result = service.queryBoilingBatches(query, 7);

        assertThat(result.getDateRangeLabel()).isEqualTo("2026-06-30");
    }

    @Test
    void materialPickTraceOmitsInternalIdsAndKeepsRegisteredSource() {
        when(refCodec.decodeAndValidate("aer_order", "PRODUCTION_ORDER", 7, "production:order:view"))
                .thenReturn(new AgentEntityRefCodec.DecodedRef("PRODUCTION_ORDER", 42L, 99L));
        ProductionOrderBaseVO base = new ProductionOrderBaseVO();
        base.setOrderNo("PO-001");
        base.setStatus("IN_PROGRESS");
        ProductionMaterialVO material = new ProductionMaterialVO();
        material.setId(999L);
        material.setPalletCodeId(88);
        material.setPalletCode("P001");
        material.setProductName("半成品糖");
        material.setWarehouseName("1号库位");
        material.setSide("左");
        material.setRowNumber(2);
        material.setLayer(1);
        ProductionOrderDetailVO detail = new ProductionOrderDetailVO();
        detail.setBaseInfo(base);
        detail.setMaterials(List.of(material));
        when(orderService.getOrderDetail(42L)).thenReturn(detail);
        ProductionMaterialPickTraceQueryDTO query = new ProductionMaterialPickTraceQueryDTO();
        query.setOrderRef("aer_order");

        var result = service.queryMaterialPickTrace(query, 7);

        assertThat(result.getRecords()).singleElement().satisfies(record -> {
            assertThat(record.getPalletCode()).isEqualTo("P001");
            assertThat(record.getPositionText()).isEqualTo("左侧2排1层");
        });
        assertThat(result.getLimitations()).anyMatch(value -> value.contains("不计算计划差异"));
    }

    @Test
    void labelCompletionSeparatesPrintedBoundAndInboundStages() {
        when(refCodec.decodeAndValidate("aer_order", "PRODUCTION_ORDER", 7, "production:order:view"))
                .thenReturn(new AgentEntityRefCodec.DecodedRef("PRODUCTION_ORDER", 42L, 99L));
        ProductionOrderBaseVO base = new ProductionOrderBaseVO();
        base.setOrderNo("PO-001");
        base.setStatus("IN_PROGRESS");
        ProductionOutputVO output = new ProductionOutputVO();
        output.setRequiredQrCount(10);
        output.setBoundQrCount(7);
        output.setInboundQrCount(4);
        ProductionLabelBatchVO label = new ProductionLabelBatchVO();
        label.setBatchNo("LB-001");
        label.setProductName("白砂糖");
        label.setReservedCount(10);
        label.setUsedCount(8);
        label.setRecycledCount(1);
        ProductionOrderDetailVO detail = new ProductionOrderDetailVO();
        detail.setBaseInfo(base);
        detail.setOutputs(List.of(output));
        detail.setLabelBatches(List.of(label));
        when(orderService.getOrderDetail(42L)).thenReturn(detail);
        ProductionLabelCompletionQueryDTO query = new ProductionLabelCompletionQueryDTO();
        query.setOrderRef("aer_order");

        var result = service.queryProductionLabelCompletion(query, 7);

        assertThat(result.getNotBoundQrCount()).isEqualTo(3);
        assertThat(result.getNotInboundQrCount()).isEqualTo(6);
        assertThat(result.getBatches()).singleElement().satisfies(batch ->
                assertThat(batch.getBatchNo()).isEqualTo("LB-001"));
        assertThat(result.getLimitations()).anyMatch(value ->
                value.contains("printedAt") && value.contains("不代表二维码已绑定或已入库"));
    }

    @Test
    void inProcessMaterialsOmitsInternalIdsAndDoesNotClaimAvailability() {
        ProductionMaterialVO material = new ProductionMaterialVO();
        material.setId(99L);
        material.setProductionOrderId(42L);
        material.setProductId(8);
        material.setPalletCodeId(7);
        material.setOrderNo("PO-001");
        material.setProductName("半成品糖");
        material.setPalletCode("P001");
        material.setStatus("PICKED");
        when(orderService.pageInProcessMaterials(any())).thenReturn(new PageResult<>(1L, List.of(material)));

        var result = service.queryInProcessMaterials(new ProductionInProcessMaterialsAgentQueryDTO());

        assertThat(result.getRecords()).singleElement().satisfies(row -> {
            assertThat(row.getOrderNo()).isEqualTo("PO-001");
            assertThat(row.getPalletCode()).isEqualTo("P001");
        });
        assertThat(result.getLimitations()).anyMatch(value -> value.contains("不代表仍可再次领用"));
    }

    @Test
    void materialCandidatesRequireControlledOrderAndDoNotClaimRecommendation() {
        when(refCodec.decodeAndValidate("aer_order", "PRODUCTION_ORDER", 7, "production:order:view"))
                .thenReturn(new AgentEntityRefCodec.DecodedRef("PRODUCTION_ORDER", 42L, 99L));
        com.Laibin.SugarInventory.production.domain.vo.ProductionMaterialCandidateVO item =
                new com.Laibin.SugarInventory.production.domain.vo.ProductionMaterialCandidateVO();
        item.setInventoryId(99); item.setProductId(8); item.setPalletCodeId(7);
        item.setProductName("半成品糖"); item.setPalletCode("P001"); item.setWarehouseName("1号库位");
        when(orderService.pageMaterialCandidates(any(), any())).thenReturn(new PageResult<>(1L, List.of(item)));
        ProductionMaterialCandidatesAgentQueryDTO query = new ProductionMaterialCandidatesAgentQueryDTO();
        query.setOrderRef("aer_order");

        var result = service.queryMaterialCandidates(query, 7);

        assertThat(result.getRecords()).singleElement().satisfies(row -> assertThat(row.getPalletCode()).isEqualTo("P001"));
        assertThat(result.getLimitations()).anyMatch(value -> value.contains("不得解释为 Agent 的 FIFO/FEFO 推荐"));
    }
}
