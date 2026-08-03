package com.Laibin.SugarInventory.inventoryhistory;

import com.Laibin.SugarInventory.inventoryhistory.domain.InventoryHistoryJobRun;
import com.Laibin.SugarInventory.inventoryhistory.domain.InventoryReconciliationRun;
import com.Laibin.SugarInventory.inventoryhistory.domain.InventorySnapshotRun;
import com.Laibin.SugarInventory.inventoryhistory.domain.InventoryTrendReleaseGate;
import com.Laibin.SugarInventory.inventoryhistory.domain.vo.InventoryHistoryOperationsStatusVO;
import com.Laibin.SugarInventory.inventoryhistory.mapper.InventoryHistoryJobRunMapper;
import com.Laibin.SugarInventory.inventoryhistory.mapper.InventoryReconciliationRunMapper;
import com.Laibin.SugarInventory.inventoryhistory.mapper.InventorySnapshotRunMapper;
import com.Laibin.SugarInventory.inventoryhistory.mapper.InventoryTrendReleaseGateMapper;
import com.Laibin.SugarInventory.inventoryhistory.service.InventoryHistoryOperationsReadService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InventoryHistoryOperationsReadServiceTest {
    private static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 7, 31);
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-01T02:00:00Z"),
            ZoneOffset.UTC
    );

    @Test
    void statusUsesUserReadableLabelsInsteadOfInternalEnums() {
        InventorySnapshotRunMapper snapshotMapper = mock(InventorySnapshotRunMapper.class);
        InventoryReconciliationRunMapper reconciliationMapper =
                mock(InventoryReconciliationRunMapper.class);
        InventoryHistoryJobRunMapper jobMapper = mock(InventoryHistoryJobRunMapper.class);
        InventoryTrendReleaseGateMapper gateMapper = mock(InventoryTrendReleaseGateMapper.class);

        InventorySnapshotRun snapshot = new InventorySnapshotRun();
        snapshot.setSnapshotRunId("snap_1");
        snapshot.setDataAsOf(LocalDateTime.of(2026, 8, 1, 0, 0));
        InventoryReconciliationRun reconciliation = new InventoryReconciliationRun();
        reconciliation.setStatus("INSUFFICIENT_DATA");
        reconciliation.setReason("缺少完整期初或期末日终快照");
        InventoryHistoryJobRun job = new InventoryHistoryJobRun();
        job.setStatus("FAILED");
        job.setJobMode("VERIFY");
        job.setTriggerSource("EXTERNAL_VERIFY");
        job.setErrorMessage("缺少可信日终库存快照");
        job.setStartedAt(LocalDateTime.of(2026, 8, 1, 0, 15));
        InventoryTrendReleaseGate gate = new InventoryTrendReleaseGate();
        gate.setStatus("BLOCKED");
        gate.setConsecutivePassedDays(0);
        gate.setRequiredPassedDays(7);
        gate.setReason("等待连续日终快照和每日守恒对账");

        when(snapshotMapper.findLatestCompleted(BUSINESS_DATE, "DAILY_CLOSE"))
                .thenReturn(snapshot);
        when(reconciliationMapper.findLatest(BUSINESS_DATE)).thenReturn(reconciliation);
        when(jobMapper.findLatest(BUSINESS_DATE)).thenReturn(job);
        when(gateMapper.findByGateKey("INVENTORY_LEVEL_TREND")).thenReturn(gate);
        InventoryHistoryOperationsReadService service =
                new InventoryHistoryOperationsReadService(
                        snapshotMapper,
                        reconciliationMapper,
                        jobMapper,
                        gateMapper,
                        CLOCK
                );

        InventoryHistoryOperationsStatusVO status = service.latestStatus();

        assertTrue(status.isSnapshotAvailable());
        assertEquals("数据不足，暂无法对账", status.getReconciliationStatus());
        assertEquals("执行失败", status.getLatestExecutionStatus());
        assertEquals("核验日终任务", status.getLatestExecutionMode());
        assertEquals("外部健康检查", status.getLatestTriggerSource());
        assertEquals("暂不可开放库存趋势", status.getTrendGateStatus());
    }

    @Test
    void missingEvidenceIsExplicitAndDoesNotLeakCodes() {
        InventorySnapshotRunMapper snapshotMapper = mock(InventorySnapshotRunMapper.class);
        InventoryReconciliationRunMapper reconciliationMapper =
                mock(InventoryReconciliationRunMapper.class);
        InventoryHistoryJobRunMapper jobMapper = mock(InventoryHistoryJobRunMapper.class);
        InventoryTrendReleaseGateMapper gateMapper = mock(InventoryTrendReleaseGateMapper.class);
        InventoryHistoryOperationsReadService service =
                new InventoryHistoryOperationsReadService(
                        snapshotMapper,
                        reconciliationMapper,
                        jobMapper,
                        gateMapper,
                        CLOCK
                );

        InventoryHistoryOperationsStatusVO status = service.latestStatus();

        assertFalse(status.isSnapshotAvailable());
        assertEquals("缺少可信日终快照", status.getSnapshotStatus());
        assertEquals("未执行", status.getReconciliationStatus());
        assertEquals("尚无运行记录", status.getLatestExecutionStatus());
        assertNull(status.getLatestFailureSummary());
    }
}
