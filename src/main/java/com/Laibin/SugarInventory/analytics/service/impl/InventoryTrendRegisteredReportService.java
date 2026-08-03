package com.Laibin.SugarInventory.analytics.service.impl;

import com.Laibin.SugarInventory.analytics.domain.dto.RegisteredReportRunQueryDTO;
import com.Laibin.SugarInventory.analytics.domain.vo.InventoryReplayBalanceRowVO;
import com.Laibin.SugarInventory.analytics.domain.vo.InventoryReplayAnchorCheckRowVO;
import com.Laibin.SugarInventory.analytics.domain.vo.InventoryReplayMovementRowVO;
import com.Laibin.SugarInventory.analytics.domain.vo.InventoryTrustedTrendPointRowVO;
import com.Laibin.SugarInventory.analytics.domain.vo.RegisteredReportDataQualityVO;
import com.Laibin.SugarInventory.analytics.domain.vo.RegisteredReportInventoryTrendDailyPointVO;
import com.Laibin.SugarInventory.analytics.domain.vo.RegisteredReportInventoryTrendMetricsVO;
import com.Laibin.SugarInventory.analytics.domain.vo.RegisteredReportInventoryTrendProductBreakdownVO;
import com.Laibin.SugarInventory.analytics.domain.vo.RegisteredReportRunVO;
import com.Laibin.SugarInventory.analytics.mapper.InventoryTrendReportMapper;
import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.inventoryhistory.domain.InventoryTrendReleaseGate;
import com.Laibin.SugarInventory.inventoryhistory.mapper.InventoryTrendReleaseGateMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class InventoryTrendRegisteredReportService {
    public static final String REPORT_ID = "inventory_level_trend_v1";
    public static final int REPORT_VERSION = 1;
    public static final String METRIC_DEFINITION_VERSION = "inventory_level_v1";
    private static final String GATE_KEY = "INVENTORY_LEVEL_TREND";
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final int MAX_RANGE_DAYS = 31;

    private final InventoryTrendReportMapper reportMapper;
    private final InventoryTrendReleaseGateMapper gateMapper;
    private final boolean simulationEnabled;

    public InventoryTrendRegisteredReportService(
            InventoryTrendReportMapper reportMapper,
            InventoryTrendReleaseGateMapper gateMapper,
            @Value("${analytics.inventory-trend.simulation-enabled:false}") boolean simulationEnabled) {
        this.reportMapper = reportMapper;
        this.gateMapper = gateMapper;
        this.simulationEnabled = simulationEnabled;
    }

    public RegisteredReportRunVO run(RegisteredReportRunQueryDTO query) {
        ValidatedQuery validated = validate(query);
        InventoryTrendReleaseGate gate = gateMapper.findByGateKey(GATE_KEY);
        if (trustedWindowContains(gate, validated.startDate(), validated.endDate())) {
            return runTrusted(validated);
        }
        if (simulationEnabled) {
            return runReplay(validated, gate);
        }
        throw blocked(gate);
    }

    private RegisteredReportRunVO runTrusted(ValidatedQuery query) {
        List<InventoryTrustedTrendPointRowVO> source = defaultList(
                reportMapper.listTrustedTrendPoints(
                        query.startDate(), query.endDate(), query.productQuery()));
        Map<LocalDate, Map<Integer, ProductBalance>> snapshots = emptySnapshots(query);
        LocalDateTime dataAsOf = null;
        for (InventoryTrustedTrendPointRowVO row : source) {
            if (row == null || row.getBusinessDate() == null || row.getProductId() == null) {
                continue;
            }
            snapshots.get(row.getBusinessDate()).put(row.getProductId(), new ProductBalance(
                    row.getProductId(), cleanProductName(row.getProductName()), row.getTotalPieces(),
                    orZero(row.getTotalWeightKg())));
            dataAsOf = later(dataAsOf, row.getDataAsOf());
        }
        return assemble(
                query,
                "TRUSTED_DAILY_CLOSE_SNAPSHOT",
                dataAsOf == null ? LocalDateTime.now(BUSINESS_ZONE) : dataAsOf,
                dataAsOf,
                snapshots,
                Map.of(),
                false,
                0,
                List.of(),
                List.of(
                        "库存水平来自已通过每日守恒对账的真实日终快照。",
                        "趋势只表示登记库存水平变化，不自动解释变化原因或预测未来库存。"
                ));
    }

    private RegisteredReportRunVO runReplay(
            ValidatedQuery query,
            InventoryTrendReleaseGate gate) {
        LocalDateTime dataAsOf = LocalDateTime.now(BUSINESS_ZONE);
        LocalDate anchorDate = dataAsOf.toLocalDate();
        List<InventoryReplayAnchorCheckRowVO> anchorChecks = defaultList(
                reportMapper.listReplayAnchorChecks(query.productQuery()));
        assertReplayAnchorReconciled(anchorChecks);
        List<InventoryReplayBalanceRowVO> currentRows = defaultList(
                reportMapper.listCurrentProductBalances(query.productQuery()));
        List<InventoryReplayMovementRowVO> movementRows = defaultList(
                reportMapper.listReplayMovementsAfter(query.startDate(), query.productQuery()));

        Map<Integer, ProductBalance> balance = new LinkedHashMap<>();
        for (InventoryReplayBalanceRowVO row : currentRows) {
            if (row == null || row.getProductId() == null) {
                continue;
            }
            balance.put(row.getProductId(), new ProductBalance(
                    row.getProductId(), cleanProductName(row.getProductName()), row.getTotalPieces(),
                    orZero(row.getTotalWeightKg())));
        }

        Map<LocalDate, List<InventoryReplayMovementRowVO>> movementsByDate = new LinkedHashMap<>();
        Map<LocalDate, Integer> movementCounts = new LinkedHashMap<>();
        int replayMovementCount = 0;
        LocalDateTime latestRecordedAt = null;
        for (InventoryReplayMovementRowVO row : movementRows) {
            if (row == null || row.getBusinessDate() == null || row.getProductId() == null) {
                continue;
            }
            movementsByDate.computeIfAbsent(row.getBusinessDate(), ignored -> new ArrayList<>())
                    .add(row);
            movementCounts.merge(row.getBusinessDate(), row.getMovementRecordCount(), Integer::sum);
            replayMovementCount += row.getMovementRecordCount();
            latestRecordedAt = later(latestRecordedAt, row.getLatestRecordedAt());
            balance.computeIfAbsent(row.getProductId(), ignored -> new ProductBalance(
                    row.getProductId(), cleanProductName(row.getProductName()), 0, BigDecimal.ZERO));
        }

        Map<LocalDate, Map<Integer, ProductBalance>> snapshots = emptySnapshots(query);
        for (LocalDate day = anchorDate; !day.isBefore(query.startDate()); day = day.minusDays(1)) {
            if (!day.isAfter(query.endDate())) {
                snapshots.put(day, copyBalances(balance));
            }
            reverse(balance, movementsByDate.getOrDefault(day, List.of()), day);
        }

        List<String> qualityNotes = new ArrayList<>();
        qualityNotes.add("当前为本地历史回放模拟，用于验证报表与 Agent 流程；不代表零点任务已连续运行 7 天。");
        qualityNotes.add("回放锚点是当前库存，历史增减采用入库、半成品入库和出库记录的系统登记时间。");
        if (gate != null) {
            qualityNotes.add("真实发布门禁当前为 "
                    + value(gate.getConsecutivePassedDays()) + "/"
                    + value(gate.getRequiredPassedDays()) + " 天。");
        }

        return assemble(
                query,
                "HISTORICAL_REPLAY_SIMULATION",
                dataAsOf,
                latestRecordedAt,
                snapshots,
                movementCounts,
                true,
                replayMovementCount,
                List.copyOf(qualityNotes),
                List.of(
                        "本结果是当前库存与历史登记流水的反向回放，只用于本地工程验收，不是正式日终快照。",
                        "回放仅提供产品总库存趋势，不提供历史库位分布；产品内调拨按总量净零处理。",
                        "登记时间不等同于生产日期或现场实际发生时间，迟到补录可能改变回放结果。",
                        "正式环境仍需连续 7 天真实日终快照和每日守恒对账通过后才能发布。"
                ));
    }

    private RegisteredReportRunVO assemble(
            ValidatedQuery query,
            String dataScope,
            LocalDateTime dataAsOf,
            LocalDateTime latestRecordAt,
            Map<LocalDate, Map<Integer, ProductBalance>> snapshots,
            Map<LocalDate, Integer> movementCounts,
            boolean simulation,
            int replayMovementCount,
            List<String> qualityNotes,
            List<String> limitations) {
        List<LocalDate> dates = new ArrayList<>(snapshots.keySet());
        Collections.sort(dates);
        List<RegisteredReportInventoryTrendDailyPointVO> daily = new ArrayList<>();
        DayTotal previous = null;
        int increaseDays = 0;
        int decreaseDays = 0;
        int unchangedDays = 0;
        for (LocalDate day : dates) {
            DayTotal total = total(snapshots.get(day));
            long pieceChange = previous == null ? 0 : total.pieces() - previous.pieces();
            BigDecimal weightChange = previous == null
                    ? BigDecimal.ZERO
                    : total.weightKg().subtract(previous.weightKg());
            if (previous != null) {
                if (pieceChange > 0) {
                    increaseDays++;
                } else if (pieceChange < 0) {
                    decreaseDays++;
                } else {
                    unchangedDays++;
                }
            }
            daily.add(RegisteredReportInventoryTrendDailyPointVO.builder()
                    .businessDate(day)
                    .totalPieces(total.pieces())
                    .totalWeightKg(normalized(total.weightKg()))
                    .pieceChange(pieceChange)
                    .weightChangeKg(normalized(weightChange))
                    .movementRecordCount(movementCounts.getOrDefault(day, 0))
                    .build());
            previous = total;
        }

        Map<Integer, ProductBalance> opening = dates.isEmpty()
                ? Map.of()
                : snapshots.get(dates.get(0));
        Map<Integer, ProductBalance> closing = dates.isEmpty()
                ? Map.of()
                : snapshots.get(dates.get(dates.size() - 1));
        Set<Integer> productIds = new LinkedHashSet<>();
        productIds.addAll(opening.keySet());
        productIds.addAll(closing.keySet());
        List<RegisteredReportInventoryTrendProductBreakdownVO> products = productIds.stream()
                .map(productId -> productBreakdown(productId, opening, closing))
                .sorted(Comparator.comparing(
                        RegisteredReportInventoryTrendProductBreakdownVO::getProductName))
                .toList();

        DayTotal openingTotal = total(opening);
        DayTotal closingTotal = total(closing);
        RegisteredReportInventoryTrendMetricsVO metrics = RegisteredReportInventoryTrendMetricsVO.builder()
                .observationDayCount(dates.size())
                .openingPieces(openingTotal.pieces())
                .closingPieces(closingTotal.pieces())
                .netChangePieces(closingTotal.pieces() - openingTotal.pieces())
                .openingWeightKg(normalized(openingTotal.weightKg()))
                .closingWeightKg(normalized(closingTotal.weightKg()))
                .netChangeWeightKg(normalized(closingTotal.weightKg().subtract(openingTotal.weightKg())))
                .increaseDayCount(increaseDays)
                .decreaseDayCount(decreaseDays)
                .unchangedDayCount(unchangedDays)
                .build();

        Map<String, String> filters = new LinkedHashMap<>();
        filters.put("businessDate", dateRangeLabel(query.startDate(), query.endDate()));
        filters.put("productScope", query.productQuery() == null
                ? "全部产品"
                : "产品名称包含“" + query.productQuery() + "”");
        filters.put("dataSource", simulation ? "本地历史回放模拟" : "可信日终快照");

        return RegisteredReportRunVO.builder()
                .dataScope(dataScope)
                .reportRunId(newReportRunId())
                .reportDefinitionId(REPORT_ID)
                .reportVersion(REPORT_VERSION)
                .reportName(simulation ? "库存水平趋势（历史回放模拟）" : "库存水平趋势")
                .metricDefinitionVersion(METRIC_DEFINITION_VERSION)
                .startDate(query.startDate())
                .endDate(query.endDate())
                .dateRangeLabel(dateRangeLabel(query.startDate(), query.endDate()))
                .dataAsOf(dataAsOf)
                .latestRecordAt(latestRecordAt)
                .filtersApplied(filters)
                .inventoryTrendMetrics(metrics)
                .inventoryTrendDailySeries(List.copyOf(daily))
                .inventoryTrendProductBreakdowns(products)
                .dataQuality(RegisteredReportDataQualityVO.builder()
                        .partial(simulation)
                        .simulationData(simulation)
                        .replayMovementRecordCount(simulation ? replayMovementCount : null)
                        .replayAnchorReconciled(simulation ? Boolean.TRUE : null)
                        .trustedSnapshotDayCount(simulation ? 0 : dates.size())
                        .requiredSnapshotDayCount(dates.size())
                        .notes(qualityNotes)
                        .build())
                .limitations(limitations)
                .build();
    }

    private void reverse(
            Map<Integer, ProductBalance> balances,
            List<InventoryReplayMovementRowVO> movements,
            LocalDate day) {
        for (InventoryReplayMovementRowVO movement : movements) {
            ProductBalance current = balances.computeIfAbsent(
                    movement.getProductId(),
                    ignored -> new ProductBalance(
                            movement.getProductId(), cleanProductName(movement.getProductName()),
                            0, BigDecimal.ZERO));
            long previousPieces = current.pieces()
                    - movement.getInboundPieces()
                    + movement.getOutboundPieces();
            BigDecimal previousWeight = current.weightKg()
                    .subtract(orZero(movement.getInboundWeightKg()))
                    .add(orZero(movement.getOutboundWeightKg()));
            if (previousPieces < 0 || previousWeight.compareTo(BigDecimal.ZERO) < 0) {
                throw new BusinessException(409,
                        day + " 的历史登记流水无法从当前库存安全回放，请先修复库存守恒差异");
            }
            balances.put(movement.getProductId(), new ProductBalance(
                    current.productId(), current.productName(), previousPieces, previousWeight));
        }
    }

    private static void assertReplayAnchorReconciled(
            List<InventoryReplayAnchorCheckRowVO> checks) {
        for (InventoryReplayAnchorCheckRowVO check : checks) {
            if (check == null) {
                continue;
            }
            BigDecimal weightDifference = orZero(check.getWeightDifferenceKg()).abs();
            if (check.getPieceDifference() != 0
                    || weightDifference.compareTo(new BigDecimal("0.0001")) > 0) {
                throw new BusinessException(409,
                        cleanProductName(check.getProductName())
                                + " 的历史入出库与当前库存不守恒，不能用于历史回放模拟");
            }
        }
    }

    private static Map<LocalDate, Map<Integer, ProductBalance>> emptySnapshots(ValidatedQuery query) {
        Map<LocalDate, Map<Integer, ProductBalance>> result = new LinkedHashMap<>();
        for (LocalDate day = query.startDate(); !day.isAfter(query.endDate()); day = day.plusDays(1)) {
            result.put(day, new LinkedHashMap<>());
        }
        return result;
    }

    private static Map<Integer, ProductBalance> copyBalances(Map<Integer, ProductBalance> source) {
        Map<Integer, ProductBalance> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> copy.put(key, new ProductBalance(
                value.productId(), value.productName(), value.pieces(), value.weightKg())));
        return copy;
    }

    private static RegisteredReportInventoryTrendProductBreakdownVO productBreakdown(
            Integer productId,
            Map<Integer, ProductBalance> opening,
            Map<Integer, ProductBalance> closing) {
        ProductBalance first = opening.get(productId);
        ProductBalance last = closing.get(productId);
        String name = first != null ? first.productName() : last.productName();
        long openingPieces = first == null ? 0 : first.pieces();
        long closingPieces = last == null ? 0 : last.pieces();
        BigDecimal openingWeight = first == null ? BigDecimal.ZERO : first.weightKg();
        BigDecimal closingWeight = last == null ? BigDecimal.ZERO : last.weightKg();
        return RegisteredReportInventoryTrendProductBreakdownVO.builder()
                .productId(productId)
                .productName(name)
                .openingPieces(openingPieces)
                .closingPieces(closingPieces)
                .netChangePieces(closingPieces - openingPieces)
                .openingWeightKg(normalized(openingWeight))
                .closingWeightKg(normalized(closingWeight))
                .netChangeWeightKg(normalized(closingWeight.subtract(openingWeight)))
                .build();
    }

    private static DayTotal total(Map<Integer, ProductBalance> balances) {
        long pieces = 0;
        BigDecimal weight = BigDecimal.ZERO;
        if (balances != null) {
            for (ProductBalance balance : balances.values()) {
                pieces += balance.pieces();
                weight = weight.add(balance.weightKg());
            }
        }
        return new DayTotal(pieces, weight);
    }

    private static boolean trustedWindowContains(
            InventoryTrendReleaseGate gate,
            LocalDate start,
            LocalDate end) {
        return gate != null
                && "ELIGIBLE".equals(gate.getStatus())
                && gate.getWindowStartDate() != null
                && gate.getWindowEndDate() != null
                && !start.isBefore(gate.getWindowStartDate())
                && !end.isAfter(gate.getWindowEndDate());
    }

    private static BusinessException blocked(InventoryTrendReleaseGate gate) {
        int passed = gate == null ? 0 : value(gate.getConsecutivePassedDays());
        int required = gate == null ? 7 : Math.max(1, value(gate.getRequiredPassedDays()));
        return new BusinessException(409,
                "库存趋势仍在积累可信日终数据，当前连续通过 " + passed + "/" + required
                        + " 天；现阶段可以继续查询当前库存，但不能把不完整历史作为正式趋势。");
    }

    private static ValidatedQuery validate(RegisteredReportRunQueryDTO query) {
        if (query == null) {
            throw new BusinessException(400, "报表查询条件不能为空");
        }
        if (!REPORT_ID.equals(trimToNull(query.getReportDefinitionId()))) {
            throw new BusinessException(400, "当前库存趋势报表定义不受支持");
        }
        if (query.getReportVersion() == null || query.getReportVersion() != REPORT_VERSION) {
            throw new BusinessException(400, "当前库存趋势报表版本不受支持");
        }
        if (query.getStartDate() == null || query.getEndDate() == null) {
            throw new BusinessException(400, "报表开始日期和结束日期不能为空");
        }
        if (query.getStartDate().isAfter(query.getEndDate())) {
            throw new BusinessException(400, "报表开始日期不能晚于结束日期");
        }
        if (query.getEndDate().isAfter(LocalDate.now(BUSINESS_ZONE))) {
            throw new BusinessException(400, "库存趋势不能查询未来日期");
        }
        long days = ChronoUnit.DAYS.between(query.getStartDate(), query.getEndDate()) + 1;
        if (days > MAX_RANGE_DAYS) {
            throw new BusinessException(400, "库存水平趋势单次最多查询 31 天");
        }
        String productQuery = trimToNull(query.getProductQuery());
        if (productQuery != null && productQuery.length() > 100) {
            throw new BusinessException(400, "产品筛选条件不能超过 100 个字符");
        }
        return new ValidatedQuery(query.getStartDate(), query.getEndDate(), productQuery);
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String cleanProductName(String value) {
        String normalized = trimToNull(value);
        return normalized == null ? "产品名称未登记" : normalized;
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static BigDecimal normalized(BigDecimal value) {
        BigDecimal safe = orZero(value).stripTrailingZeros();
        return safe.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO : safe;
    }

    private static LocalDateTime later(LocalDateTime current, LocalDateTime candidate) {
        if (candidate == null) {
            return current;
        }
        return current == null || candidate.isAfter(current) ? candidate : current;
    }

    private static int value(Integer value) {
        return value == null ? 0 : value;
    }

    private static String dateRangeLabel(LocalDate start, LocalDate end) {
        return start.equals(end) ? start.toString() : start + " 至 " + end;
    }

    private static String newReportRunId() {
        return "report_run_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private static <T> List<T> defaultList(List<T> value) {
        return value == null ? List.of() : value;
    }

    private record ValidatedQuery(LocalDate startDate, LocalDate endDate, String productQuery) {
    }

    private record ProductBalance(
            Integer productId,
            String productName,
            long pieces,
            BigDecimal weightKg) {
    }

    private record DayTotal(long pieces, BigDecimal weightKg) {
    }
}
