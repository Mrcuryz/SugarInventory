package com.Laibin.SugarInventory.analytics.service;

import com.Laibin.SugarInventory.analytics.domain.dto.RegisteredReportRunQueryDTO;
import com.Laibin.SugarInventory.analytics.domain.vo.PalletTaskCycleFactRowVO;
import com.Laibin.SugarInventory.analytics.mapper.PalletTaskCycleReportMapper;
import com.Laibin.SugarInventory.analytics.service.impl.PalletTaskCycleRegisteredReportService;
import com.Laibin.SugarInventory.common.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PalletTaskCycleRegisteredReportServiceTest {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private PalletTaskCycleReportMapper mapper;
    private PalletTaskCycleRegisteredReportService service;

    @BeforeEach
    void setUp() {
        mapper = mock(PalletTaskCycleReportMapper.class);
        Clock clock = Clock.fixed(Instant.parse("2026-07-31T10:00:00Z"), BUSINESS_ZONE);
        service = new PalletTaskCycleRegisteredReportService(mapper, clock);
    }

    @Test
    void separatesCompletedDurationsPendingWaitingAndCanceledCounts() {
        LocalDate day = LocalDate.of(2026, 7, 30);
        when(mapper.listTaskFacts(day, day, null, null, 100001)).thenReturn(List.of(
                task("BT001", "FINISH_IN", "CONFIRMED", day.atTime(8, 0), day.atTime(8, 1), true),
                task("BT002", "FINISH_IN", "CONFIRMED", day.atTime(9, 0), day.atTime(9, 3), true),
                task("BT003", "OUT", "PENDING", day.atTime(10, 0), null, false),
                task("BT004", "SEMI_IN", "CANCELED", day.atTime(11, 0), null, true)));

        var report = service.run(query(day, day, null, null));

        assertThat(report.getReportDefinitionId()).isEqualTo("pallet_task_cycle_time_v1");
        assertThat(report.getPalletTaskCycleMetrics().getCohortTaskCount()).isEqualTo(4);
        assertThat(report.getPalletTaskCycleMetrics().getCompletedTaskCount()).isEqualTo(2);
        assertThat(report.getPalletTaskCycleMetrics().getInProgressTaskCount()).isEqualTo(1);
        assertThat(report.getPalletTaskCycleMetrics().getCanceledTaskCount()).isEqualTo(1);
        assertThat(report.getPalletTaskCycleMetrics().getAverageDurationSeconds()).isEqualTo(120);
        assertThat(report.getPalletTaskCycleMetrics().getMedianDurationSeconds()).isEqualTo(120);
        assertThat(report.getPalletTaskCycleMetrics().getMaximumWaitingSeconds()).isEqualTo(115200);
        assertThat(report.getPalletTaskPendingItems()).singleElement().satisfies(item -> {
            assertThat(item.getPalletCode()).isEqualTo("BT003");
            assertThat(item.getTaskTypeLabel()).isEqualTo("出库任务");
            assertThat(item.getWaitingSeconds()).isEqualTo(115200);
        });
        assertThat(report.getDataQuality().isPartial()).isTrue();
        assertThat(report.getDataQuality().getCanceledWithoutTerminalTimeCount()).isEqualTo(1);
        assertThat(report.getDataQuality().getTasksWithoutFlowRecordCount()).isEqualTo(1);
        assertThat(report.getLimitations())
                .anyMatch(value -> value.contains("不代表现场全部操作") && value.contains("员工绩效"));
    }

    @Test
    void supportsControlledInboundFilterAndRejectsUnknownType() {
        LocalDate day = LocalDate.of(2026, 7, 30);
        when(mapper.listTaskFacts(day, day, "黄冰糖", "INBOUND", 100001))
                .thenReturn(List.of());

        var report = service.run(query(day, day, " 黄冰糖 ", "INBOUND"));

        assertThat(report.getFiltersApplied().get("productScope")).contains("黄冰糖");
        assertThat(report.getFiltersApplied().get("taskScope")).isEqualTo("全部入库任务");

        RegisteredReportRunQueryDTO unknown = query(day, day, null, "DELETE");
        assertThatThrownBy(() -> service.run(unknown))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("任务类型筛选不受支持");
    }

    @Test
    void rejectsRangesLongerThan366Days() {
        assertThatThrownBy(() -> service.run(query(
                LocalDate.of(2025, 7, 30), LocalDate.of(2026, 7, 31), null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("最多查询 366 天");
    }

    private static RegisteredReportRunQueryDTO query(
            LocalDate start,
            LocalDate end,
            String productQuery,
            String taskType) {
        RegisteredReportRunQueryDTO query = new RegisteredReportRunQueryDTO();
        query.setReportDefinitionId(PalletTaskCycleRegisteredReportService.REPORT_ID);
        query.setReportVersion(1);
        query.setStartDate(start);
        query.setEndDate(end);
        query.setProductQuery(productQuery);
        query.setTaskType(taskType);
        return query;
    }

    private static PalletTaskCycleFactRowVO task(
            String palletCode,
            String type,
            String status,
            LocalDateTime createdAt,
            LocalDateTime confirmedAt,
            boolean hasFlow) {
        PalletTaskCycleFactRowVO row = new PalletTaskCycleFactRowVO();
        row.setTaskId(Math.abs(palletCode.hashCode()));
        row.setPalletCode(palletCode);
        row.setTaskType(type);
        row.setStatus(status);
        row.setProductName("黄冰糖（袋）");
        row.setTargetWarehouseName("1号库位");
        row.setCreatedAt(createdAt);
        row.setConfirmedAt(confirmedAt);
        row.setHasFlowRecord(hasFlow);
        return row;
    }
}
