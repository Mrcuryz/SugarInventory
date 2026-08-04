package com.Laibin.SugarInventory.analytics.service.impl;

import com.Laibin.SugarInventory.analytics.domain.po.RegisteredReportCleanupRunPO;
import com.Laibin.SugarInventory.analytics.domain.po.RegisteredReportRunPO;
import com.Laibin.SugarInventory.analytics.mapper.RegisteredReportCleanupRunMapper;
import com.Laibin.SugarInventory.analytics.mapper.RegisteredReportRunMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RegisteredReportRetentionService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final int DEFAULT_BATCH_SIZE = 1000;
    private static final int MAX_BATCH_SIZE = 5000;

    private final RegisteredReportRunMapper reportRunMapper;
    private final RegisteredReportCleanupRunMapper cleanupRunMapper;
    private final Clock inventoryHistoryClock;

    @Transactional
    public CleanupResult cleanupExpiredBatch(int requestedBatchSize) {
        int batchSize = normalizeBatchSize(requestedBatchSize);
        LocalDateTime startedAt = now();
        RegisteredReportCleanupRunPO audit = newAudit(startedAt, batchSize, "STARTED");
        cleanupRunMapper.insert(audit);

        List<String> reportRunIds = reportRunMapper.selectExpiredReportRunIds(startedAt, batchSize);
        int selectedCount = reportRunIds.size();
        int deletedReportRuns = 0;
        if (!reportRunIds.isEmpty()) {
            deletedReportRuns = reportRunMapper.delete(
                    new LambdaQueryWrapper<RegisteredReportRunPO>()
                            .in(RegisteredReportRunPO::getReportRunId, reportRunIds)
                            .le(RegisteredReportRunPO::getExpiresAt, startedAt));
        }

        audit.setSelectedReportCount(selectedCount);
        audit.setDeletedReportRunCount(deletedReportRuns);
        audit.setStatus("SUCCESS");
        audit.setCompletedAt(now());
        cleanupRunMapper.updateById(audit);
        return new CleanupResult(
                audit.getCleanupRunId(),
                selectedCount,
                deletedReportRuns);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(int requestedBatchSize, Throwable failure) {
        LocalDateTime recordedAt = now();
        RegisteredReportCleanupRunPO audit = newAudit(
                recordedAt,
                normalizeBatchSize(requestedBatchSize),
                "FAILED");
        audit.setFailureMessage(safeFailureMessage(failure));
        audit.setCompletedAt(recordedAt);
        cleanupRunMapper.insert(audit);
    }

    private RegisteredReportCleanupRunPO newAudit(
            LocalDateTime startedAt,
            int batchSize,
            String status) {
        RegisteredReportCleanupRunPO audit = new RegisteredReportCleanupRunPO();
        audit.setCleanupRunId("report_cleanup_"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        audit.setCutoffAt(startedAt);
        audit.setBatchSize(batchSize);
        audit.setSelectedReportCount(0);
        audit.setDeletedReportRunCount(0);
        audit.setStatus(status);
        audit.setStartedAt(startedAt);
        return audit;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(inventoryHistoryClock.withZone(BUSINESS_ZONE));
    }

    private static int normalizeBatchSize(int requestedBatchSize) {
        if (requestedBatchSize <= 0) {
            return DEFAULT_BATCH_SIZE;
        }
        return Math.min(requestedBatchSize, MAX_BATCH_SIZE);
    }

    private static String safeFailureMessage(Throwable failure) {
        String message = failure == null ? "未知清理错误" : failure.getMessage();
        String normalized = message == null || message.isBlank()
                ? failure.getClass().getSimpleName()
                : message.replaceAll("[\\r\\n\\t]+", " ").trim();
        return normalized.length() <= 500 ? normalized : normalized.substring(0, 500);
    }

    public record CleanupResult(
            String cleanupRunId,
            int selectedReportCount,
            int deletedReportRunCount) {
    }
}
