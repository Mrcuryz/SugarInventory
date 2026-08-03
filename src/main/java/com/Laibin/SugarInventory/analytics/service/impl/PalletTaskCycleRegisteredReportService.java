package com.Laibin.SugarInventory.analytics.service.impl;

import com.Laibin.SugarInventory.analytics.domain.dto.RegisteredReportRunQueryDTO;
import com.Laibin.SugarInventory.analytics.domain.vo.PalletTaskCycleFactRowVO;
import com.Laibin.SugarInventory.analytics.domain.vo.RegisteredReportDataQualityVO;
import com.Laibin.SugarInventory.analytics.domain.vo.RegisteredReportPalletTaskCycleDailyPointVO;
import com.Laibin.SugarInventory.analytics.domain.vo.RegisteredReportPalletTaskCycleMetricsVO;
import com.Laibin.SugarInventory.analytics.domain.vo.RegisteredReportPalletTaskCycleTypeBreakdownVO;
import com.Laibin.SugarInventory.analytics.domain.vo.RegisteredReportPalletTaskPendingItemVO;
import com.Laibin.SugarInventory.analytics.domain.vo.RegisteredReportRunVO;
import com.Laibin.SugarInventory.analytics.mapper.PalletTaskCycleReportMapper;
import com.Laibin.SugarInventory.common.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class PalletTaskCycleRegisteredReportService {
    public static final String REPORT_ID = "pallet_task_cycle_time_v1";
    public static final int REPORT_VERSION = 1;
    public static final String METRIC_DEFINITION_VERSION = "registered_pallet_task_cycle_v1";
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final int MAX_RANGE_DAYS = 366;
    private static final int MAX_SOURCE_ROWS = 100_000;
    private static final int PENDING_ITEM_LIMIT = 20;
    private static final Set<String> TASK_TYPES = Set.of(
            "INBOUND", "SEMI_IN", "FINISH_IN", "OUT", "TRANSFER");

    private final PalletTaskCycleReportMapper mapper;
    private final Clock clock;

    @Autowired
    public PalletTaskCycleRegisteredReportService(PalletTaskCycleReportMapper mapper) {
        this(mapper, Clock.system(BUSINESS_ZONE));
    }

    public PalletTaskCycleRegisteredReportService(PalletTaskCycleReportMapper mapper, Clock clock) {
        this.mapper = mapper;
        this.clock = clock;
    }

    public RegisteredReportRunVO run(RegisteredReportRunQueryDTO query) {
        ValidatedQuery validated = validate(query);
        List<PalletTaskCycleFactRowVO> rows = safeList(mapper.listTaskFacts(
                validated.startDate(), validated.endDate(), validated.productQuery(),
                validated.taskType(), MAX_SOURCE_ROWS + 1));
        if (rows.size() > MAX_SOURCE_ROWS) {
            throw new BusinessException(400, "托盘任务记录超过 100000 条，请缩小日期或产品范围");
        }

        LocalDateTime dataAsOf = LocalDateTime.now(clock);
        MutableBucket total = new MutableBucket(dataAsOf);
        Map<LocalDate, MutableBucket> daily = new LinkedHashMap<>();
        for (LocalDate day = validated.startDate(); !day.isAfter(validated.endDate()); day = day.plusDays(1)) {
            daily.put(day, new MutableBucket(dataAsOf));
        }
        Map<String, MutableBucket> byType = new LinkedHashMap<>();
        for (String type : List.of("SEMI_IN", "FINISH_IN", "OUT", "TRANSFER")) {
            byType.put(type, new MutableBucket(dataAsOf));
        }

        int rowsMissingProductName = 0;
        int canceledWithoutTerminalTime = 0;
        int tasksWithoutFlowRecord = 0;
        int tasksWithoutOperationBatch = 0;
        LocalDateTime latestRecordAt = null;

        for (PalletTaskCycleFactRowVO row : rows) {
            total.add(row);
            if (row.getCreatedAt() != null) {
                MutableBucket day = daily.get(row.getCreatedAt().toLocalDate());
                if (day != null) {
                    day.add(row);
                }
            }
            byType.computeIfAbsent(cleanTaskType(row.getTaskType()), ignored -> new MutableBucket(dataAsOf))
                    .add(row);
            if (row.getProductName() == null || row.getProductName().isBlank()) {
                rowsMissingProductName++;
            }
            if ("CANCELED".equalsIgnoreCase(row.getStatus()) && row.getConfirmedAt() == null) {
                canceledWithoutTerminalTime++;
            }
            if (!Boolean.TRUE.equals(row.getHasFlowRecord())) {
                tasksWithoutFlowRecord++;
            }
            if (row.getOperationBatchNo() == null || row.getOperationBatchNo().isBlank()) {
                tasksWithoutOperationBatch++;
            }
            latestRecordAt = later(latestRecordAt, row.getCreatedAt());
            latestRecordAt = later(latestRecordAt, row.getConfirmedAt());
        }

        List<String> qualityNotes = new ArrayList<>();
        if (total.invalidCount > 0) {
            qualityNotes.add(total.invalidCount + " 条任务缺少必要时间、状态不受支持或结束早于开始，未进入耗时统计。");
        }
        if (canceledWithoutTerminalTime > 0) {
            qualityNotes.add(canceledWithoutTerminalTime + " 条已取消任务没有独立取消时间，只计入取消数量。");
        }
        if (tasksWithoutFlowRecord > 0) {
            qualityNotes.add(tasksWithoutFlowRecord + " 条任务没有关联托盘流转记录；任务自身时间仍保留。");
        }
        if (tasksWithoutOperationBatch > 0) {
            qualityNotes.add(tasksWithoutOperationBatch + " 条任务没有操作批次号，不能计算批量作业耗时。");
        }
        if (rowsMissingProductName > 0) {
            qualityNotes.add(rowsMissingProductName + " 条任务当前无法解析产品名称。");
        }

        Map<String, String> filters = new LinkedHashMap<>();
        filters.put("businessDate", "任务创建日期 " + dateRangeLabel(validated.startDate(), validated.endDate()));
        filters.put("productScope", validated.productQuery() == null
                ? "全部产品"
                : "当前产品名称包含“" + validated.productQuery() + "”");
        filters.put("taskScope", taskScopeLabel(validated.taskType()));

        return RegisteredReportRunVO.builder()
                .dataScope("REGISTERED_PALLET_TASK_CYCLE_TIME")
                .reportRunId("report_run_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16))
                .reportDefinitionId(REPORT_ID)
                .reportVersion(REPORT_VERSION)
                .reportName("托盘任务处理耗时趋势")
                .metricDefinitionVersion(METRIC_DEFINITION_VERSION)
                .startDate(validated.startDate())
                .endDate(validated.endDate())
                .dateRangeLabel(dateRangeLabel(validated.startDate(), validated.endDate()))
                .dataAsOf(dataAsOf)
                .latestRecordAt(latestRecordAt)
                .filtersApplied(filters)
                .palletTaskCycleMetrics(total.toMetrics())
                .palletTaskCycleDailySeries(daily.entrySet().stream()
                        .map(entry -> entry.getValue().toDailyPoint(entry.getKey()))
                        .toList())
                .palletTaskCycleTypeBreakdowns(byType.entrySet().stream()
                        .filter(entry -> entry.getValue().taskCount > 0)
                        .map(entry -> entry.getValue().toTypeBreakdown(taskTypeLabel(entry.getKey())))
                        .toList())
                .palletTaskPendingItems(rows.stream()
                        .filter(row -> "PENDING".equalsIgnoreCase(row.getStatus()) && row.getCreatedAt() != null)
                        .map(row -> toPendingItem(row, dataAsOf))
                        .sorted(Comparator.comparingLong(
                                RegisteredReportPalletTaskPendingItemVO::getWaitingSeconds).reversed())
                        .limit(PENDING_ITEM_LIMIT)
                        .toList())
                .dataQuality(RegisteredReportDataQualityVO.builder()
                        .partial(total.invalidCount > 0 || canceledWithoutTerminalTime > 0
                                || tasksWithoutFlowRecord > 0 || rowsMissingProductName > 0)
                        .rowsMissingWeight(0)
                        .rowsMissingPieceConversion(0)
                        .rowsMissingProductName(rowsMissingProductName)
                        .invalidDurationCount(total.invalidCount)
                        .canceledWithoutTerminalTimeCount(canceledWithoutTerminalTime)
                        .tasksWithoutFlowRecordCount(tasksWithoutFlowRecord)
                        .tasksWithoutOperationBatchCount(tasksWithoutOperationBatch)
                        .notes(List.copyOf(qualityNotes))
                        .build())
                .limitations(List.of(
                        "统计范围按任务创建日期形成队列；完成、进行中和取消使用报表生成时的受控状态快照。",
                        "完成耗时仅统计已确认且创建/确认时间有效的任务；进行中等待不混入完成耗时，取消任务也不进入完成耗时。",
                        "当前没有登记 SLA，只能描述已等待时长，不能称为逾期。",
                        "本报表是系统已登记的托盘任务处理耗时，不代表现场全部操作、员工绩效或因果结论。",
                        "任务没有历史产品名称快照，产品筛选和展示使用报表生成时的当前产品主数据名称。"
                ))
                .build();
    }

    private ValidatedQuery validate(RegisteredReportRunQueryDTO query) {
        if (query == null) {
            throw new BusinessException(400, "报表查询条件不能为空");
        }
        if (!REPORT_ID.equals(trimToNull(query.getReportDefinitionId()))) {
            throw new BusinessException(400, "当前报表定义不受支持");
        }
        if (query.getReportVersion() == null || query.getReportVersion() != REPORT_VERSION) {
            throw new BusinessException(400, "当前报表版本不受支持");
        }
        if (query.getStartDate() == null || query.getEndDate() == null) {
            throw new BusinessException(400, "报表开始日期和结束日期不能为空");
        }
        if (query.getStartDate().isAfter(query.getEndDate())) {
            throw new BusinessException(400, "报表开始日期不能晚于结束日期");
        }
        long rangeDays = ChronoUnit.DAYS.between(query.getStartDate(), query.getEndDate()) + 1;
        if (rangeDays > MAX_RANGE_DAYS) {
            throw new BusinessException(400, "托盘任务处理耗时报表单次最多查询 366 天");
        }
        String productQuery = trimToNull(query.getProductQuery());
        if (productQuery != null && productQuery.length() > 100) {
            throw new BusinessException(400, "产品筛选条件不能超过 100 个字符");
        }
        String taskType = trimToNull(query.getTaskType());
        if (taskType != null) {
            taskType = taskType.toUpperCase();
            if ("ALL".equals(taskType)) {
                taskType = null;
            } else if (!TASK_TYPES.contains(taskType)) {
                throw new BusinessException(400, "当前任务类型筛选不受支持");
            }
        }
        return new ValidatedQuery(query.getStartDate(), query.getEndDate(), productQuery, taskType);
    }

    private static RegisteredReportPalletTaskPendingItemVO toPendingItem(
            PalletTaskCycleFactRowVO row,
            LocalDateTime dataAsOf) {
        return RegisteredReportPalletTaskPendingItemVO.builder()
                .palletCode(cleanLabel(row.getPalletCode(), "托盘码未登记"))
                .taskTypeLabel(taskTypeLabel(row.getTaskType()))
                .productName(cleanLabel(row.getProductName(), "产品名称未登记"))
                .createdAt(row.getCreatedAt())
                .waitingSeconds(Math.max(0, Duration.between(row.getCreatedAt(), dataAsOf).getSeconds()))
                .targetWarehouseName(cleanLabel(row.getTargetWarehouseName(), "尚未登记目标库位"))
                .build();
    }

    private static String taskScopeLabel(String taskType) {
        return taskType == null ? "全部托盘任务" : taskTypeLabel(taskType);
    }

    private static String taskTypeLabel(String taskType) {
        return switch (cleanTaskType(taskType)) {
            case "SEMI_IN" -> "半成品入库任务";
            case "FINISH_IN" -> "成品入库任务";
            case "OUT" -> "出库任务";
            case "TRANSFER" -> "调拨任务";
            case "INBOUND" -> "全部入库任务";
            default -> "其他托盘任务";
        };
    }

    private static String cleanTaskType(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }

    private static String cleanLabel(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String dateRangeLabel(LocalDate startDate, LocalDate endDate) {
        return startDate.equals(endDate) ? startDate.toString() : startDate + " 至 " + endDate;
    }

    private static LocalDateTime later(LocalDateTime current, LocalDateTime candidate) {
        return candidate != null && (current == null || candidate.isAfter(current)) ? candidate : current;
    }

    private static <T> List<T> safeList(List<T> value) {
        return value == null ? List.of() : value;
    }

    private record ValidatedQuery(LocalDate startDate, LocalDate endDate, String productQuery, String taskType) {
    }

    private static final class MutableBucket {
        private final LocalDateTime dataAsOf;
        private int taskCount;
        private int completedCount;
        private int inProgressCount;
        private int canceledCount;
        private int invalidCount;
        private final List<Long> completedDurations = new ArrayList<>();
        private final List<Long> waitingDurations = new ArrayList<>();

        private MutableBucket(LocalDateTime dataAsOf) {
            this.dataAsOf = dataAsOf;
        }

        private void add(PalletTaskCycleFactRowVO row) {
            taskCount++;
            String status = row.getStatus() == null ? "" : row.getStatus().trim().toUpperCase();
            if ("CONFIRMED".equals(status)) {
                completedCount++;
                Long duration = durationSeconds(row.getCreatedAt(), row.getConfirmedAt());
                if (duration == null) {
                    invalidCount++;
                } else {
                    completedDurations.add(duration);
                }
            } else if ("PENDING".equals(status)) {
                inProgressCount++;
                Long waiting = durationSeconds(row.getCreatedAt(), dataAsOf);
                if (waiting == null) {
                    invalidCount++;
                } else {
                    waitingDurations.add(waiting);
                }
            } else if ("CANCELED".equals(status)) {
                canceledCount++;
            } else {
                invalidCount++;
            }
        }

        private RegisteredReportPalletTaskCycleMetricsVO toMetrics() {
            DurationStats completed = DurationStats.of(completedDurations);
            DurationStats waiting = DurationStats.of(waitingDurations);
            return RegisteredReportPalletTaskCycleMetricsVO.builder()
                    .cohortTaskCount(taskCount)
                    .completedTaskCount(completedCount)
                    .inProgressTaskCount(inProgressCount)
                    .canceledTaskCount(canceledCount)
                    .invalidTaskCount(invalidCount)
                    .averageDurationSeconds(completed.average())
                    .medianDurationSeconds(completed.median())
                    .p90DurationSeconds(completed.p90())
                    .maximumDurationSeconds(completed.maximum())
                    .medianWaitingSeconds(waiting.median())
                    .p90WaitingSeconds(waiting.p90())
                    .maximumWaitingSeconds(waiting.maximum())
                    .build();
        }

        private RegisteredReportPalletTaskCycleDailyPointVO toDailyPoint(LocalDate date) {
            DurationStats completed = DurationStats.of(completedDurations);
            DurationStats waiting = DurationStats.of(waitingDurations);
            return RegisteredReportPalletTaskCycleDailyPointVO.builder()
                    .businessDate(date)
                    .taskCount(taskCount)
                    .completedTaskCount(completedCount)
                    .inProgressTaskCount(inProgressCount)
                    .canceledTaskCount(canceledCount)
                    .averageDurationSeconds(completed.average())
                    .medianDurationSeconds(completed.median())
                    .p90DurationSeconds(completed.p90())
                    .maximumDurationSeconds(completed.maximum())
                    .maximumWaitingSeconds(waiting.maximum())
                    .build();
        }

        private RegisteredReportPalletTaskCycleTypeBreakdownVO toTypeBreakdown(String label) {
            DurationStats completed = DurationStats.of(completedDurations);
            DurationStats waiting = DurationStats.of(waitingDurations);
            return RegisteredReportPalletTaskCycleTypeBreakdownVO.builder()
                    .taskTypeLabel(label)
                    .taskCount(taskCount)
                    .completedTaskCount(completedCount)
                    .inProgressTaskCount(inProgressCount)
                    .canceledTaskCount(canceledCount)
                    .invalidTaskCount(invalidCount)
                    .averageDurationSeconds(completed.average())
                    .medianDurationSeconds(completed.median())
                    .p90DurationSeconds(completed.p90())
                    .maximumDurationSeconds(completed.maximum())
                    .maximumWaitingSeconds(waiting.maximum())
                    .build();
        }

        private static Long durationSeconds(LocalDateTime start, LocalDateTime end) {
            if (start == null || end == null || end.isBefore(start)) {
                return null;
            }
            return Duration.between(start, end).getSeconds();
        }
    }

    private record DurationStats(Long average, Long median, Long p90, Long maximum) {
        private static DurationStats of(List<Long> values) {
            if (values == null || values.isEmpty()) {
                return new DurationStats(null, null, null, null);
            }
            List<Long> sorted = values.stream().sorted().toList();
            long total = sorted.stream().mapToLong(Long::longValue).sum();
            long average = Math.round((double) total / sorted.size());
            int middle = sorted.size() / 2;
            long median = sorted.size() % 2 == 1
                    ? sorted.get(middle)
                    : Math.round((sorted.get(middle - 1) + sorted.get(middle)) / 2.0d);
            int p90Index = Math.max(0, (int) Math.ceil(sorted.size() * 0.9d) - 1);
            return new DurationStats(average, median, sorted.get(p90Index), sorted.getLast());
        }
    }
}
