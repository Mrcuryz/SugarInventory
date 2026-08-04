package com.Laibin.SugarInventory.analytics.task;

import com.Laibin.SugarInventory.analytics.service.impl.RegisteredReportRetentionService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "agent.reports.retention",
        name = "cleanup-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class RegisteredReportRetentionTask {
    private static final Logger log = LoggerFactory.getLogger(RegisteredReportRetentionTask.class);

    private final RegisteredReportRetentionService retentionService;

    @Value("${agent.reports.retention.cleanup-batch-size:1000}")
    private int cleanupBatchSize;

    @Scheduled(
            cron = "${agent.reports.retention.cleanup-cron:0 30 2 * * ?}",
            zone = "Asia/Shanghai")
    public void cleanupExpiredReports() {
        try {
            RegisteredReportRetentionService.CleanupResult result =
                    retentionService.cleanupExpiredBatch(cleanupBatchSize);
            log.info(
                    "历史报表过期清理完成，cleanupRunId={}，selected={}，deletedReports={}",
                    result.cleanupRunId(),
                    result.selectedReportCount(),
                    result.deletedReportRunCount());
        } catch (RuntimeException exception) {
            try {
                retentionService.recordFailure(cleanupBatchSize, exception);
            } catch (RuntimeException auditException) {
                exception.addSuppressed(auditException);
            }
            log.error("历史报表过期清理失败", exception);
        }
    }
}
