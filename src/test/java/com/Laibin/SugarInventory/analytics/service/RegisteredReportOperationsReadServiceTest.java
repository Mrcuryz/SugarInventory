package com.Laibin.SugarInventory.analytics.service;

import com.Laibin.SugarInventory.analytics.domain.ReportExecutionWindowAggregate;
import com.Laibin.SugarInventory.analytics.domain.po.RegisteredReportCleanupRunPO;
import com.Laibin.SugarInventory.analytics.domain.po.RegisteredReportExecutionRunPO;
import com.Laibin.SugarInventory.analytics.domain.vo.RegisteredReportOperationsStatusVO;
import com.Laibin.SugarInventory.analytics.mapper.RegisteredReportCleanupRunMapper;
import com.Laibin.SugarInventory.analytics.mapper.RegisteredReportExecutionRunMapper;
import com.Laibin.SugarInventory.analytics.mapper.RegisteredReportExportAuditMapper;
import com.Laibin.SugarInventory.analytics.mapper.RegisteredReportRunMapper;
import com.Laibin.SugarInventory.analytics.service.impl.RegisteredReportOperationsReadService;
import com.Laibin.SugarInventory.inventoryhistory.domain.InventoryTrendReleaseGate;
import com.Laibin.SugarInventory.inventoryhistory.mapper.InventoryTrendReleaseGateMapper;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RegisteredReportOperationsReadServiceTest {
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-12T08:00:00Z"), ZoneOffset.UTC);

    @Test
    void aggregatesExecutionExportCleanupAndTrendGateWithoutPayloads() {
        RegisteredReportExecutionRunMapper executionMapper =
                mock(RegisteredReportExecutionRunMapper.class);
        RegisteredReportRunMapper reportMapper = mock(RegisteredReportRunMapper.class);
        RegisteredReportExportAuditMapper exportMapper =
                mock(RegisteredReportExportAuditMapper.class);
        RegisteredReportCleanupRunMapper cleanupMapper =
                mock(RegisteredReportCleanupRunMapper.class);
        InventoryTrendReleaseGateMapper gateMapper =
                mock(InventoryTrendReleaseGateMapper.class);
        ReportExecutionWindowAggregate aggregate = new ReportExecutionWindowAggregate();
        aggregate.setExecutionCount(4L);
        aggregate.setSuccessCount(2L);
        aggregate.setRejectionCount(1L);
        aggregate.setFailureCount(1L);
        aggregate.setPartialDataCount(1L);
        aggregate.setMaxDurationMs(400L);
        when(executionMapper.aggregateWindow(any(), any())).thenReturn(aggregate);
        when(executionMapper.findDurationAtOffset(any(), any(), org.mockito.ArgumentMatchers.eq(1L)))
                .thenReturn(200L);
        when(executionMapper.findDurationAtOffset(any(), any(), org.mockito.ArgumentMatchers.eq(3L)))
                .thenReturn(400L);
        RegisteredReportExecutionRunPO latest = new RegisteredReportExecutionRunPO();
        latest.setStatus("SUCCEEDED");
        latest.setReportDefinitionId("quality_metric_trend_v1");
        latest.setStartedAt(LocalDateTime.of(2026, 8, 12, 15, 30));
        when(executionMapper.findLatest(any(), any())).thenReturn(latest);
        RegisteredReportExecutionRunPO rejection = new RegisteredReportExecutionRunPO();
        rejection.setFailureSummary("当前用户没有运行该报表的权限");
        when(executionMapper.findLatestRejection(any(), any())).thenReturn(rejection);
        RegisteredReportExecutionRunPO failure = new RegisteredReportExecutionRunPO();
        failure.setFailureSummary("日期范围无效");
        when(executionMapper.findLatestFailure(any(), any())).thenReturn(failure);
        when(exportMapper.countInWindow(any(), any())).thenReturn(2L);
        when(reportMapper.countActive(any())).thenReturn(6L);
        when(reportMapper.countExpired(any())).thenReturn(1L);
        RegisteredReportCleanupRunPO cleanup = new RegisteredReportCleanupRunPO();
        cleanup.setStatus("SUCCESS");
        cleanup.setSelectedReportCount(2);
        cleanup.setDeletedReportRunCount(2);
        cleanup.setStartedAt(LocalDateTime.of(2026, 8, 12, 2, 30));
        when(cleanupMapper.findLatest()).thenReturn(cleanup);
        InventoryTrendReleaseGate gate = new InventoryTrendReleaseGate();
        gate.setStatus("BLOCKED");
        gate.setConsecutivePassedDays(0);
        gate.setRequiredPassedDays(7);
        when(gateMapper.findByGateKey("INVENTORY_LEVEL_TREND")).thenReturn(gate);
        RegisteredReportOperationsReadService service = new RegisteredReportOperationsReadService(
                executionMapper, reportMapper, exportMapper, cleanupMapper, gateMapper, CLOCK);

        RegisteredReportOperationsStatusVO status = service.latestStatus();

        assertEquals(4L, status.getExecutionCount());
        assertEquals(2L, status.getSuccessCount());
        assertEquals(1L, status.getRejectionCount());
        assertEquals(1L, status.getFailureCount());
        assertEquals(1L, status.getPartialDataCount());
        assertEquals(200L, status.getDurationP50Ms());
        assertEquals(400L, status.getDurationP95Ms());
        assertEquals(2L, status.getExportCount());
        assertEquals(6L, status.getActiveSnapshotCount());
        assertEquals("当前用户没有运行该报表的权限", status.getLatestRejectionSummary());
        assertEquals("清理成功", status.getLatestCleanupStatus());
        assertEquals("暂不可开放库存趋势", status.getInventoryTrendGateStatus());
        assertEquals("最近24小时运行 4 次，拒绝 1 次，失败 1 次，部分数据 1 次；库存趋势门禁 0/7 天",
                status.getSummary());
    }
}
