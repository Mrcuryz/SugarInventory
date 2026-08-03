package com.Laibin.SugarInventory.analytics.service.impl;

import com.Laibin.SugarInventory.analytics.domain.dto.RegisteredReportRunQueryDTO;
import com.Laibin.SugarInventory.analytics.domain.vo.QualityAssayReportRowVO;
import com.Laibin.SugarInventory.analytics.domain.vo.RegisteredReportDataQualityVO;
import com.Laibin.SugarInventory.analytics.domain.vo.RegisteredReportQualityMetricsVO;
import com.Laibin.SugarInventory.analytics.domain.vo.RegisteredReportQualityPeriodPointVO;
import com.Laibin.SugarInventory.analytics.domain.vo.RegisteredReportQualityProductBreakdownVO;
import com.Laibin.SugarInventory.analytics.domain.vo.RegisteredReportRunVO;
import com.Laibin.SugarInventory.analytics.domain.vo.RegisteredReportStandardBreakdownVO;
import com.Laibin.SugarInventory.analytics.mapper.QualityAssayReportMapper;
import com.Laibin.SugarInventory.common.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QualityAssayRegisteredReportService {
    public static final String REPORT_ID = "quality_assay_result_trend_v1";
    public static final int REPORT_VERSION = 1;
    public static final String METRIC_DEFINITION_VERSION = "quality_assay_judgement_v1";
    private static final int MAX_RANGE_DAYS = 366;
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final Set<String> KNOWN_RESULTS =
            Set.of("PASS", "FAIL", "NO_STANDARD", "MULTIPLE_CANDIDATES");

    private final QualityAssayReportMapper mapper;

    public RegisteredReportRunVO run(RegisteredReportRunQueryDTO query) {
        ValidatedQuery validated = validate(query);
        List<QualityAssayReportRowVO> queriedRows = mapper.listQualityAssaysForReport(
                validated.startDate(), validated.endDate(), validated.productQuery());
        List<QualityAssayReportRowVO> rows = queriedRows == null ? List.of() : queriedRows;

        QualityAggregate total = new QualityAggregate();
        Map<String, PeriodAggregate> periods = initializePeriods(
                validated.startDate(), validated.endDate(), validated.seriesGranularity());
        Map<String, QualityAggregate> products = new LinkedHashMap<>();
        Map<String, StandardAggregate> standards = new LinkedHashMap<>();
        int rowsMissingJudgeResult = 0;
        int rowsMissingStandardVersion = 0;
        int rowsMissingProductName = 0;
        int lateRecordedCount = 0;
        LocalDateTime latestRecordAt = null;

        for (QualityAssayReportRowVO row : rows) {
            String judgeResult = normalizeJudgeResult(row.getJudgeResult());
            String productName = cleanProductName(row.getProductName());
            total.add(row, judgeResult);

            String periodKey = periodKey(row.getSampleDate(), validated.seriesGranularity());
            PeriodAggregate period = periods.get(periodKey);
            if (period != null) {
                period.aggregate().add(row, judgeResult);
            }

            String productKey = String.valueOf(row.getProductId()) + "|" + productName;
            products.computeIfAbsent(productKey, ignored -> new QualityAggregate())
                    .add(row, judgeResult);

            if (hasAppliedStandard(row)) {
                String standardKey = row.getAppliedStandardName().trim()
                        + "|v" + row.getAppliedStandardVersion();
                standards.computeIfAbsent(
                                standardKey,
                                ignored -> new StandardAggregate(
                                        row.getAppliedStandardName().trim(),
                                        row.getAppliedStandardVersion()))
                        .aggregate()
                        .add(row, judgeResult);
            }

            if (!KNOWN_RESULTS.contains(judgeResult)) {
                rowsMissingJudgeResult++;
            }
            if (("PASS".equals(judgeResult) || "FAIL".equals(judgeResult))
                    && !hasAppliedStandard(row)) {
                rowsMissingStandardVersion++;
            }
            if (row.getProductName() == null || row.getProductName().isBlank()) {
                rowsMissingProductName++;
            }
            if (row.getSampleDate() != null
                    && row.getCreatedAt() != null
                    && row.getCreatedAt().toLocalDate().isAfter(row.getSampleDate())) {
                lateRecordedCount++;
            }
            if (row.getCreatedAt() != null
                    && (latestRecordAt == null || row.getCreatedAt().isAfter(latestRecordAt))) {
                latestRecordAt = row.getCreatedAt();
            }
        }

        List<String> qualityNotes = new ArrayList<>();
        if (rowsMissingJudgeResult > 0) {
            qualityNotes.add(rowsMissingJudgeResult + " 条化验记录缺少受控判定结果，未计入合格率。");
        }
        if (rowsMissingStandardVersion > 0) {
            qualityNotes.add(rowsMissingStandardVersion + " 条已判定记录缺少采用标准或版本信息。");
        }
        if (rowsMissingProductName > 0) {
            qualityNotes.add(rowsMissingProductName + " 条化验记录缺少产品名称，已归入“产品名称未登记”。");
        }
        if (lateRecordedCount > 0) {
            qualityNotes.add(lateRecordedCount + " 条化验记录晚于生产日期录入；趋势仍按生产日期统计。");
        }

        Map<String, String> filters = new LinkedHashMap<>();
        filters.put("businessDate", dateRangeLabel(validated.startDate(), validated.endDate()));
        filters.put("productScope", validated.productQuery() == null
                ? "全部产品"
                : "产品名称包含“" + validated.productQuery() + "”");

        List<RegisteredReportQualityProductBreakdownVO> productBreakdowns = new ArrayList<>();
        for (Map.Entry<String, QualityAggregate> entry : products.entrySet()) {
            String productName = entry.getKey().substring(entry.getKey().indexOf('|') + 1);
            productBreakdowns.add(entry.getValue().toProductBreakdown(productName));
        }

        return RegisteredReportRunVO.builder()
                .dataScope("REGISTERED_QUALITY_ASSAY")
                .reportRunId(newReportRunId())
                .reportDefinitionId(REPORT_ID)
                .reportVersion(REPORT_VERSION)
                .reportName("化验判定趋势")
                .metricDefinitionVersion(METRIC_DEFINITION_VERSION)
                .startDate(validated.startDate())
                .endDate(validated.endDate())
                .dateRangeLabel(dateRangeLabel(validated.startDate(), validated.endDate()))
                .dataAsOf(LocalDateTime.now(BUSINESS_ZONE))
                .latestRecordAt(latestRecordAt)
                .filtersApplied(filters)
                .dailySeries(List.of())
                .productBreakdowns(List.of())
                .qualityMetrics(total.toMetrics())
                .seriesGranularity(validated.seriesGranularity())
                .qualitySeries(periods.values().stream()
                        .map(PeriodAggregate::toPoint)
                        .toList())
                .qualityProductBreakdowns(productBreakdowns)
                .standardBreakdowns(standards.values().stream()
                        .map(StandardAggregate::toBreakdown)
                        .toList())
                .dataQuality(RegisteredReportDataQualityVO.builder()
                        .partial(rowsMissingJudgeResult > 0
                                || rowsMissingStandardVersion > 0
                                || rowsMissingProductName > 0)
                        .rowsMissingWeight(0)
                        .rowsMissingPieceConversion(0)
                        .rowsMissingProductName(rowsMissingProductName)
                        .rowsMissingJudgeResult(rowsMissingJudgeResult)
                        .rowsMissingStandardVersion(rowsMissingStandardVersion)
                        .lateRecordedCount(lateRecordedCount)
                        .notes(List.copyOf(qualityNotes))
                        .build())
                .limitations(List.of(
                        "生产日期采用 assay.sample_date；录入时间仅用于识别晚录数据。",
                        "合格率固定为合格数除以合格数与不合格数之和；无标准、标准多候选和缺少判定不进入分母。",
                        "本报表只统计已有化验记录，不能据此推断哪些历史批次缺少化验。",
                        "标准版本按每条化验记录实际采用的历史版本展示，不使用当前标准重算历史判定。",
                        "样本量不足或数据不完整时不得输出质量改善、恶化或生产原因结论。"
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
            throw new BusinessException(400, "化验判定趋势单次最多查询 366 天");
        }
        String productQuery = trimToNull(query.getProductQuery());
        if (productQuery != null && productQuery.length() > 100) {
            throw new BusinessException(400, "产品筛选条件不能超过 100 个字符");
        }
        return new ValidatedQuery(
                query.getStartDate(),
                query.getEndDate(),
                productQuery,
                rangeDays <= 31 ? "DAY" : "MONTH");
    }

    private static Map<String, PeriodAggregate> initializePeriods(
            LocalDate startDate,
            LocalDate endDate,
            String granularity) {
        Map<String, PeriodAggregate> periods = new LinkedHashMap<>();
        if ("DAY".equals(granularity)) {
            for (LocalDate day = startDate; !day.isAfter(endDate); day = day.plusDays(1)) {
                periods.put(day.toString(), new PeriodAggregate(day.toString(), day, day, new QualityAggregate()));
            }
            return periods;
        }
        for (YearMonth month = YearMonth.from(startDate);
             !month.isAfter(YearMonth.from(endDate));
             month = month.plusMonths(1)) {
            LocalDate periodStart = month.atDay(1).isBefore(startDate) ? startDate : month.atDay(1);
            LocalDate periodEnd = month.atEndOfMonth().isAfter(endDate) ? endDate : month.atEndOfMonth();
            periods.put(
                    month.toString(),
                    new PeriodAggregate(month.toString(), periodStart, periodEnd, new QualityAggregate()));
        }
        return periods;
    }

    private static String periodKey(LocalDate sampleDate, String granularity) {
        if (sampleDate == null) {
            return "";
        }
        return "DAY".equals(granularity) ? sampleDate.toString() : YearMonth.from(sampleDate).toString();
    }

    private static boolean hasAppliedStandard(QualityAssayReportRowVO row) {
        return row.getAppliedStandardName() != null
                && !row.getAppliedStandardName().isBlank()
                && row.getAppliedStandardVersion() != null
                && row.getAppliedStandardVersion() > 0;
    }

    private static String normalizeJudgeResult(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String cleanProductName(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? "产品名称未登记" : trimmed;
    }

    private static String dateRangeLabel(LocalDate startDate, LocalDate endDate) {
        return startDate.equals(endDate) ? startDate.toString() : startDate + " 至 " + endDate;
    }

    private static String newReportRunId() {
        return "report_run_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private record ValidatedQuery(
            LocalDate startDate,
            LocalDate endDate,
            String productQuery,
            String seriesGranularity) {
    }

    private record PeriodAggregate(
            String label,
            LocalDate startDate,
            LocalDate endDate,
            QualityAggregate aggregate) {
        RegisteredReportQualityPeriodPointVO toPoint() {
            RegisteredReportQualityMetricsVO metrics = aggregate.toMetrics();
            return RegisteredReportQualityPeriodPointVO.builder()
                    .periodLabel(label)
                    .periodStart(startDate)
                    .periodEnd(endDate)
                    .assayRecordCount(metrics.getAssayRecordCount())
                    .judgedRecordCount(metrics.getJudgedRecordCount())
                    .passCount(metrics.getPassCount())
                    .failCount(metrics.getFailCount())
                    .noStandardCount(metrics.getNoStandardCount())
                    .multipleCandidatesCount(metrics.getMultipleCandidatesCount())
                    .passRatePercent(metrics.getPassRatePercent())
                    .build();
        }
    }

    private record StandardAggregate(
            String standardName,
            Integer standardVersion,
            QualityAggregate aggregate) {
        private StandardAggregate(String standardName, Integer standardVersion) {
            this(standardName, standardVersion, new QualityAggregate());
        }

        RegisteredReportStandardBreakdownVO toBreakdown() {
            RegisteredReportQualityMetricsVO metrics = aggregate.toMetrics();
            return RegisteredReportStandardBreakdownVO.builder()
                    .standardName(standardName)
                    .standardVersion(standardVersion)
                    .standardLabel(standardName + " v" + standardVersion)
                    .assayRecordCount(metrics.getAssayRecordCount())
                    .judgedRecordCount(metrics.getJudgedRecordCount())
                    .passCount(metrics.getPassCount())
                    .failCount(metrics.getFailCount())
                    .passRatePercent(metrics.getPassRatePercent())
                    .build();
        }
    }

    private static final class QualityAggregate {
        private int assayRecordCount;
        private int passCount;
        private int failCount;
        private int noStandardCount;
        private int multipleCandidatesCount;
        private final Set<Integer> productIds = new LinkedHashSet<>();
        private final Set<String> standardVersions = new LinkedHashSet<>();

        void add(QualityAssayReportRowVO row, String judgeResult) {
            assayRecordCount++;
            if (row.getProductId() != null) {
                productIds.add(row.getProductId());
            }
            if (hasAppliedStandard(row)) {
                standardVersions.add(row.getAppliedStandardName().trim()
                        + "|v" + row.getAppliedStandardVersion());
            }
            switch (judgeResult) {
                case "PASS" -> passCount++;
                case "FAIL" -> failCount++;
                case "NO_STANDARD" -> noStandardCount++;
                case "MULTIPLE_CANDIDATES" -> multipleCandidatesCount++;
                default -> {
                }
            }
        }

        RegisteredReportQualityMetricsVO toMetrics() {
            int judgedRecordCount = passCount + failCount;
            return RegisteredReportQualityMetricsVO.builder()
                    .assayRecordCount(assayRecordCount)
                    .judgedRecordCount(judgedRecordCount)
                    .passCount(passCount)
                    .failCount(failCount)
                    .noStandardCount(noStandardCount)
                    .multipleCandidatesCount(multipleCandidatesCount)
                    .passRatePercent(passRate(passCount, judgedRecordCount))
                    .distinctProductCount(productIds.size())
                    .distinctStandardVersionCount(standardVersions.size())
                    .build();
        }

        RegisteredReportQualityProductBreakdownVO toProductBreakdown(String productName) {
            RegisteredReportQualityMetricsVO metrics = toMetrics();
            return RegisteredReportQualityProductBreakdownVO.builder()
                    .productName(productName)
                    .assayRecordCount(metrics.getAssayRecordCount())
                    .judgedRecordCount(metrics.getJudgedRecordCount())
                    .passCount(metrics.getPassCount())
                    .failCount(metrics.getFailCount())
                    .noStandardCount(metrics.getNoStandardCount())
                    .multipleCandidatesCount(metrics.getMultipleCandidatesCount())
                    .passRatePercent(metrics.getPassRatePercent())
                    .build();
        }

        private static BigDecimal passRate(int passed, int judged) {
            if (judged == 0) {
                return null;
            }
            return BigDecimal.valueOf(passed)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(judged), 1, RoundingMode.HALF_UP);
        }
    }
}
