package com.Laibin.SugarInventory.service;

import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.domain.dto.PalletAnomaliesQueryDTO;
import com.Laibin.SugarInventory.domain.dto.PalletFlowRecordsQueryDTO;
import com.Laibin.SugarInventory.domain.dto.PalletLifecycleQueryDTO;
import com.Laibin.SugarInventory.domain.dto.QrBatchInboundCompletionQueryDTO;
import com.Laibin.SugarInventory.domain.vo.PalletAnomaliesVO;
import com.Laibin.SugarInventory.domain.vo.PalletFlowRecordsVO;
import com.Laibin.SugarInventory.domain.vo.PalletLifecycleVO;
import com.Laibin.SugarInventory.mapper.PalletLifecycleQueryMapper;
import com.Laibin.SugarInventory.mapper.model.PalletAnomalyGroupRow;
import com.Laibin.SugarInventory.mapper.model.PalletLifecycleEventRow;
import com.Laibin.SugarInventory.mapper.model.PalletLifecycleSummaryRow;
import com.Laibin.SugarInventory.service.impl.PalletLifecycleAnalysisServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PalletLifecycleAnalysisServiceImplTest {
    private PalletLifecycleQueryMapper mapper;
    private PalletLifecycleAnalysisService service;

    @BeforeEach
    void setUp() {
        mapper = mock(PalletLifecycleQueryMapper.class);
        service = new PalletLifecycleAnalysisServiceImpl(mapper);
    }

    @Test
    void returnsSafeLifecycleTimelineAndRiskLabels() {
        PalletLifecycleSummaryRow summary = new PalletLifecycleSummaryRow();
        summary.setPalletCodeId(12);
        summary.setCode("P202607090001");
        summary.setStatus("INSTOCK");
        summary.setProductName("黄冰糖（袋）");
        summary.setProductionDate(LocalDate.of(2026, 7, 9));
        summary.setQuantity(1);
        summary.setPieces(20);
        summary.setWarehouseName("1号库位");
        summary.setAssayId(null);

        PalletLifecycleEventRow event = new PalletLifecycleEventRow();
        event.setCode("P202607090001");
        event.setOperationType("FINISH_INSTOCK");
        event.setOperationName("成品入库");
        event.setOperationTime(LocalDateTime.of(2026, 7, 9, 10, 30));
        event.setToWarehouseName("1号库位");
        when(mapper.selectLifecycleSummary("P202607090001")).thenReturn(summary);
        when(mapper.selectLifecycleEvents(12, 50)).thenReturn(List.of(event));

        PalletLifecycleQueryDTO query = new PalletLifecycleQueryDTO();
        query.setCode("P202607090001");
        PalletLifecycleVO result = service.queryQrCodeLifecycle(query);

        assertThat(result.getCodeLabel()).isEqualTo("P202607090001");
        assertThat(result.getCurrentStatusLabel()).isEqualTo("在库");
        assertThat(result.getWarehouseLabel()).isEqualTo("1号库位");
        assertThat(result.getQuantityText()).isEqualTo("1板20件");
        assertThat(result.getTimeline()).hasSize(1);
        assertThat(result.getRiskLabels()).contains("当前库存未关联有效化验");
        assertThat(result.toString()).doesNotContain("palletCodeId", "assayId");
    }

    @Test
    void rejectsUnsupportedFlowEventTypes() {
        PalletFlowRecordsQueryDTO query = new PalletFlowRecordsQueryDTO();
        query.setEventTypes(List.of("DELETE"));

        assertThatThrownBy(() -> service.queryPalletFlowRecords(query))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("流转事件类型");
    }

    @Test
    void reportsMissingScanLogSourceWithoutInventingVoidCodeAnomalies() {
        when(mapper.selectAnomalyGroups(any())).thenReturn(List.of());
        PalletAnomaliesQueryDTO query = new PalletAnomaliesQueryDTO();
        query.setAnomalyTypes(List.of("VOID_CODE_SCANNED"));

        PalletAnomaliesVO result = service.queryPalletAnomalies(query);

        assertThat(result.getGroups()).isEmpty();
        assertThat(result.getNotes()).anyMatch(note -> note.contains("没有独立的二维码扫描日志"));
    }

    @Test
    void requiresBatchOrOrderOrProductOrDateForCompletionQuery() {
        assertThatThrownBy(() -> service.queryQrBatchInboundCompletion(new QrBatchInboundCompletionQueryDTO()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("至少提供一项");
    }
}
