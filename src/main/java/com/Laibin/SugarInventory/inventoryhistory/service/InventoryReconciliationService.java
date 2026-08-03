package com.Laibin.SugarInventory.inventoryhistory.service;

import com.Laibin.SugarInventory.inventoryhistory.domain.InventoryBalanceAggregate;
import com.Laibin.SugarInventory.inventoryhistory.domain.InventoryDataQualityIssue;
import com.Laibin.SugarInventory.inventoryhistory.domain.InventoryReconciliationResult;
import com.Laibin.SugarInventory.inventoryhistory.domain.InventoryReconciliationRun;
import com.Laibin.SugarInventory.inventoryhistory.domain.InventorySnapshotRun;
import com.Laibin.SugarInventory.inventoryhistory.domain.InventoryTrendReleaseGate;
import com.Laibin.SugarInventory.inventoryhistory.domain.StockMovementEvent;
import com.Laibin.SugarInventory.inventoryhistory.mapper.InventoryDailySnapshotMapper;
import com.Laibin.SugarInventory.inventoryhistory.mapper.InventoryDataQualityIssueMapper;
import com.Laibin.SugarInventory.inventoryhistory.mapper.InventoryReconciliationResultMapper;
import com.Laibin.SugarInventory.inventoryhistory.mapper.InventoryReconciliationRunMapper;
import com.Laibin.SugarInventory.inventoryhistory.mapper.InventorySnapshotRunMapper;
import com.Laibin.SugarInventory.inventoryhistory.mapper.InventoryTrendReleaseGateMapper;
import com.Laibin.SugarInventory.inventoryhistory.mapper.StockMovementEventMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InventoryReconciliationService {
    public static final String RULE_VERSION = "inventory-reconciliation-v1";
    public static final String GATE_KEY = "INVENTORY_LEVEL_TREND";

    private final InventoryReconciliationRunMapper runMapper;
    private final InventoryReconciliationResultMapper resultMapper;
    private final InventoryDataQualityIssueMapper issueMapper;
    private final InventoryTrendReleaseGateMapper gateMapper;
    private final InventorySnapshotRunMapper snapshotRunMapper;
    private final InventoryDailySnapshotMapper snapshotMapper;
    private final StockMovementEventMapper eventMapper;
    private final InventoryReconciliationCalculator calculator;

    @Value("${inventory.history.minimum-passed-days:7}")
    private int minimumPassedDays;

    @Transactional
    public InventoryReconciliationRun reconcileIfNeeded(LocalDate businessDate) {
        InventorySnapshotRun opening = snapshotRunMapper.findLatestCompleted(
                businessDate.minusDays(1),
                "DAILY_CLOSE"
        );
        InventorySnapshotRun closing = snapshotRunMapper.findLatestCompleted(
                businessDate,
                "DAILY_CLOSE"
        );
        String openingId = opening == null ? null : opening.getSnapshotRunId();
        String closingId = closing == null ? null : closing.getSnapshotRunId();
        InventoryReconciliationRun existing = runMapper.findLatest(businessDate);
        if (existing != null
                && Objects.equals(existing.getOpeningSnapshotRunId(), openingId)
                && Objects.equals(existing.getClosingSnapshotRunId(), closingId)
                && isTerminal(existing.getStatus())) {
            return existing;
        }

        InventoryReconciliationRun run = startRun(businessDate, openingId, closingId);
        if (opening == null || closing == null) {
            List<InventoryReconciliationCalculator.IssueDraft> issues = new ArrayList<>();
            if (opening == null) {
                issues.add(new InventoryReconciliationCalculator.IssueDraft(
                        "MISSING_OPENING_SNAPSHOT",
                        "BLOCKER",
                        "INVENTORY_SNAPSHOT",
                        null,
                        null,
                        null,
                        null,
                        "缺少 " + businessDate.minusDays(1) + " 的日终期初快照",
                        null
                ));
            }
            if (closing == null) {
                issues.add(new InventoryReconciliationCalculator.IssueDraft(
                        "MISSING_CLOSING_SNAPSHOT",
                        "BLOCKER",
                        "INVENTORY_SNAPSHOT",
                        null,
                        null,
                        null,
                        null,
                        "缺少 " + businessDate + " 的日终期末快照",
                        null
                ));
            }
            persistIssues(run, issues);
            completeRun(
                    run,
                    "INSUFFICIENT_DATA",
                    0,
                    0,
                    0,
                    issues.size(),
                    0,
                    "缺少完整期初或期末日终快照"
            );
            updateReleaseGate(businessDate, run);
            return run;
        }

        LocalDateTime start = businessDate.atStartOfDay();
        LocalDateTime end = businessDate.plusDays(1).atStartOfDay();
        List<StockMovementEvent> dailyEvents = eventMapper.listOccurredBetween(start, end);
        List<StockMovementEvent> validationEvents =
                eventMapper.listOccurredBetween(start.minusMinutes(5), end.plusMinutes(5));
        InventoryReconciliationCalculator.Calculation calculation = calculator.calculate(
                snapshotMapper.listProductBalances(openingId),
                snapshotMapper.listProductBalances(closingId),
                snapshotMapper.listWarehouseProductBalances(openingId),
                snapshotMapper.listWarehouseProductBalances(closingId),
                dailyEvents,
                validationEvents
        );
        List<InventoryReconciliationCalculator.IssueDraft> issues =
                new ArrayList<>(calculation.issues());
        if (!Objects.equals(opening.getRuleVersion(), closing.getRuleVersion())) {
            issues.add(new InventoryReconciliationCalculator.IssueDraft(
                    "SNAPSHOT_RULE_VERSION_MISMATCH",
                    "BLOCKER",
                    "INVENTORY_SNAPSHOT",
                    null,
                    null,
                    null,
                    null,
                    "期初与期末快照采用了不同的折算规则版本",
                    null
            ));
        }

        int globalCount = 0;
        int warehouseCount = 0;
        for (InventoryReconciliationCalculator.ResultDraft draft : calculation.results()) {
            InventoryReconciliationResult result = toResult(run, draft);
            resultMapper.insert(result);
            if ("GLOBAL_PRODUCT".equals(result.getDimensionType())) {
                globalCount++;
            } else {
                warehouseCount++;
            }
        }
        persistIssues(run, issues);
        boolean blocker = issues.stream()
                .anyMatch(issue -> "BLOCKER".equals(issue.severity()));
        String status = calculation.failedResultCount() > 0 || blocker
                ? "FAILED"
                : "PASSED";
        completeRun(
                run,
                status,
                globalCount,
                warehouseCount,
                calculation.failedResultCount(),
                issues.size(),
                calculation.maxAbsPieceDifference(),
                "PASSED".equals(status)
                        ? "产品全局和仓库维度守恒"
                        : "存在守恒差异或阻断级数据质量问题"
        );
        updateReleaseGate(businessDate, run);
        return run;
    }

    private InventoryReconciliationRun startRun(
            LocalDate businessDate,
            String openingSnapshotRunId,
            String closingSnapshotRunId
    ) {
        InventoryReconciliationRun run = new InventoryReconciliationRun();
        run.setReconciliationRunId(
                "recon_" + UUID.randomUUID().toString().replace("-", "")
        );
        run.setBusinessDate(businessDate);
        run.setRevision(runMapper.findMaxRevision(businessDate) + 1);
        run.setOpeningSnapshotRunId(openingSnapshotRunId);
        run.setClosingSnapshotRunId(closingSnapshotRunId);
        run.setRuleVersion(RULE_VERSION);
        run.setStatus("BUILDING");
        run.setGlobalResultCount(0);
        run.setWarehouseResultCount(0);
        run.setFailedResultCount(0);
        run.setIssueCount(0);
        run.setMaxAbsPieceDifference(0);
        run.setStartedAt(LocalDateTime.now());
        runMapper.insert(run);
        return run;
    }

    private InventoryReconciliationResult toResult(
            InventoryReconciliationRun run,
            InventoryReconciliationCalculator.ResultDraft draft
    ) {
        InventoryReconciliationResult result = new InventoryReconciliationResult();
        result.setReconciliationRunId(run.getReconciliationRunId());
        result.setBusinessDate(run.getBusinessDate());
        result.setDimensionType(draft.dimensionType());
        result.setProductId(draft.productId());
        result.setProductNameSnapshot(draft.productName());
        result.setWarehouseId(draft.warehouseId());
        result.setWarehouseNameSnapshot(draft.warehouseName());
        result.setOpeningPieces(draft.openingPieces());
        result.setInboundPieces(draft.inboundPieces());
        result.setOutboundPieces(draft.outboundPieces());
        result.setTransferInPieces(draft.transferInPieces());
        result.setTransferOutPieces(draft.transferOutPieces());
        result.setExpectedClosingPieces(draft.expectedClosingPieces());
        result.setActualClosingPieces(draft.actualClosingPieces());
        result.setPieceDifference(draft.pieceDifference());
        result.setOpeningWeightKg(draft.openingWeight());
        result.setInboundWeightKg(draft.inboundWeight());
        result.setOutboundWeightKg(draft.outboundWeight());
        result.setTransferInWeightKg(draft.transferInWeight());
        result.setTransferOutWeightKg(draft.transferOutWeight());
        result.setExpectedClosingWeightKg(draft.expectedClosingWeight());
        result.setActualClosingWeightKg(draft.actualClosingWeight());
        result.setWeightDifferenceKg(draft.weightDifference());
        result.setStatus(draft.status());
        result.setCreatedAt(LocalDateTime.now());
        return result;
    }

    private void persistIssues(
            InventoryReconciliationRun run,
            List<InventoryReconciliationCalculator.IssueDraft> drafts
    ) {
        for (InventoryReconciliationCalculator.IssueDraft draft : drafts) {
            InventoryDataQualityIssue issue = new InventoryDataQualityIssue();
            issue.setIssueId("iqi_" + UUID.randomUUID().toString().replace("-", ""));
            issue.setIssueFingerprint(issueFingerprint(draft));
            issue.setReconciliationRunId(run.getReconciliationRunId());
            issue.setBusinessDate(run.getBusinessDate());
            issue.setIssueCode(draft.issueCode());
            issue.setSeverity(draft.severity());
            issue.setSourceType(draft.sourceType());
            issue.setSourceRecordId(draft.sourceRecordId());
            issue.setBusinessActionId(draft.businessActionId());
            issue.setProductId(draft.productId());
            issue.setWarehouseId(draft.warehouseId());
            issue.setMessage(draft.message());
            issue.setDetailsJson(draft.detailsJson());
            issue.setStatus("OPEN");
            issue.setCreatedAt(LocalDateTime.now());
            issueMapper.insert(issue);
        }
    }

    private void completeRun(
            InventoryReconciliationRun run,
            String status,
            int globalResultCount,
            int warehouseResultCount,
            int failedResultCount,
            int issueCount,
            int maxAbsPieceDifference,
            String reason
    ) {
        run.setStatus(status);
        run.setGlobalResultCount(globalResultCount);
        run.setWarehouseResultCount(warehouseResultCount);
        run.setFailedResultCount(failedResultCount);
        run.setIssueCount(issueCount);
        run.setMaxAbsPieceDifference(maxAbsPieceDifference);
        run.setReason(reason);
        run.setCompletedAt(LocalDateTime.now());
        runMapper.updateById(run);
    }

    private void updateReleaseGate(
            LocalDate businessDate,
            InventoryReconciliationRun currentRun
    ) {
        int requiredDays = Math.max(1, minimumPassedDays);
        List<InventoryReconciliationRun> latestDays =
                runMapper.listLatestDays(businessDate, requiredDays);
        int consecutivePassedDays = 0;
        LocalDate expectedDate = businessDate;
        for (InventoryReconciliationRun day : latestDays) {
            if (!expectedDate.equals(day.getBusinessDate())
                    || !"PASSED".equals(day.getStatus())) {
                break;
            }
            consecutivePassedDays++;
            expectedDate = expectedDate.minusDays(1);
        }
        boolean eligible = consecutivePassedDays >= requiredDays;
        InventoryTrendReleaseGate gate = gateMapper.findByGateKey(GATE_KEY);
        if (gate == null) {
            gate = new InventoryTrendReleaseGate();
            gate.setGateKey(GATE_KEY);
        }
        gate.setStatus(eligible ? "ELIGIBLE" : "BLOCKED");
        gate.setRequiredPassedDays(requiredDays);
        gate.setConsecutivePassedDays(consecutivePassedDays);
        gate.setWindowEndDate(consecutivePassedDays == 0 ? null : businessDate);
        gate.setWindowStartDate(consecutivePassedDays == 0
                ? null
                : businessDate.minusDays(consecutivePassedDays - 1L));
        gate.setLastReconciliationRunId(currentRun.getReconciliationRunId());
        gate.setRuleVersion(RULE_VERSION);
        gate.setReason(eligible
                ? "已连续 " + consecutivePassedDays + " 天通过库存守恒对账"
                : "尚需连续通过 " + requiredDays + " 天；当前连续通过 "
                + consecutivePassedDays + " 天");
        gate.setEvaluatedAt(LocalDateTime.now());
        if (gate.getId() == null) {
            gateMapper.insert(gate);
        } else {
            gateMapper.updateById(gate);
        }
    }

    private String issueFingerprint(
            InventoryReconciliationCalculator.IssueDraft draft
    ) {
        String canonical = String.join("|",
                value(draft.issueCode()),
                value(draft.sourceType()),
                value(draft.sourceRecordId()),
                value(draft.businessActionId()),
                value(draft.productId()),
                value(draft.warehouseId())
        );
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(canonical.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("当前运行环境不支持 SHA-256", impossible);
        }
    }

    private boolean isTerminal(String status) {
        return "PASSED".equals(status)
                || "FAILED".equals(status)
                || "INSUFFICIENT_DATA".equals(status);
    }

    private String value(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
