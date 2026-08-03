package com.Laibin.SugarInventory.inventoryhistory.service;

import com.Laibin.SugarInventory.inventoryhistory.domain.InventoryHistoryJobRun;
import com.Laibin.SugarInventory.inventoryhistory.domain.InventoryReconciliationRun;
import com.Laibin.SugarInventory.inventoryhistory.domain.InventorySnapshotRun;
import com.Laibin.SugarInventory.inventoryhistory.domain.InventoryTrendReleaseGate;
import com.Laibin.SugarInventory.inventoryhistory.domain.vo.InventoryHistoryOperationsStatusVO;
import com.Laibin.SugarInventory.inventoryhistory.mapper.InventoryHistoryJobRunMapper;
import com.Laibin.SugarInventory.inventoryhistory.mapper.InventoryReconciliationRunMapper;
import com.Laibin.SugarInventory.inventoryhistory.mapper.InventorySnapshotRunMapper;
import com.Laibin.SugarInventory.inventoryhistory.mapper.InventoryTrendReleaseGateMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
public class InventoryHistoryOperationsReadService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private final InventorySnapshotRunMapper snapshotRunMapper;
    private final InventoryReconciliationRunMapper reconciliationRunMapper;
    private final InventoryHistoryJobRunMapper jobRunMapper;
    private final InventoryTrendReleaseGateMapper gateMapper;
    private final Clock inventoryHistoryClock;

    public InventoryHistoryOperationsStatusVO latestStatus() {
        LocalDate businessDate = LocalDate.now(inventoryHistoryClock.withZone(BUSINESS_ZONE))
                .minusDays(1);
        InventorySnapshotRun snapshot = snapshotRunMapper.findLatestCompleted(
                businessDate,
                "DAILY_CLOSE"
        );
        InventoryReconciliationRun reconciliation =
                reconciliationRunMapper.findLatest(businessDate);
        InventoryHistoryJobRun job = jobRunMapper.findLatest(businessDate);
        InventoryTrendReleaseGate gate = gateMapper.findByGateKey(
                InventoryReconciliationService.GATE_KEY
        );

        return InventoryHistoryOperationsStatusVO.builder()
                .businessDate(businessDate)
                .snapshotAvailable(snapshot != null)
                .snapshotStatus(snapshot == null ? "缺少可信日终快照" : "日终快照已生成")
                .snapshotRunId(snapshot == null ? null : snapshot.getSnapshotRunId())
                .dataAsOf(snapshot == null ? null : snapshot.getDataAsOf())
                .reconciliationStatus(reconciliationLabel(reconciliation))
                .reconciliationSummary(
                        reconciliation == null ? "尚未执行库存守恒对账" : reconciliation.getReason()
                )
                .latestExecutionStatus(executionStatusLabel(job))
                .latestExecutionMode(job == null ? null : modeLabel(job.getJobMode()))
                .latestTriggerSource(job == null ? null : triggerLabel(job.getTriggerSource()))
                .latestFailureSummary(job == null ? null : job.getErrorMessage())
                .latestExecutionAt(job == null ? null : job.getStartedAt())
                .trendGateStatus(gateLabel(gate))
                .consecutivePassedDays(gate == null ? 0 : gate.getConsecutivePassedDays())
                .requiredPassedDays(gate == null ? 0 : gate.getRequiredPassedDays())
                .trendGateSummary(gate == null ? "库存趋势发布门禁尚未初始化" : gate.getReason())
                .build();
    }

    private String reconciliationLabel(InventoryReconciliationRun reconciliation) {
        if (reconciliation == null) {
            return "未执行";
        }
        return switch (reconciliation.getStatus()) {
            case "PASSED" -> "守恒对账通过";
            case "FAILED" -> "守恒对账未通过";
            case "INSUFFICIENT_DATA" -> "数据不足，暂无法对账";
            case "BUILDING" -> "正在对账";
            default -> "状态待确认";
        };
    }

    private String executionStatusLabel(InventoryHistoryJobRun job) {
        if (job == null) {
            return "尚无运行记录";
        }
        return switch (job.getStatus()) {
            case "SUCCEEDED" -> "执行完成";
            case "FAILED" -> "执行失败";
            case "RUNNING" -> "正在执行";
            default -> "状态待确认";
        };
    }

    private String modeLabel(String mode) {
        return "CAPTURE".equals(mode) ? "生成日终快照" : "核验日终任务";
    }

    private String triggerLabel(String triggerSource) {
        return switch (triggerSource) {
            case "EXTERNAL_ONE_SHOT" -> "外部可靠调度";
            case "IN_PROCESS_SCHEDULED" -> "应用内冗余调度";
            case "APPLICATION_STARTUP_VERIFY" -> "应用启动检查";
            case "EXTERNAL_VERIFY" -> "外部健康检查";
            default -> "系统任务";
        };
    }

    private String gateLabel(InventoryTrendReleaseGate gate) {
        if (gate == null || !"ELIGIBLE".equals(gate.getStatus())) {
            return "暂不可开放库存趋势";
        }
        return "已具备进入产品评审的条件";
    }
}
