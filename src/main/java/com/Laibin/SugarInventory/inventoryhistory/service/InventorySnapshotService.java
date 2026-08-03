package com.Laibin.SugarInventory.inventoryhistory.service;

import com.Laibin.SugarInventory.inventoryhistory.domain.InventoryDailySnapshot;
import com.Laibin.SugarInventory.inventoryhistory.domain.InventorySnapshotAggregate;
import com.Laibin.SugarInventory.inventoryhistory.domain.InventorySnapshotRun;
import com.Laibin.SugarInventory.inventoryhistory.mapper.InventoryDailySnapshotMapper;
import com.Laibin.SugarInventory.inventoryhistory.mapper.InventoryHistoryLockMapper;
import com.Laibin.SugarInventory.inventoryhistory.mapper.InventorySnapshotRunMapper;
import com.Laibin.SugarInventory.inventoryhistory.mapper.InventorySnapshotSourceMapper;
import com.Laibin.SugarInventory.inventoryhistory.mapper.StockMovementEventMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.ArrayList;
import java.util.UUID;

@Service
public class InventorySnapshotService {
    public static final String RULE_VERSION = "inventory-snapshot-v1";
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final int LOCK_TIMEOUT_SECONDS = 15;

    private final InventorySnapshotRunMapper runMapper;
    private final InventoryDailySnapshotMapper snapshotMapper;
    private final InventorySnapshotSourceMapper sourceMapper;
    private final InventoryHistoryLockMapper lockMapper;
    private final StockMovementEventMapper eventMapper;
    private final int maxCaptureDelayMinutes;

    public InventorySnapshotService(
            InventorySnapshotRunMapper runMapper,
            InventoryDailySnapshotMapper snapshotMapper,
            InventorySnapshotSourceMapper sourceMapper,
            InventoryHistoryLockMapper lockMapper,
            StockMovementEventMapper eventMapper,
            @Value("${inventory.history.max-capture-delay-minutes:10}")
            int maxCaptureDelayMinutes
    ) {
        this.runMapper = runMapper;
        this.snapshotMapper = snapshotMapper;
        this.sourceMapper = sourceMapper;
        this.lockMapper = lockMapper;
        this.eventMapper = eventMapper;
        if (maxCaptureDelayMinutes < 0 || maxCaptureDelayMinutes > 60) {
            throw new IllegalArgumentException(
                    "inventory.history.max-capture-delay-minutes 必须在 0 到 60 之间"
            );
        }
        this.maxCaptureDelayMinutes = maxCaptureDelayMinutes;
    }

    @Transactional
    public InventorySnapshotRun captureDailyCloseIfAbsent(LocalDate snapshotDate) {
        return captureDailyCloseWithOutcome(
                snapshotDate,
                LocalDateTime.now(BUSINESS_ZONE),
                "北京时间日终自动快照"
        ).snapshotRun();
    }

    @Transactional
    public InventorySnapshotCaptureResult captureDailyCloseWithOutcome(
            LocalDate snapshotDate,
            LocalDateTime capturedAt,
            String reason
    ) {
        String lockName = "inventory-daily-close:" + snapshotDate;
        Integer acquired = lockMapper.acquire(lockName, LOCK_TIMEOUT_SECONDS);
        if (!Integer.valueOf(1).equals(acquired)) {
            throw new InventorySnapshotCaptureRejectedException(
                    "SNAPSHOT_LOCK_TIMEOUT",
                    "未能取得库存日终快照互斥锁，请由调度器稍后重试"
            );
        }
        try {
            InventorySnapshotRun existing =
                    runMapper.findLatestCompleted(snapshotDate, "DAILY_CLOSE");
            if (existing != null) {
                assertTrustedCompletedDailyClose(existing);
                return new InventorySnapshotCaptureResult(existing, true);
            }
            validateCaptureWindow(snapshotDate, capturedAt);
            LocalDateTime boundary = snapshotDate.plusDays(1).atStartOfDay();
            int postBoundaryMovements = eventMapper.countOccurredBetween(boundary, capturedAt);
            if (postBoundaryMovements > 0) {
                throw new InventorySnapshotCaptureRejectedException(
                        "POST_BOUNDARY_MOVEMENT_DETECTED",
                        "零点后已经发生 " + postBoundaryMovements
                                + " 条库存变动，拒绝把当前库存伪装成前一日日终快照"
                );
            }
            return new InventorySnapshotCaptureResult(
                    captureAt(snapshotDate, "DAILY_CLOSE", reason, capturedAt),
                    false
            );
        } finally {
            lockMapper.release(lockName);
        }
    }

    @Transactional(readOnly = true)
    public void assertTrustedCompletedDailyClose(InventorySnapshotRun snapshot) {
        if (snapshot == null
                || !"DAILY_CLOSE".equals(snapshot.getSnapshotType())
                || !"COMPLETED".equals(snapshot.getStatus())
                || snapshot.getSnapshotDate() == null
                || snapshot.getDataAsOf() == null) {
            throw new InventorySnapshotCaptureRejectedException(
                    "INVALID_COMPLETED_SNAPSHOT",
                    "日终库存快照缺少可信时点或完成状态"
            );
        }
        LocalDateTime boundary = snapshot.getSnapshotDate().plusDays(1).atStartOfDay();
        if (snapshot.getDataAsOf().isBefore(boundary)
                || snapshot.getDataAsOf().isAfter(
                        boundary.plusMinutes(maxCaptureDelayMinutes)
                )) {
            throw new InventorySnapshotCaptureRejectedException(
                    "SNAPSHOT_OUTSIDE_TRUSTED_WINDOW",
                    "已有日终库存快照不在可信采集窗口内"
            );
        }
        int postBoundaryMovements = eventMapper.countOccurredBetween(
                boundary,
                snapshot.getDataAsOf()
        );
        if (postBoundaryMovements > 0) {
            throw new InventorySnapshotCaptureRejectedException(
                    "EXISTING_SNAPSHOT_CONTAMINATED",
                    "已有日终库存快照生成前已发生零点后库存变动，不能进入趋势窗口"
            );
        }
    }

    @Transactional
    public InventorySnapshotRun capture(LocalDate snapshotDate, String snapshotType, String reason) {
        return captureAt(
                snapshotDate,
                snapshotType,
                reason,
                LocalDateTime.now(BUSINESS_ZONE)
        );
    }

    private InventorySnapshotRun captureAt(
            LocalDate snapshotDate,
            String snapshotType,
            String reason,
            LocalDateTime now
    ) {
        InventorySnapshotRun run = new InventorySnapshotRun();
        run.setSnapshotRunId("snap_" + UUID.randomUUID().toString().replace("-", ""));
        run.setSnapshotDate(snapshotDate);
        run.setSnapshotType(snapshotType);
        run.setRevision(runMapper.findMaxRevision(snapshotDate, snapshotType) + 1);
        run.setDataAsOf(now);
        run.setRuleVersion(RULE_VERSION);
        run.setRowCount(0);
        run.setStatus("BUILDING");
        run.setReason(reason);
        run.setCreatedAt(now);
        runMapper.insert(run);

        List<InventorySnapshotAggregate> aggregates =
                new ArrayList<>(sourceMapper.listCurrentAggregates());
        aggregates.sort(Comparator
                .comparing(InventorySnapshotAggregate::getProductId)
                .thenComparing(InventorySnapshotAggregate::getWarehouseId)
                .thenComparing(
                        InventorySnapshotAggregate::getProductionDate,
                        Comparator.nullsFirst(Comparator.naturalOrder())
                ));

        MessageDigest digest = sha256();
        for (InventorySnapshotAggregate aggregate : aggregates) {
            InventoryDailySnapshot row = toSnapshot(run, aggregate, now);
            snapshotMapper.insert(row);
            digest.update(canonical(row).getBytes(StandardCharsets.UTF_8));
        }

        run.setRowCount(aggregates.size());
        run.setContentSha256(HexFormat.of().formatHex(digest.digest()));
        run.setStatus("COMPLETED");
        run.setCompletedAt(LocalDateTime.now());
        runMapper.updateById(run);
        return run;
    }

    private void validateCaptureWindow(LocalDate snapshotDate, LocalDateTime capturedAt) {
        LocalDate expectedDate = capturedAt.toLocalDate().minusDays(1);
        if (!snapshotDate.equals(expectedDate)) {
            throw new InventorySnapshotCaptureRejectedException(
                    "INVALID_SNAPSHOT_DATE",
                    "只允许为最近一个已关闭的北京时间自然日生成日终快照"
            );
        }
        LocalDateTime boundary = snapshotDate.plusDays(1).atStartOfDay();
        if (capturedAt.isBefore(boundary)) {
            throw new InventorySnapshotCaptureRejectedException(
                    "CAPTURE_WINDOW_NOT_OPEN",
                    "尚未到达该业务日的北京时间日终边界"
            );
        }
        if (capturedAt.isAfter(boundary.plusMinutes(maxCaptureDelayMinutes))) {
            throw new InventorySnapshotCaptureRejectedException(
                    "CAPTURE_WINDOW_MISSED",
                    "已错过日终快照可信采集窗口，禁止使用白天库存回填历史"
            );
        }
    }

    private InventoryDailySnapshot toSnapshot(
            InventorySnapshotRun run,
            InventorySnapshotAggregate aggregate,
            LocalDateTime createdAt
    ) {
        InventoryDailySnapshot row = new InventoryDailySnapshot();
        row.setSnapshotRunId(run.getSnapshotRunId());
        row.setSnapshotDate(run.getSnapshotDate());
        row.setProductId(aggregate.getProductId());
        row.setProductNameSnapshot(aggregate.getProductName());
        row.setProductStatusSnapshot(aggregate.getProductStatus());
        row.setWarehouseId(aggregate.getWarehouseId());
        row.setWarehouseNameSnapshot(aggregate.getWarehouseName());
        row.setProductionDate(aggregate.getProductionDate());
        row.setBoardCount(zero(aggregate.getBoardCount()));
        row.setLoosePieceCount(zero(aggregate.getLoosePieceCount()));
        row.setTotalPieces(zero(aggregate.getTotalPieces()));
        row.setTotalWeightKg(decimal(aggregate.getTotalWeightKg()));
        row.setInventoryRecordCount(zero(aggregate.getInventoryRecordCount()));
        row.setPalletCount(zero(aggregate.getPalletCount()));
        row.setPiecesPerPalletSnapshot(aggregate.getPiecesPerPallet());
        row.setWeightPerPieceSnapshot(decimal(aggregate.getWeightPerPiece()));
        row.setCreatedAt(createdAt);
        return row;
    }

    private String canonical(InventoryDailySnapshot row) {
        return String.join("|",
                value(row.getProductId()),
                value(row.getProductNameSnapshot()),
                value(row.getProductStatusSnapshot()),
                value(row.getWarehouseId()),
                value(row.getWarehouseNameSnapshot()),
                value(row.getProductionDate()),
                value(row.getBoardCount()),
                value(row.getLoosePieceCount()),
                value(row.getTotalPieces()),
                decimal(row.getTotalWeightKg()).stripTrailingZeros().toPlainString(),
                value(row.getInventoryRecordCount()),
                value(row.getPalletCount()),
                value(row.getPiecesPerPalletSnapshot()),
                decimal(row.getWeightPerPieceSnapshot()).stripTrailingZeros().toPlainString()
        ) + "\n";
    }

    private MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("当前运行环境不支持 SHA-256", impossible);
        }
    }

    private int zero(Integer value) {
        return value == null ? 0 : value;
    }

    private BigDecimal decimal(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String value(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
