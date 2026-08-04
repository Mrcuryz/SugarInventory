package com.Laibin.SugarInventory.service;

import com.Laibin.SugarInventory.common.PageResult;
import com.Laibin.SugarInventory.domain.dto.PalletTaskAgentQueryDTO;
import com.Laibin.SugarInventory.domain.vo.PalletTaskPageVO;
import com.Laibin.SugarInventory.service.impl.LogisticsAgentReadServiceImpl;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LogisticsAgentReadServiceImplTest {
    private static final String REF_SECRET = "12345678901234567890123456789012";
    @Test
    void removesInternalIdsAndKeepsReadOnlyTaskFacts() {
        PalletCodeService palletCodeService = mock(PalletCodeService.class);
        PalletTaskPageVO row = new PalletTaskPageVO();
        row.setTaskId(11); row.setPalletCodeId(22); row.setProductId(33); row.setTargetWarehouseId(44);
        row.setTaskType("OUT"); row.setTaskStatus("PENDING"); row.setCode("P001"); row.setProductName("冰糖");
        when(palletCodeService.pagePalletTasks(any())).thenReturn(new PageResult<>(1L, List.of(row)));
        LogisticsAgentReadServiceImpl service = new LogisticsAgentReadServiceImpl(
                palletCodeService, mock(InStockService.class), mock(OutStockService.class), mock(SemiProductRecordService.class),
                mock(AutoInboundParseService.class), new com.Laibin.SugarInventory.agent.security.AutoInboundBatchRefCodec(REF_SECRET),
                new com.Laibin.SugarInventory.agent.security.TaskTransitionPreviewRefCodec(REF_SECRET));
        PalletTaskAgentQueryDTO query = new PalletTaskAgentQueryDTO();
        query.setTaskType("OUT"); query.setStatus("PENDING");

        var result = service.queryPalletTasks(query);

        assertThat(result.getRecords()).singleElement().satisfies(item -> {
            assertThat(item.getCode()).isEqualTo("P001");
            assertThat(item.getTaskStatus()).isEqualTo("PENDING");
        });
        assertThat(result.getLimitations()).anyMatch(value -> value.contains("不执行任务确认"));
    }

    @Test
    void stockDocumentsUseOneExplicitSourceAndOmitAssayIds() {
        OutStockService outStockService = mock(OutStockService.class);
        com.Laibin.SugarInventory.domain.vo.OutStockRecordVO row = new com.Laibin.SugarInventory.domain.vo.OutStockRecordVO();
        row.setProductName("冰糖"); row.setWarehouseName("1号库位"); row.setAssayId(99); row.setQuantity(2);
        when(outStockService.searchOutRecords(any(), any())).thenReturn(new PageResult<>(1L, List.of(row)));
        LogisticsAgentReadServiceImpl service = new LogisticsAgentReadServiceImpl(
                mock(PalletCodeService.class), mock(InStockService.class), outStockService, mock(SemiProductRecordService.class),
                mock(AutoInboundParseService.class), new com.Laibin.SugarInventory.agent.security.AutoInboundBatchRefCodec(REF_SECRET),
                new com.Laibin.SugarInventory.agent.security.TaskTransitionPreviewRefCodec(REF_SECRET));
        com.Laibin.SugarInventory.domain.dto.StockDocumentAgentQueryDTO query =
                new com.Laibin.SugarInventory.domain.dto.StockDocumentAgentQueryDTO();
        query.setDocumentType("OUTBOUND");

        var result = service.queryStockDocuments(query, new com.Laibin.SugarInventory.domain.po.User());

        assertThat(result.getDocumentType()).isEqualTo("OUTBOUND");
        assertThat(result.getRecords()).singleElement().satisfies(item -> assertThat(item.getProductName()).isEqualTo("冰糖"));
        assertThat(result.getLimitations()).anyMatch(value -> value.contains("只覆盖一种明确单据类型"));
    }

    @Test
    void autoInboundBatchRefsAreBoundToCurrentUserBeforeDetailRead() {
        AutoInboundParseService parseService = mock(AutoInboundParseService.class);
        var option = new com.Laibin.SugarInventory.domain.vo.AutoInboundBatchOptionVO();
        option.setBatchId("raw-batch-1"); option.setDisplayName("今日报数"); option.setTaskCount(1); option.setStatus("PARSED");
        when(parseService.listBatches(any())).thenReturn(List.of(option));
        var task = new com.Laibin.SugarInventory.domain.redis.AutoInboundTask();
        task.setTaskId("internal-task"); task.setBatchId("raw-batch-1"); task.setProductId(10); task.setWarehouseId(20);
        task.setType(com.Laibin.SugarInventory.domain.enumObject.AutoInboundType.FINISHED_PRODUCT);
        task.setProductName("单晶冰糖"); task.setWarehouseName("1号库位");
        var response = new com.Laibin.SugarInventory.domain.vo.AutoInboundParseResponse(); response.setTasks(List.of(task));
        when(parseService.getBatch("raw-batch-1")).thenReturn(response);
        var codec = new com.Laibin.SugarInventory.agent.security.AutoInboundBatchRefCodec(REF_SECRET);
        LogisticsAgentReadServiceImpl service = new LogisticsAgentReadServiceImpl(
                mock(PalletCodeService.class), mock(InStockService.class), mock(OutStockService.class),
                mock(SemiProductRecordService.class), parseService, codec,
                new com.Laibin.SugarInventory.agent.security.TaskTransitionPreviewRefCodec(REF_SECRET));
        var user = new com.Laibin.SugarInventory.domain.po.User(); user.setId(7);

        var batches = service.queryAutoInboundBatches(null, user);
        var query = new com.Laibin.SugarInventory.domain.dto.AutoInboundBatchDetailAgentQueryDTO();
        query.setBatchRef(batches.getRecords().getFirst().getBatchRef());
        var detail = service.getAutoInboundBatchDetail(query, user);

        assertThat(batches.getRecords().getFirst().getBatchRef()).startsWith("aibr_").doesNotContain("raw-batch-1");
        assertThat(detail.getTasks()).singleElement().satisfies(item -> {
            assertThat(item.getProductName()).isEqualTo("单晶冰糖");
            assertThat(item.getWarehouseName()).isEqualTo("1号库位");
        });
        var otherUser = new com.Laibin.SugarInventory.domain.po.User(); otherUser.setId(8);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.getAutoInboundBatchDetail(query, otherUser))
                .isInstanceOf(com.Laibin.SugarInventory.common.BusinessException.class);
    }

    @Test
    void previewsSelectedPendingFinishInboundTasksWithoutExecutingAnything() {
        PalletCodeService palletCodeService = mock(PalletCodeService.class);
        PalletTaskPageVO row = new PalletTaskPageVO();
        row.setTaskId(101); row.setTaskType("FINISH_IN"); row.setTaskStatus("PENDING");
        row.setCode("BT0019N1"); row.setProductName("黄冰糖（袋）");
        row.setProductionDate(java.time.LocalDate.of(2026, 5, 22));
        row.setCreatedAt(java.time.LocalDateTime.of(2026, 5, 22, 8, 0));
        when(palletCodeService.pagePalletTasks(any())).thenReturn(new PageResult<>(1L, List.of(row)));
        LogisticsAgentReadServiceImpl service = new LogisticsAgentReadServiceImpl(
                palletCodeService, mock(InStockService.class), mock(OutStockService.class),
                mock(SemiProductRecordService.class), mock(AutoInboundParseService.class),
                new com.Laibin.SugarInventory.agent.security.AutoInboundBatchRefCodec(REF_SECRET),
                new com.Laibin.SugarInventory.agent.security.TaskTransitionPreviewRefCodec(REF_SECRET));
        var request = new com.Laibin.SugarInventory.domain.dto.TaskTransitionPreviewDTO();
        request.setPreviewVersion(1); request.setTransition("CONFIRM_FINISH_INBOUND");
        request.setPalletCodes(List.of("bt0019n1"));
        var user = new com.Laibin.SugarInventory.domain.po.User(); user.setId(7);

        var result = service.previewTaskTransition(request, user);

        assertThat(result.getPreviewStatus()).isEqualTo("READY");
        assertThat(result.isCanOpenBusinessDialog()).isTrue();
        assertThat(result.getPreviewRef()).startsWith("tpr1_");
        assertThat(result.getStateDigest()).hasSize(64);
        assertThat(result.getExpiresAt()).isAfter(result.getPreviewedAt());
        assertThat(result.getTasks()).singleElement().satisfies(task -> {
            assertThat(task.getPalletCode()).isEqualTo("BT0019N1");
            assertThat(task.getCurrentTaskStatus()).isEqualTo("PENDING");
        });
        assertThat(result.getLimitations())
                .anyMatch(value -> value.contains("不能直接执行任何业务写入"))
                .noneMatch(value -> value.contains("previewRef") || value.contains("executionToken"));
    }

    @Test
    void previewsSelectedPendingFinishOutboundTasksWithCurrentInventoryFacts() {
        PalletCodeService palletCodeService = mock(PalletCodeService.class);
        PalletTaskPageVO row = new PalletTaskPageVO();
        row.setTaskId(201); row.setTaskType("OUT"); row.setBizScene("FINISH_OUT");
        row.setTaskStatus("PENDING"); row.setCode("BT00135D"); row.setProductName("黄冰糖（袋）");
        row.setProductionDate(java.time.LocalDate.of(2026, 5, 7));
        row.setCreatedAt(java.time.LocalDateTime.of(2026, 5, 7, 8, 0));
        when(palletCodeService.pagePalletTasks(any())).thenReturn(new PageResult<>(1L, List.of(row)));
        var pallet = new com.Laibin.SugarInventory.domain.po.PalletCode();
        pallet.setStatus("INSTOCK"); pallet.setProductStatus("成品"); pallet.setProductId(84);
        pallet.setProductionDate(java.time.LocalDate.of(2026, 5, 7));
        when(palletCodeService.parseAndFind("BT00135D")).thenReturn(pallet);
        var inventory = new com.Laibin.SugarInventory.domain.vo.PalletInventoryVO();
        inventory.setWarehouseName("2"); inventory.setSide("LEFT");
        inventory.setRowNumber(2); inventory.setLayer(1); inventory.setQuantity(1); inventory.setUnit(false);
        when(palletCodeService.getInventoryByCode("BT00135D")).thenReturn(inventory);
        LogisticsAgentReadServiceImpl service = new LogisticsAgentReadServiceImpl(
                palletCodeService, mock(InStockService.class), mock(OutStockService.class),
                mock(SemiProductRecordService.class), mock(AutoInboundParseService.class),
                new com.Laibin.SugarInventory.agent.security.AutoInboundBatchRefCodec(REF_SECRET),
                new com.Laibin.SugarInventory.agent.security.TaskTransitionPreviewRefCodec(REF_SECRET));
        var request = new com.Laibin.SugarInventory.domain.dto.TaskTransitionPreviewDTO();
        request.setPreviewVersion(1); request.setTransition("CONFIRM_FINISH_OUTBOUND");
        request.setPalletCodes(List.of("bt00135d"));
        var user = new com.Laibin.SugarInventory.domain.po.User(); user.setId(7);

        var result = service.previewTaskTransition(request, user);

        assertThat(result.getDataScope()).isEqualTo("CURRENT_FINISH_OUTBOUND_TASK_TRANSITION_PREVIEW");
        assertThat(result.getPreviewStatus()).isEqualTo("READY");
        assertThat(result.isCanOpenBusinessDialog()).isTrue();
        assertThat(result.getPreviewRef()).startsWith("tpr1_");
        assertThat(result.getRequiredUserInputs()).isEmpty();
        assertThat(result.getTasks()).singleElement().satisfies(task -> {
            assertThat(task.getPalletCode()).isEqualTo("BT00135D");
            assertThat(task.getCurrentWarehouseName()).isEqualTo("2");
            assertThat(task.getCurrentSide()).isEqualTo("LEFT");
            assertThat(task.getCurrentInventoryQuantity()).isEqualTo(1);
            assertThat(task.getCurrentInventoryUnit()).isEqualTo("板");
        });
        org.mockito.Mockito.verify(palletCodeService, org.mockito.Mockito.never())
                .confirmFinishOutTasks(any(), any());
    }

    @Test
    void finishOutboundPreviewFailsClosedWhenPalletIsNoLongerInStock() {
        PalletCodeService palletCodeService = mock(PalletCodeService.class);
        PalletTaskPageVO row = new PalletTaskPageVO();
        row.setTaskId(201); row.setTaskType("OUT"); row.setBizScene("FINISH_OUT");
        row.setTaskStatus("PENDING"); row.setCode("BT00135D");
        row.setCreatedAt(java.time.LocalDateTime.of(2026, 5, 7, 8, 0));
        when(palletCodeService.pagePalletTasks(any())).thenReturn(new PageResult<>(1L, List.of(row)));
        var pallet = new com.Laibin.SugarInventory.domain.po.PalletCode();
        pallet.setStatus("FREE"); pallet.setProductStatus("成品"); pallet.setProductId(84);
        pallet.setProductionDate(java.time.LocalDate.of(2026, 5, 7));
        when(palletCodeService.parseAndFind("BT00135D")).thenReturn(pallet);
        LogisticsAgentReadServiceImpl service = new LogisticsAgentReadServiceImpl(
                palletCodeService, mock(InStockService.class), mock(OutStockService.class),
                mock(SemiProductRecordService.class), mock(AutoInboundParseService.class),
                new com.Laibin.SugarInventory.agent.security.AutoInboundBatchRefCodec(REF_SECRET),
                new com.Laibin.SugarInventory.agent.security.TaskTransitionPreviewRefCodec(REF_SECRET));
        var request = new com.Laibin.SugarInventory.domain.dto.TaskTransitionPreviewDTO();
        request.setPreviewVersion(1); request.setTransition("CONFIRM_FINISH_OUTBOUND");
        request.setPalletCodes(List.of("BT00135D"));
        var user = new com.Laibin.SugarInventory.domain.po.User(); user.setId(7);

        var result = service.previewTaskTransition(request, user);

        assertThat(result.getPreviewStatus()).isEqualTo("CONFLICT");
        assertThat(result.isCanOpenBusinessDialog()).isFalse();
        assertThat(result.getPreviewRef()).isNull();
        assertThat(result.getBlockingIssues()).singleElement().asString().contains("不在可出库状态");
        org.mockito.Mockito.verify(palletCodeService, org.mockito.Mockito.never()).getInventoryByCode(any());
    }

    @Test
    void previewFailsClosedWhenAnySelectedTaskIsNoLongerEligible() {
        PalletCodeService palletCodeService = mock(PalletCodeService.class);
        when(palletCodeService.pagePalletTasks(any())).thenReturn(new PageResult<>(0L, List.of()));
        LogisticsAgentReadServiceImpl service = new LogisticsAgentReadServiceImpl(
                palletCodeService, mock(InStockService.class), mock(OutStockService.class),
                mock(SemiProductRecordService.class), mock(AutoInboundParseService.class),
                new com.Laibin.SugarInventory.agent.security.AutoInboundBatchRefCodec(REF_SECRET),
                new com.Laibin.SugarInventory.agent.security.TaskTransitionPreviewRefCodec(REF_SECRET));
        var request = new com.Laibin.SugarInventory.domain.dto.TaskTransitionPreviewDTO();
        request.setPreviewVersion(1); request.setTransition("CONFIRM_FINISH_INBOUND");
        request.setPalletCodes(List.of("BT0019N1"));
        var user = new com.Laibin.SugarInventory.domain.po.User(); user.setId(7);

        var result = service.previewTaskTransition(request, user);

        assertThat(result.getPreviewStatus()).isEqualTo("CONFLICT");
        assertThat(result.isCanOpenBusinessDialog()).isFalse();
        assertThat(result.getPreviewRef()).isNull();
        assertThat(result.getBlockingIssues()).singleElement().asString().contains("状态已变化");
    }
}
