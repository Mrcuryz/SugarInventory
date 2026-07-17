package com.Laibin.SugarInventory.production.service.impl;

import com.Laibin.SugarInventory.agent.security.AgentEntityRefCodec;
import com.Laibin.SugarInventory.common.PageResult;
import com.Laibin.SugarInventory.production.domain.dto.ProductionEntityResolveQueryDTO;
import com.Laibin.SugarInventory.production.domain.dto.ProductionBoilingBatchTraceQueryDTO;
import com.Laibin.SugarInventory.production.domain.dto.ProductionMaterialPickTraceQueryDTO;
import com.Laibin.SugarInventory.production.domain.dto.ProductionLabelCompletionQueryDTO;
import com.Laibin.SugarInventory.production.domain.dto.ProductionInProcessMaterialsAgentQueryDTO;
import com.Laibin.SugarInventory.production.domain.dto.ProductionMaterialCandidatesAgentQueryDTO;
import com.Laibin.SugarInventory.production.domain.dto.ProductionLabelCompletionQueryDTO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionBoilingBatchTraceNodeVO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionBoilingBatchVO;
import com.Laibin.SugarInventory.production.domain.dto.ProductionOrderProgressQueryDTO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionLabelBatchVO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionOrderBaseVO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionOrderDetailVO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionOrderPageVO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionOrderProgressVO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionMaterialVO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionOutputVO;
import com.Laibin.SugarInventory.production.service.ProductionBoilingBatchService;
import com.Laibin.SugarInventory.production.service.ProductionOrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
    private ProductionAgentReadServiceImpl service;

    @BeforeEach
    void setUp() {
        orderService = mock(ProductionOrderService.class);
        boilingService = mock(ProductionBoilingBatchService.class);
        refCodec = mock(AgentEntityRefCodec.class);
        service = new ProductionAgentReadServiceImpl(orderService, boilingService, refCodec);
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
        ProductionLabelBatchVO label = new ProductionLabelBatchVO();
        label.setReservedCount(10);
        label.setUsedCount(8);
        label.setRecycledCount(1);
        ProductionOrderDetailVO detail = new ProductionOrderDetailVO();
        detail.setBaseInfo(base);
        detail.setMaterials(List.of());
        detail.setOutputs(List.of(output));
        detail.setLabelBatches(List.of(label));
        when(orderService.getOrderDetail(42L)).thenReturn(detail);
        ProductionOrderProgressQueryDTO query = new ProductionOrderProgressQueryDTO();
        query.setOrderRef("aer_controlled");

        ProductionOrderProgressVO result = service.queryOrderProgress(query, 7);

        verify(orderService).getOrderDetail(42L);
        assertThat(result.getRequiredQrCount()).isEqualTo(10);
        assertThat(result.getInboundQrCount()).isEqualTo(6);
        assertThat(result.getReservedLabelCount()).isEqualTo(10);
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
        assertThat(result.getLimitations()).anyMatch(value -> value.contains("不会由 Agent 推断"));
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
