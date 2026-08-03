package com.Laibin.SugarInventory.inventoryhistory.service;

import com.Laibin.SugarInventory.inventoryhistory.domain.InventoryHistoryJobRun;
import com.Laibin.SugarInventory.inventoryhistory.domain.InventoryReconciliationRun;
import com.Laibin.SugarInventory.inventoryhistory.domain.InventorySnapshotRun;
import com.Laibin.SugarInventory.inventoryhistory.mapper.InventoryHistoryJobRunMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InventoryHistoryJobEvidenceService {
    private final InventoryHistoryJobRunMapper mapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public InventoryHistoryJobRun start(
            LocalDate businessDate,
            String mode,
            String triggerSource,
            LocalDateTime startedAt
    ) {
        InventoryHistoryJobRun run = new InventoryHistoryJobRun();
        run.setJobRunId("invjob_" + UUID.randomUUID().toString().replace("-", ""));
        run.setBusinessDate(businessDate);
        run.setJobMode(mode);
        run.setTriggerSource(triggerSource);
        run.setStatus("RUNNING");
        run.setSnapshotReused(false);
        run.setExecutorInstance(executorInstance());
        run.setStartedAt(startedAt);
        run.setCreatedAt(startedAt);
        run.setUpdatedAt(startedAt);
        mapper.insert(run);
        return run;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(
            InventoryHistoryJobRun evidence,
            InventorySnapshotRun snapshot,
            boolean snapshotReused,
            InventoryReconciliationRun reconciliation,
            LocalDateTime completedAt
    ) {
        evidence.setStatus("SUCCEEDED");
        evidence.setSnapshotRunId(snapshot == null ? null : snapshot.getSnapshotRunId());
        evidence.setSnapshotReused(snapshotReused);
        evidence.setReconciliationRunId(
                reconciliation == null ? null : reconciliation.getReconciliationRunId()
        );
        evidence.setReconciliationStatus(
                reconciliation == null ? null : reconciliation.getStatus()
        );
        evidence.setErrorCode(null);
        evidence.setErrorMessage(null);
        evidence.setCompletedAt(completedAt);
        evidence.setUpdatedAt(completedAt);
        mapper.updateById(evidence);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(
            InventoryHistoryJobRun evidence,
            String errorCode,
            String errorMessage,
            LocalDateTime completedAt
    ) {
        evidence.setStatus("FAILED");
        evidence.setErrorCode(errorCode);
        evidence.setErrorMessage(limit(errorMessage, 500));
        evidence.setCompletedAt(completedAt);
        evidence.setUpdatedAt(completedAt);
        mapper.updateById(evidence);
    }

    private String executorInstance() {
        try {
            return limit(InetAddress.getLocalHost().getHostName(), 128);
        } catch (UnknownHostException ignored) {
            return "unknown-host";
        }
    }

    private String limit(String value, int maximumLength) {
        if (value == null || value.length() <= maximumLength) {
            return value;
        }
        return value.substring(0, maximumLength);
    }
}
