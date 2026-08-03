package com.Laibin.SugarInventory.inventoryhistory;

import com.Laibin.SugarInventory.inventoryhistory.domain.InventoryHistoryJobRun;
import com.Laibin.SugarInventory.inventoryhistory.domain.InventoryReconciliationRun;
import com.Laibin.SugarInventory.inventoryhistory.domain.InventorySnapshotRun;
import com.Laibin.SugarInventory.inventoryhistory.mapper.InventorySnapshotRunMapper;
import com.Laibin.SugarInventory.inventoryhistory.service.InventoryHistoryDailyJobService;
import com.Laibin.SugarInventory.inventoryhistory.service.InventoryHistoryJobEvidenceService;
import com.Laibin.SugarInventory.inventoryhistory.service.InventoryHistoryJobResult;
import com.Laibin.SugarInventory.inventoryhistory.service.InventoryReconciliationService;
import com.Laibin.SugarInventory.inventoryhistory.service.InventorySnapshotCaptureRejectedException;
import com.Laibin.SugarInventory.inventoryhistory.service.InventorySnapshotCaptureResult;
import com.Laibin.SugarInventory.inventoryhistory.service.InventorySnapshotService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InventoryHistoryDailyJobServiceTest {
    private static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 7, 31);
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-07-31T16:05:00Z"),
            ZoneOffset.UTC
    );

    @Test
    void capturePersistsEvidenceAndReturnsHealthyWhenReconciliationPassed() {
        Fixture fixture = fixture();
        InventorySnapshotRun snapshot = snapshot("snap_1");
        InventoryReconciliationRun reconciliation = reconciliation("PASSED");
        when(fixture.snapshotService.captureDailyCloseWithOutcome(
                eq(BUSINESS_DATE), any(), any()
        )).thenReturn(new InventorySnapshotCaptureResult(snapshot, false));
        when(fixture.reconciliationService.reconcileIfNeeded(BUSINESS_DATE))
                .thenReturn(reconciliation);

        InventoryHistoryJobResult result =
                fixture.service.captureLatestClosedDay("EXTERNAL_ONE_SHOT");

        assertTrue(result.operationalSuccess());
        assertTrue(result.dataQualityPassed());
        assertFalse(result.snapshotReused());
        assertEquals(0, result.exitCode());
        verify(fixture.evidenceService).complete(
                eq(fixture.evidence),
                eq(snapshot),
                eq(false),
                eq(reconciliation),
                any()
        );
    }

    @Test
    void captureLeavesFailureEvidenceWhenTrustedWindowWasMissed() {
        Fixture fixture = fixture();
        when(fixture.snapshotService.captureDailyCloseWithOutcome(
                eq(BUSINESS_DATE), any(), any()
        )).thenThrow(new InventorySnapshotCaptureRejectedException(
                "CAPTURE_WINDOW_MISSED",
                "已错过可信采集窗口"
        ));

        InventoryHistoryJobResult result =
                fixture.service.captureLatestClosedDay("EXTERNAL_ONE_SHOT");

        assertFalse(result.operationalSuccess());
        assertEquals(2, result.exitCode());
        verify(fixture.evidenceService).fail(
                eq(fixture.evidence),
                eq("CAPTURE_WINDOW_MISSED"),
                eq("已错过可信采集窗口"),
                any()
        );
    }

    @Test
    void verifyFailsWhenDailyCloseIsMissing() {
        Fixture fixture = fixture();
        when(fixture.snapshotRunMapper.findLatestCompleted(BUSINESS_DATE, "DAILY_CLOSE"))
                .thenReturn(null);

        InventoryHistoryJobResult result =
                fixture.service.verifyLatestClosedDay("EXTERNAL_VERIFY");

        assertFalse(result.operationalSuccess());
        assertEquals(2, result.exitCode());
        verify(fixture.evidenceService).fail(
                eq(fixture.evidence),
                eq("MISSING_DAILY_CLOSE"),
                any(),
                any()
        );
    }

    @Test
    void completedJobUsesDistinctExitCodeWhenDataQualityIsBlocked() {
        Fixture fixture = fixture();
        InventorySnapshotRun snapshot = snapshot("snap_2");
        InventoryReconciliationRun reconciliation = reconciliation("INSUFFICIENT_DATA");
        when(fixture.snapshotService.captureDailyCloseWithOutcome(
                eq(BUSINESS_DATE), any(), any()
        )).thenReturn(new InventorySnapshotCaptureResult(snapshot, true));
        when(fixture.reconciliationService.reconcileIfNeeded(BUSINESS_DATE))
                .thenReturn(reconciliation);

        InventoryHistoryJobResult result =
                fixture.service.captureLatestClosedDay("IN_PROCESS_SCHEDULED");

        assertTrue(result.operationalSuccess());
        assertFalse(result.dataQualityPassed());
        assertTrue(result.snapshotReused());
        assertEquals(3, result.exitCode());
    }

    private Fixture fixture() {
        InventorySnapshotService snapshotService = mock(InventorySnapshotService.class);
        InventoryReconciliationService reconciliationService =
                mock(InventoryReconciliationService.class);
        InventorySnapshotRunMapper snapshotRunMapper = mock(InventorySnapshotRunMapper.class);
        InventoryHistoryJobEvidenceService evidenceService =
                mock(InventoryHistoryJobEvidenceService.class);
        InventoryHistoryJobRun evidence = new InventoryHistoryJobRun();
        evidence.setJobRunId("invjob_test");
        when(evidenceService.start(eq(BUSINESS_DATE), any(), any(), any()))
                .thenReturn(evidence);
        InventoryHistoryDailyJobService service = new InventoryHistoryDailyJobService(
                snapshotService,
                reconciliationService,
                snapshotRunMapper,
                evidenceService,
                CLOCK
        );
        return new Fixture(
                service,
                snapshotService,
                reconciliationService,
                snapshotRunMapper,
                evidenceService,
                evidence
        );
    }

    private InventorySnapshotRun snapshot(String id) {
        InventorySnapshotRun snapshot = new InventorySnapshotRun();
        snapshot.setSnapshotRunId(id);
        return snapshot;
    }

    private InventoryReconciliationRun reconciliation(String status) {
        InventoryReconciliationRun reconciliation = new InventoryReconciliationRun();
        reconciliation.setReconciliationRunId("recon_1");
        reconciliation.setStatus(status);
        return reconciliation;
    }

    private record Fixture(
            InventoryHistoryDailyJobService service,
            InventorySnapshotService snapshotService,
            InventoryReconciliationService reconciliationService,
            InventorySnapshotRunMapper snapshotRunMapper,
            InventoryHistoryJobEvidenceService evidenceService,
            InventoryHistoryJobRun evidence
    ) {
    }
}
