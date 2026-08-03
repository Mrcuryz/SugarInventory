package com.Laibin.SugarInventory.inventoryhistory.service;

import com.Laibin.SugarInventory.inventoryhistory.domain.InventoryHistoryJobRun;
import com.Laibin.SugarInventory.inventoryhistory.domain.InventoryReconciliationRun;
import com.Laibin.SugarInventory.inventoryhistory.domain.InventorySnapshotRun;
import com.Laibin.SugarInventory.inventoryhistory.mapper.InventorySnapshotRunMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
public class InventoryHistoryDailyJobService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private final InventorySnapshotService snapshotService;
    private final InventoryReconciliationService reconciliationService;
    private final InventorySnapshotRunMapper snapshotRunMapper;
    private final InventoryHistoryJobEvidenceService evidenceService;
    private final Clock inventoryHistoryClock;

    public InventoryHistoryJobResult captureLatestClosedDay(String triggerSource) {
        LocalDateTime now = now();
        LocalDate businessDate = now.toLocalDate().minusDays(1);
        InventoryHistoryJobRun evidence = evidenceService.start(
                businessDate,
                "CAPTURE",
                triggerSource,
                now
        );
        try {
            InventorySnapshotCaptureResult capture =
                    snapshotService.captureDailyCloseWithOutcome(
                            businessDate,
                            now,
                            captureReason(triggerSource)
                    );
            InventoryReconciliationRun reconciliation =
                    reconciliationService.reconcileIfNeeded(businessDate);
            evidenceService.complete(
                    evidence,
                    capture.snapshotRun(),
                    capture.reused(),
                    reconciliation,
                    now()
            );
            return successResult(
                    businessDate,
                    "CAPTURE",
                    capture.snapshotRun(),
                    capture.reused(),
                    reconciliation
            );
        } catch (RuntimeException failure) {
            evidenceService.fail(evidence, errorCode(failure), failure.getMessage(), now());
            return failureResult(businessDate, "CAPTURE", failure);
        }
    }

    public InventoryHistoryJobResult verifyLatestClosedDay(String triggerSource) {
        LocalDateTime now = now();
        LocalDate businessDate = now.toLocalDate().minusDays(1);
        InventoryHistoryJobRun evidence = evidenceService.start(
                businessDate,
                "VERIFY",
                triggerSource,
                now
        );
        try {
            InventorySnapshotRun snapshot = snapshotRunMapper.findLatestCompleted(
                    businessDate,
                    "DAILY_CLOSE"
            );
            if (snapshot == null) {
                throw new InventorySnapshotCaptureRejectedException(
                        "MISSING_DAILY_CLOSE",
                        "缺少 " + businessDate + " 的可信日终库存快照"
                );
            }
            snapshotService.assertTrustedCompletedDailyClose(snapshot);
            InventoryReconciliationRun reconciliation =
                    reconciliationService.reconcileIfNeeded(businessDate);
            if (reconciliation == null || !isTerminal(reconciliation.getStatus())) {
                throw new InventorySnapshotCaptureRejectedException(
                        "RECONCILIATION_NOT_TERMINAL",
                        "库存守恒对账尚未形成终态结果"
                );
            }
            evidenceService.complete(evidence, snapshot, true, reconciliation, now());
            return successResult(
                    businessDate,
                    "VERIFY",
                    snapshot,
                    true,
                    reconciliation
            );
        } catch (RuntimeException failure) {
            evidenceService.fail(evidence, errorCode(failure), failure.getMessage(), now());
            return failureResult(businessDate, "VERIFY", failure);
        }
    }

    private InventoryHistoryJobResult successResult(
            LocalDate businessDate,
            String mode,
            InventorySnapshotRun snapshot,
            boolean reused,
            InventoryReconciliationRun reconciliation
    ) {
        boolean passed = reconciliation != null && "PASSED".equals(reconciliation.getStatus());
        return new InventoryHistoryJobResult(
                businessDate,
                mode,
                true,
                passed,
                reused,
                snapshot == null ? null : snapshot.getSnapshotRunId(),
                reconciliation == null ? null : reconciliation.getReconciliationRunId(),
                reconciliation == null ? null : reconciliation.getStatus(),
                passed ? "日终快照和库存守恒对账均已通过" : "日终任务已完成，但库存趋势数据质量门禁仍未通过"
        );
    }

    private InventoryHistoryJobResult failureResult(
            LocalDate businessDate,
            String mode,
            RuntimeException failure
    ) {
        return new InventoryHistoryJobResult(
                businessDate,
                mode,
                false,
                false,
                false,
                null,
                null,
                null,
                failure.getMessage()
        );
    }

    private String captureReason(String triggerSource) {
        return switch (triggerSource) {
            case "EXTERNAL_ONE_SHOT" -> "北京时间日终外部可靠调度快照";
            case "IN_PROCESS_SCHEDULED" -> "北京时间日终应用内冗余快照";
            default -> "北京时间日终库存快照";
        };
    }

    private String errorCode(RuntimeException failure) {
        if (failure instanceof InventorySnapshotCaptureRejectedException rejected) {
            return rejected.getErrorCode();
        }
        return "JOB_EXECUTION_FAILED";
    }

    private boolean isTerminal(String status) {
        return "PASSED".equals(status)
                || "FAILED".equals(status)
                || "INSUFFICIENT_DATA".equals(status);
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(inventoryHistoryClock.instant(), BUSINESS_ZONE);
    }
}
