package com.Laibin.SugarInventory.analytics.service.impl;

import com.Laibin.SugarInventory.analytics.domain.ReportExecutionWindowAggregate;
import com.Laibin.SugarInventory.analytics.domain.po.RegisteredReportCleanupRunPO;
import com.Laibin.SugarInventory.analytics.domain.po.RegisteredReportExecutionRunPO;
import com.Laibin.SugarInventory.analytics.domain.vo.RegisteredReportOperationsStatusVO;
import com.Laibin.SugarInventory.analytics.mapper.RegisteredReportCleanupRunMapper;
import com.Laibin.SugarInventory.analytics.mapper.RegisteredReportExecutionRunMapper;
import com.Laibin.SugarInventory.analytics.mapper.RegisteredReportExportAuditMapper;
import com.Laibin.SugarInventory.analytics.mapper.RegisteredReportRunMapper;
import com.Laibin.SugarInventory.inventoryhistory.domain.InventoryTrendReleaseGate;
import com.Laibin.SugarInventory.inventoryhistory.mapper.InventoryTrendReleaseGateMapper;
import com.Laibin.SugarInventory.inventoryhistory.service.InventoryReconciliationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RegisteredReportOperationsReadService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private final RegisteredReportExecutionRunMapper executionRunMapper;
    private final RegisteredReportRunMapper reportRunMapper;
    private final RegisteredReportExportAuditMapper exportAuditMapper;
    private final RegisteredReportCleanupRunMapper cleanupRunMapper;
    private final InventoryTrendReleaseGateMapper trendReleaseGateMapper;
    private final Clock inventoryHistoryClock;

    public RegisteredReportOperationsStatusVO latestStatus() {
        LocalDateTime windowEndedAt = now();
        LocalDateTime windowStartedAt = windowEndedAt.minusHours(24);
        ReportExecutionWindowAggregate aggregate = executionRunMapper.aggregateWindow(
                windowStartedAt, windowEndedAt);
        RegisteredReportExecutionRunPO latest = executionRunMapper.findLatest(
                windowStartedAt, windowEndedAt);
        RegisteredReportExecutionRunPO latestRejection = executionRunMapper.findLatestRejection(
                windowStartedAt, windowEndedAt);
        RegisteredReportExecutionRunPO latestFailure = executionRunMapper.findLatestFailure(
                windowStartedAt, windowEndedAt);
        RegisteredReportCleanupRunPO cleanup = cleanupRunMapper.findLatest();
        InventoryTrendReleaseGate gate = trendReleaseGateMapper.findByGateKey(
                InventoryReconciliationService.GATE_KEY);

        long executionCount = value(aggregate == null ? null : aggregate.getExecutionCount());
        long successCount = value(aggregate == null ? null : aggregate.getSuccessCount());
        long rejectionCount = value(aggregate == null ? null : aggregate.getRejectionCount());
        long failureCount = value(aggregate == null ? null : aggregate.getFailureCount());
        long partialCount = value(aggregate == null ? null : aggregate.getPartialDataCount());
        return RegisteredReportOperationsStatusVO.builder()
                .windowStartedAt(windowStartedAt)
                .windowEndedAt(windowEndedAt)
                .executionCount(executionCount)
                .successCount(successCount)
                .rejectionCount(rejectionCount)
                .failureCount(failureCount)
                .partialDataCount(partialCount)
                .durationP50Ms(percentile(
                        windowStartedAt, windowEndedAt, executionCount, 0.50))
                .durationP95Ms(percentile(
                        windowStartedAt, windowEndedAt, executionCount, 0.95))
                .maxDurationMs(value(aggregate == null ? null : aggregate.getMaxDurationMs()))
                .exportCount(exportAuditMapper.countInWindow(windowStartedAt, windowEndedAt))
                .activeSnapshotCount(reportRunMapper.countActive(windowEndedAt))
                .expiredSnapshotCount(reportRunMapper.countExpired(windowEndedAt))
                .latestExecutionStatus(executionStatus(latest))
                .latestExecutionReportDefinitionId(
                        latest == null ? null : latest.getReportDefinitionId())
                .latestExecutionAt(latest == null ? null : latest.getStartedAt())
                .latestRejectionSummary(latestRejection == null
                        ? null : latestRejection.getFailureSummary())
                .latestFailureSummary(latestFailure == null
                        ? null : latestFailure.getFailureSummary())
                .latestCleanupStatus(cleanupStatus(cleanup))
                .latestCleanupSelectedCount(cleanup == null
                        ? null : cleanup.getSelectedReportCount())
                .latestCleanupDeletedCount(cleanup == null
                        ? null : cleanup.getDeletedReportRunCount())
                .latestCleanupAt(cleanup == null ? null : cleanup.getStartedAt())
                .inventoryTrendGateStatus(gateStatus(gate))
                .inventoryTrendConsecutivePassedDays(gate == null
                        ? 0 : gate.getConsecutivePassedDays())
                .inventoryTrendRequiredPassedDays(gate == null
                        ? 0 : gate.getRequiredPassedDays())
                .summary(summary(
                        executionCount, rejectionCount, failureCount, partialCount, gate))
                .build();
    }

    private LocalDateTime now() {
        return LocalDateTime.now(inventoryHistoryClock.withZone(BUSINESS_ZONE));
    }

    private long percentile(
            LocalDateTime windowStartedAt,
            LocalDateTime windowEndedAt,
            long count,
            double percentile) {
        if (count <= 0) {
            return 0L;
        }
        long offset = Math.max(0L, (long) Math.ceil(percentile * count) - 1L);
        Long value = executionRunMapper.findDurationAtOffset(
                windowStartedAt, windowEndedAt, Math.min(offset, count - 1L));
        return value == null ? 0L : value;
    }

    private static long value(Long value) {
        return value == null ? 0L : value;
    }

    private static String executionStatus(RegisteredReportExecutionRunPO latest) {
        if (latest == null) {
            return "尚无报表运行记录";
        }
        return switch (latest.getStatus()) {
            case "SUCCEEDED" -> "运行成功";
            case "REJECTED" -> "运行被业务或权限规则拒绝";
            case "FAILED" -> "运行失败";
            default -> "状态待确认";
        };
    }

    private static String cleanupStatus(RegisteredReportCleanupRunPO cleanup) {
        if (cleanup == null) {
            return "尚无清理运行记录";
        }
        return switch (cleanup.getStatus()) {
            case "SUCCESS" -> "清理成功";
            case "FAILED" -> "清理失败";
            case "STARTED" -> "正在清理";
            default -> "状态待确认";
        };
    }

    private static String gateStatus(InventoryTrendReleaseGate gate) {
        return gate != null && "ELIGIBLE".equals(gate.getStatus())
                ? "已具备进入产品评审的条件"
                : "暂不可开放库存趋势";
    }

    private static String summary(
            long executionCount,
            long rejectionCount,
            long failureCount,
            long partialCount,
            InventoryTrendReleaseGate gate) {
        int passed = gate == null || gate.getConsecutivePassedDays() == null
                ? 0 : gate.getConsecutivePassedDays();
        int required = gate == null || gate.getRequiredPassedDays() == null
                ? 0 : gate.getRequiredPassedDays();
        return "最近24小时运行 " + executionCount + " 次，拒绝 " + rejectionCount
                + " 次，失败 " + failureCount
                + " 次，部分数据 " + partialCount + " 次；库存趋势门禁 "
                + passed + "/" + required + " 天";
    }
}
