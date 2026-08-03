package com.Laibin.SugarInventory.inventoryhistory;

import com.Laibin.SugarInventory.inventoryhistory.domain.InventoryReconciliationRun;
import com.Laibin.SugarInventory.inventoryhistory.domain.InventorySnapshotRun;
import com.Laibin.SugarInventory.inventoryhistory.domain.InventoryTrendReleaseGate;
import com.Laibin.SugarInventory.inventoryhistory.mapper.InventoryDailySnapshotMapper;
import com.Laibin.SugarInventory.inventoryhistory.mapper.InventoryDataQualityIssueMapper;
import com.Laibin.SugarInventory.inventoryhistory.mapper.InventoryReconciliationResultMapper;
import com.Laibin.SugarInventory.inventoryhistory.mapper.InventoryReconciliationRunMapper;
import com.Laibin.SugarInventory.inventoryhistory.mapper.InventorySnapshotRunMapper;
import com.Laibin.SugarInventory.inventoryhistory.mapper.InventoryTrendReleaseGateMapper;
import com.Laibin.SugarInventory.inventoryhistory.mapper.StockMovementEventMapper;
import com.Laibin.SugarInventory.inventoryhistory.service.InventoryReconciliationCalculator;
import com.Laibin.SugarInventory.inventoryhistory.service.InventoryReconciliationService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InventoryReconciliationServiceTest {
    @Test
    void missingOpeningSnapshotIsPersistedAsInsufficientData() {
        Fixture fixture = new Fixture();
        LocalDate businessDate = LocalDate.of(2026, 7, 29);
        InventorySnapshotRun closing = snapshot("close-0729", businessDate);
        when(fixture.snapshotRunMapper.findLatestCompleted(
                businessDate.minusDays(1), "DAILY_CLOSE"
        )).thenReturn(null);
        when(fixture.snapshotRunMapper.findLatestCompleted(
                businessDate, "DAILY_CLOSE"
        )).thenReturn(closing);
        when(fixture.runMapper.findMaxRevision(businessDate)).thenReturn(0);
        when(fixture.runMapper.listLatestDays(businessDate, 7)).thenReturn(List.of());
        InventoryTrendReleaseGate gate = new InventoryTrendReleaseGate();
        gate.setId(1L);
        when(fixture.gateMapper.findByGateKey(
                InventoryReconciliationService.GATE_KEY
        )).thenReturn(gate);

        InventoryReconciliationRun run =
                fixture.service.reconcileIfNeeded(businessDate);

        assertEquals("INSUFFICIENT_DATA", run.getStatus());
        assertEquals(1, run.getIssueCount());
        verify(fixture.issueMapper).insert(any());
        verify(fixture.resultMapper, never()).insert(any());
        verify(fixture.gateMapper).updateById(any());
    }

    @Test
    void sameTerminalSnapshotPairIsIdempotent() {
        Fixture fixture = new Fixture();
        LocalDate businessDate = LocalDate.of(2026, 7, 30);
        InventorySnapshotRun opening = snapshot("open-0729", businessDate.minusDays(1));
        InventorySnapshotRun closing = snapshot("close-0730", businessDate);
        InventoryReconciliationRun existing = new InventoryReconciliationRun();
        existing.setStatus("PASSED");
        existing.setOpeningSnapshotRunId(opening.getSnapshotRunId());
        existing.setClosingSnapshotRunId(closing.getSnapshotRunId());
        when(fixture.snapshotRunMapper.findLatestCompleted(
                businessDate.minusDays(1), "DAILY_CLOSE"
        )).thenReturn(opening);
        when(fixture.snapshotRunMapper.findLatestCompleted(
                businessDate, "DAILY_CLOSE"
        )).thenReturn(closing);
        when(fixture.runMapper.findLatest(businessDate)).thenReturn(existing);

        InventoryReconciliationRun result =
                fixture.service.reconcileIfNeeded(businessDate);

        assertEquals(existing, result);
        verify(fixture.runMapper, never()).insert(any());
    }

    @Test
    void sevenConsecutivePassedDaysMakeGateEligible() {
        Fixture fixture = new Fixture();
        LocalDate businessDate = LocalDate.of(2026, 8, 6);
        InventorySnapshotRun opening = snapshot("open", businessDate.minusDays(1));
        InventorySnapshotRun closing = snapshot("close", businessDate);
        when(fixture.snapshotRunMapper.findLatestCompleted(
                businessDate.minusDays(1), "DAILY_CLOSE"
        )).thenReturn(opening);
        when(fixture.snapshotRunMapper.findLatestCompleted(
                businessDate, "DAILY_CLOSE"
        )).thenReturn(closing);
        when(fixture.runMapper.findMaxRevision(businessDate)).thenReturn(0);
        when(fixture.snapshotMapper.listProductBalances(any())).thenReturn(List.of());
        when(fixture.snapshotMapper.listWarehouseProductBalances(any())).thenReturn(List.of());
        when(fixture.eventMapper.listOccurredBetween(any(), any())).thenReturn(List.of());
        List<InventoryReconciliationRun> passedDays = new ArrayList<>();
        for (int index = 0; index < 7; index++) {
            InventoryReconciliationRun day = new InventoryReconciliationRun();
            day.setBusinessDate(businessDate.minusDays(index));
            day.setStatus("PASSED");
            passedDays.add(day);
        }
        when(fixture.runMapper.listLatestDays(businessDate, 7))
                .thenReturn(passedDays);
        InventoryTrendReleaseGate gate = new InventoryTrendReleaseGate();
        gate.setId(1L);
        when(fixture.gateMapper.findByGateKey(
                InventoryReconciliationService.GATE_KEY
        )).thenReturn(gate);

        InventoryReconciliationRun run =
                fixture.service.reconcileIfNeeded(businessDate);

        assertEquals("PASSED", run.getStatus());
        ArgumentCaptor<InventoryTrendReleaseGate> gateCaptor =
                ArgumentCaptor.forClass(InventoryTrendReleaseGate.class);
        verify(fixture.gateMapper).updateById(gateCaptor.capture());
        assertEquals("ELIGIBLE", gateCaptor.getValue().getStatus());
        assertEquals(7, gateCaptor.getValue().getConsecutivePassedDays());
    }

    private InventorySnapshotRun snapshot(String id, LocalDate date) {
        InventorySnapshotRun snapshot = new InventorySnapshotRun();
        snapshot.setSnapshotRunId(id);
        snapshot.setSnapshotDate(date);
        snapshot.setRuleVersion("inventory-snapshot-v1");
        snapshot.setStatus("COMPLETED");
        return snapshot;
    }

    private static final class Fixture {
        private final InventoryReconciliationRunMapper runMapper =
                mock(InventoryReconciliationRunMapper.class);
        private final InventoryReconciliationResultMapper resultMapper =
                mock(InventoryReconciliationResultMapper.class);
        private final InventoryDataQualityIssueMapper issueMapper =
                mock(InventoryDataQualityIssueMapper.class);
        private final InventoryTrendReleaseGateMapper gateMapper =
                mock(InventoryTrendReleaseGateMapper.class);
        private final InventorySnapshotRunMapper snapshotRunMapper =
                mock(InventorySnapshotRunMapper.class);
        private final InventoryDailySnapshotMapper snapshotMapper =
                mock(InventoryDailySnapshotMapper.class);
        private final StockMovementEventMapper eventMapper =
                mock(StockMovementEventMapper.class);
        private final InventoryReconciliationService service =
                new InventoryReconciliationService(
                        runMapper,
                        resultMapper,
                        issueMapper,
                        gateMapper,
                        snapshotRunMapper,
                        snapshotMapper,
                        eventMapper,
                        new InventoryReconciliationCalculator()
                );

        private Fixture() {
            ReflectionTestUtils.setField(service, "minimumPassedDays", 7);
            when(runMapper.insert(any())).thenReturn(1);
            when(runMapper.updateById(any())).thenReturn(1);
            when(issueMapper.insert(any())).thenReturn(1);
            when(resultMapper.insert(any())).thenReturn(1);
            when(gateMapper.updateById(any())).thenReturn(1);
        }
    }
}
