package com.Laibin.SugarInventory.inventoryhistory;

import com.Laibin.SugarInventory.inventoryhistory.domain.InventoryDailySnapshot;
import com.Laibin.SugarInventory.inventoryhistory.domain.InventorySnapshotAggregate;
import com.Laibin.SugarInventory.inventoryhistory.domain.InventorySnapshotRun;
import com.Laibin.SugarInventory.inventoryhistory.mapper.InventoryDailySnapshotMapper;
import com.Laibin.SugarInventory.inventoryhistory.mapper.InventoryHistoryLockMapper;
import com.Laibin.SugarInventory.inventoryhistory.mapper.InventorySnapshotRunMapper;
import com.Laibin.SugarInventory.inventoryhistory.mapper.InventorySnapshotSourceMapper;
import com.Laibin.SugarInventory.inventoryhistory.mapper.StockMovementEventMapper;
import com.Laibin.SugarInventory.inventoryhistory.service.InventorySnapshotCaptureRejectedException;
import com.Laibin.SugarInventory.inventoryhistory.service.InventorySnapshotService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InventorySnapshotServiceTest {
    @Test
    void capturePersistsVersionedRowsAndCompletesWithChecksum() {
        InventorySnapshotRunMapper runMapper = mock(InventorySnapshotRunMapper.class);
        InventoryDailySnapshotMapper snapshotMapper = mock(InventoryDailySnapshotMapper.class);
        InventorySnapshotSourceMapper sourceMapper = mock(InventorySnapshotSourceMapper.class);
        InventoryHistoryLockMapper lockMapper = mock(InventoryHistoryLockMapper.class);
        StockMovementEventMapper eventMapper = mock(StockMovementEventMapper.class);
        when(runMapper.findMaxRevision(any(), any())).thenReturn(2);
        when(runMapper.insert(any())).thenReturn(1);
        when(runMapper.updateById(any())).thenReturn(1);
        when(snapshotMapper.insert(any())).thenReturn(1);
        when(sourceMapper.listCurrentAggregates()).thenReturn(List.of(aggregate()));
        InventorySnapshotService service =
                new InventorySnapshotService(
                        runMapper, snapshotMapper, sourceMapper, lockMapper, eventMapper, 10
                );

        InventorySnapshotRun run =
                service.capture(LocalDate.of(2026, 7, 29), "MANUAL", "test");

        assertEquals(3, run.getRevision());
        assertEquals("COMPLETED", run.getStatus());
        assertEquals(1, run.getRowCount());
        assertNotNull(run.getContentSha256());
        assertEquals(64, run.getContentSha256().length());

        ArgumentCaptor<InventoryDailySnapshot> rowCaptor =
                ArgumentCaptor.forClass(InventoryDailySnapshot.class);
        verify(snapshotMapper).insert(rowCaptor.capture());
        assertEquals(40, rowCaptor.getValue().getTotalPieces());
        assertEquals(new BigDecimal("1000.0000"), rowCaptor.getValue().getTotalWeightKg());
    }

    @Test
    void rowFailureDoesNotMarkRunCompletedSoTransactionCanRollBack() {
        InventorySnapshotRunMapper runMapper = mock(InventorySnapshotRunMapper.class);
        InventoryDailySnapshotMapper snapshotMapper = mock(InventoryDailySnapshotMapper.class);
        InventorySnapshotSourceMapper sourceMapper = mock(InventorySnapshotSourceMapper.class);
        InventoryHistoryLockMapper lockMapper = mock(InventoryHistoryLockMapper.class);
        StockMovementEventMapper eventMapper = mock(StockMovementEventMapper.class);
        when(runMapper.findMaxRevision(any(), any())).thenReturn(0);
        when(runMapper.insert(any())).thenReturn(1);
        when(sourceMapper.listCurrentAggregates()).thenReturn(List.of(aggregate()));
        when(snapshotMapper.insert(any())).thenThrow(new IllegalStateException("write failed"));
        InventorySnapshotService service =
                new InventorySnapshotService(
                        runMapper, snapshotMapper, sourceMapper, lockMapper, eventMapper, 10
                );

        assertThrows(IllegalStateException.class,
                () -> service.capture(LocalDate.of(2026, 7, 29), "DAILY_CLOSE", "test"));
        verify(runMapper, never()).updateById(any());
    }

    @Test
    void dailyCloseIsIdempotentAfterCompletedRunExists() {
        InventorySnapshotRunMapper runMapper = mock(InventorySnapshotRunMapper.class);
        InventoryDailySnapshotMapper snapshotMapper = mock(InventoryDailySnapshotMapper.class);
        InventorySnapshotSourceMapper sourceMapper = mock(InventorySnapshotSourceMapper.class);
        InventoryHistoryLockMapper lockMapper = mock(InventoryHistoryLockMapper.class);
        StockMovementEventMapper eventMapper = mock(StockMovementEventMapper.class);
        InventorySnapshotRun existing = new InventorySnapshotRun();
        existing.setSnapshotRunId("snap_existing");
        existing.setSnapshotDate(LocalDate.of(2026, 7, 29));
        existing.setSnapshotType("DAILY_CLOSE");
        existing.setStatus("COMPLETED");
        existing.setDataAsOf(LocalDateTime.of(2026, 7, 30, 0, 1));
        when(lockMapper.acquire(any(), any(Integer.class))).thenReturn(1);
        when(runMapper.findLatestCompleted(LocalDate.of(2026, 7, 29), "DAILY_CLOSE"))
                .thenReturn(existing);
        InventorySnapshotService service =
                new InventorySnapshotService(
                        runMapper, snapshotMapper, sourceMapper, lockMapper, eventMapper, 10
                );

        InventorySnapshotRun result =
                service.captureDailyCloseIfAbsent(LocalDate.of(2026, 7, 29));

        assertEquals("snap_existing", result.getSnapshotRunId());
        verify(sourceMapper, never()).listCurrentAggregates();
        verify(lockMapper).release("inventory-daily-close:2026-07-29");
    }

    @Test
    void completedSnapshotOutsideTrustedWindowIsNotReused() {
        InventorySnapshotRunMapper runMapper = mock(InventorySnapshotRunMapper.class);
        InventoryDailySnapshotMapper snapshotMapper = mock(InventoryDailySnapshotMapper.class);
        InventorySnapshotSourceMapper sourceMapper = mock(InventorySnapshotSourceMapper.class);
        InventoryHistoryLockMapper lockMapper = mock(InventoryHistoryLockMapper.class);
        StockMovementEventMapper eventMapper = mock(StockMovementEventMapper.class);
        InventorySnapshotRun existing = new InventorySnapshotRun();
        existing.setSnapshotRunId("snap_late");
        existing.setSnapshotDate(LocalDate.of(2026, 7, 29));
        existing.setSnapshotType("DAILY_CLOSE");
        existing.setStatus("COMPLETED");
        existing.setDataAsOf(LocalDateTime.of(2026, 7, 30, 12, 0));
        when(lockMapper.acquire(any(), any(Integer.class))).thenReturn(1);
        when(runMapper.findLatestCompleted(LocalDate.of(2026, 7, 29), "DAILY_CLOSE"))
                .thenReturn(existing);
        InventorySnapshotService service = new InventorySnapshotService(
                runMapper, snapshotMapper, sourceMapper, lockMapper, eventMapper, 10
        );

        InventorySnapshotCaptureRejectedException failure = assertThrows(
                InventorySnapshotCaptureRejectedException.class,
                () -> service.captureDailyCloseWithOutcome(
                        LocalDate.of(2026, 7, 29),
                        LocalDateTime.of(2026, 7, 30, 12, 1),
                        "test"
                )
        );

        assertEquals("SNAPSHOT_OUTSIDE_TRUSTED_WINDOW", failure.getErrorCode());
        verify(sourceMapper, never()).listCurrentAggregates();
    }

    @Test
    void dailyCloseRejectsLateBackfillInsteadOfInventingHistory() {
        InventorySnapshotRunMapper runMapper = mock(InventorySnapshotRunMapper.class);
        InventoryDailySnapshotMapper snapshotMapper = mock(InventoryDailySnapshotMapper.class);
        InventorySnapshotSourceMapper sourceMapper = mock(InventorySnapshotSourceMapper.class);
        InventoryHistoryLockMapper lockMapper = mock(InventoryHistoryLockMapper.class);
        StockMovementEventMapper eventMapper = mock(StockMovementEventMapper.class);
        when(lockMapper.acquire(any(), any(Integer.class))).thenReturn(1);
        InventorySnapshotService service = new InventorySnapshotService(
                runMapper, snapshotMapper, sourceMapper, lockMapper, eventMapper, 10
        );

        InventorySnapshotCaptureRejectedException failure = assertThrows(
                InventorySnapshotCaptureRejectedException.class,
                () -> service.captureDailyCloseWithOutcome(
                        LocalDate.of(2026, 7, 29),
                        LocalDateTime.of(2026, 7, 30, 0, 11),
                        "test"
                )
        );

        assertEquals("CAPTURE_WINDOW_MISSED", failure.getErrorCode());
        verify(sourceMapper, never()).listCurrentAggregates();
        verify(lockMapper).release("inventory-daily-close:2026-07-29");
    }

    @Test
    void dailyCloseRejectsPostBoundaryMovement() {
        InventorySnapshotRunMapper runMapper = mock(InventorySnapshotRunMapper.class);
        InventoryDailySnapshotMapper snapshotMapper = mock(InventoryDailySnapshotMapper.class);
        InventorySnapshotSourceMapper sourceMapper = mock(InventorySnapshotSourceMapper.class);
        InventoryHistoryLockMapper lockMapper = mock(InventoryHistoryLockMapper.class);
        StockMovementEventMapper eventMapper = mock(StockMovementEventMapper.class);
        when(lockMapper.acquire(any(), any(Integer.class))).thenReturn(1);
        when(eventMapper.countOccurredBetween(any(), any())).thenReturn(1);
        InventorySnapshotService service = new InventorySnapshotService(
                runMapper, snapshotMapper, sourceMapper, lockMapper, eventMapper, 10
        );

        InventorySnapshotCaptureRejectedException failure = assertThrows(
                InventorySnapshotCaptureRejectedException.class,
                () -> service.captureDailyCloseWithOutcome(
                        LocalDate.of(2026, 7, 29),
                        LocalDateTime.of(2026, 7, 30, 0, 5),
                        "test"
                )
        );

        assertEquals("POST_BOUNDARY_MOVEMENT_DETECTED", failure.getErrorCode());
        verify(sourceMapper, never()).listCurrentAggregates();
        verify(lockMapper).release("inventory-daily-close:2026-07-29");
    }

    private InventorySnapshotAggregate aggregate() {
        InventorySnapshotAggregate aggregate = new InventorySnapshotAggregate();
        aggregate.setProductId(10);
        aggregate.setProductName("黄冰糖（袋）");
        aggregate.setProductStatus("成品");
        aggregate.setWarehouseId(2);
        aggregate.setWarehouseName("2号库位");
        aggregate.setProductionDate(LocalDate.of(2026, 5, 22));
        aggregate.setBoardCount(1);
        aggregate.setLoosePieceCount(0);
        aggregate.setTotalPieces(40);
        aggregate.setTotalWeightKg(new BigDecimal("1000.0000"));
        aggregate.setInventoryRecordCount(1);
        aggregate.setPalletCount(1);
        aggregate.setPiecesPerPallet(40);
        aggregate.setWeightPerPiece(new BigDecimal("25.000000"));
        return aggregate;
    }
}
