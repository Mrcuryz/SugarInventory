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
                mock(AutoInboundParseService.class), new com.Laibin.SugarInventory.agent.security.AutoInboundBatchRefCodec("12345678901234567890123456789012"));
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
                mock(AutoInboundParseService.class), new com.Laibin.SugarInventory.agent.security.AutoInboundBatchRefCodec("12345678901234567890123456789012"));
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
                mock(SemiProductRecordService.class), parseService, codec);
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
}
